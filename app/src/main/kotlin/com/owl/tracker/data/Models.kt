package com.owl.tracker.data

import com.owl.tracker.core.Direction
import com.owl.tracker.core.ImportFilters
import com.owl.tracker.core.TxKind
import org.json.JSONArray
import org.json.JSONObject

/** Light or dark: follow the phone (SYSTEM), or always one of them. [key] is how it's stored. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"), LIGHT("light", "Light"), DARK("dark", "Dark");

    /** Whether to draw dark, given whether the phone itself is in dark mode. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Where a transaction came from. BACKUP: restored from a backup file (originally an SMS, with the SMS details left out). */
enum class Source { SMS, MANUAL, BACKUP }

/** EXPENSE and SAVING (investments: money going out, flagged as invested) count toward budgets. TRANSFER and INCOME never do. */
enum class CategoryKind { EXPENSE, SAVING, TRANSFER, INCOME }

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val kind: CategoryKind,
    val sortOrder: Int,
    val isSystem: Boolean,
    val archived: Boolean,
    /** The group this category sits in; null for a group, or a category on its own. */
    val parent: String? = null,
) {
    val countsAsSpend: Boolean get() = kind == CategoryKind.EXPENSE || kind == CategoryKind.SAVING
    /** Money put into investments: it counts as money going out, and is shown in purple as invested. */
    val isInvestment: Boolean get() = kind == CategoryKind.SAVING
}

/**
 * Categories as groups: Shopping holds Fashion & Clothing, Phones & Electronics…; EMI & Loans sits on its own. Budgets,
 * Home's ring and the months behind it are by group ("tops"); a payment always has one category, which may be a group
 * itself when it isn't sorted yet (an Amazon order).
 */
class CategoryTree(val all: List<Category>) {
    val byId: Map<String, Category> = all.associateBy { it.id }

    /** The group [id] sits in, or [id] itself. */
    fun top(id: String): String = byId[id]?.parent?.takeIf { byId.containsKey(it) } ?: id

    /** The categories in group [id], archived ones left out, in their order. */
    fun children(id: String): List<Category> = all.filter { it.parent == id && !it.archived }

    fun isGroup(id: String): Boolean = all.any { it.parent == id && !it.archived }

    /** Groups and categories on their own that count as spending: what budgets, Home's ring and trends are by. */
    val spendTops: List<Category> get() = all.filter { it.parent == null && it.countsAsSpend && !it.archived }

    /**
     * What a payment can be put in, in order: each group's categories under its name, then the ones on their own.
     * A group itself is listed only when [withGroups] (as "not sorted yet").
     */
    fun pickable(withGroups: Boolean = false): List<Category> = all.filter { c ->
        !c.archived && (c.parent == null || byId[c.parent]?.archived != true) && (withGroups || !isGroup(c.id))
    }
}

data class Txn(
    val id: Long,
    val amountPaise: Long,
    val direction: Direction,
    val occurredAt: Long,
    val merchantName: String,
    val merchantKey: String,
    val merchantPackage: String?,
    val vpa: String?,
    val categoryId: String,
    val accountLabel: String?,
    val senderGroup: String?,
    val sender: String?,
    val refNo: String?,
    val source: Source,
    val smsId: Long?,
    val needsReview: Boolean,
    val excluded: Boolean,
    val note: String?,
    val kind: TxKind,
    val smsBody: String?,
    /** Split across categories: two or more parts adding up to [amountPaise]. Empty when it isn't split. */
    val parts: List<TxnPart> = emptyList(),
    /** One part of a split payment, made by [portions]: same id, the part's category and amount. */
    val isPortion: Boolean = false,
) {
    val isMoneyIn: Boolean get() = direction == Direction.CREDIT

    val isSplit: Boolean get() = parts.size >= 2

    /**
     * What every total counts: the payment itself, or for a split payment each part as if it were a payment of its
     * own (same id, the part's category and amount). Lists show the payment once; totals add up its portions.
     */
    fun portions(): List<Txn> =
        if (isPortion || parts.size < 2) listOf(this) else parts.map { copy(categoryId = it.categoryId, amountPaise = it.paise, isPortion = true) }

    /**
     * Counts toward spending and budgets: money out, in an expense category, not left out by the user.
     * Money in never does, not even a refund: it shows with a + and counts as income on Income & expenses.
     */
    fun isSpending(category: Category?): Boolean = direction == Direction.DEBIT && !excluded && (category?.countsAsSpend ?: true)

    /** Money going out into investments: counted like spending, flagged as invested. */
    fun isInvested(category: Category?): Boolean = isSpending(category) && category?.isInvestment == true

    /** Counts as income on Income & expenses: salary and other money in, refunds and money back. Transfers and savings coming back don't. */
    fun isIncome(category: Category?): Boolean =
        direction == Direction.CREDIT && !excluded && (category == null || category.kind == CategoryKind.INCOME || category.kind == CategoryKind.EXPENSE)
}

/** A transaction about to be written; the database assigns the id. */
data class NewTxn(
    val amountPaise: Long,
    val direction: Direction,
    val occurredAt: Long,
    val merchantName: String,
    val merchantKey: String,
    val merchantPackage: String?,
    val vpa: String?,
    val categoryId: String,
    val accountLabel: String?,
    val senderGroup: String?,
    val sender: String?,
    val refNo: String?,
    val source: Source,
    val smsId: Long?,
    val needsReview: Boolean,
    val note: String? = null,
    val kind: TxKind = TxKind.NORMAL,
    val smsBody: String? = null,
    val excluded: Boolean = false,
    val parts: List<TxnPart> = emptyList(),
)

/** One part of a payment split across categories. */
data class TxnPart(val categoryId: String, val paise: Long)

/** How a split is kept in the transactions table: "shopping=1299900|groceries=153709". */
object Parts {
    fun encode(parts: List<TxnPart>): String? = if (parts.size < 2) null else parts.joinToString("|") { "${it.categoryId}=${it.paise}" }

    fun decode(s: String?): List<TxnPart> {
        if (s.isNullOrBlank()) return emptyList()
        val parts = s.split('|').mapNotNull { p ->
            val i = p.lastIndexOf('=')
            if (i <= 0) null else p.substring(i + 1).toLongOrNull()?.takeIf { it > 0 }?.let { TxnPart(p.substring(0, i), it) }
        }
        return if (parts.size >= 2) parts else emptyList()
    }

    /** A split is kept only if it has two or more parts in different categories, each above zero, adding up exactly. */
    fun valid(parts: List<TxnPart>, total: Long): Boolean =
        parts.size >= 2 && parts.all { it.paise > 0 } && parts.map { it.categoryId }.toSet().size == parts.size && parts.sumOf { it.paise } == total
}

/**
 * A merchant the user renamed or chose an icon for, by merchant key. [name] null keeps the name from the
 * SMS. [logo]: null = automatic (the recognised app's icon, if any), "" = no app icon, else an app package.
 */
data class MerchantOverride(val key: String, val name: String?, val logo: String?)

data class StoredRule(val id: Long, val matchType: String, val pattern: String, val categoryId: String)

/** A rule as the Rules screen shows it: who it's for, where it puts them, when it was made and how many payments it covers. */
data class RuleInfo(
    val id: Long,
    val match: com.owl.tracker.core.RuleMatch,
    val pattern: String,
    val categoryId: String,
    val createdAt: Long,
    val name: String,
    val payments: Int,
)

/** Everything that is not a transaction. Lives inside the encrypted database, not in plain preferences. */
/** A card or account payments came from ([label] as shown), the bank whose messages named it, and how many payments. */
data class KnownAccount(val label: String, val senderGroup: String?, val payments: Int)

data class AppSettings(
    val onboarded: Boolean = false,
    val smsEnabled: Boolean = false,
    val filters: ImportFilters = ImportFilters(),
    val budgetStartDay: Int = 1,
    val appLock: Boolean = false,
    val screenPrivacy: Boolean = true,
    val lastSyncAt: Long = 0,
    val lastSmsTimestamp: Long = 0,
    /** True after "Explore with sample data": the transactions are built-in examples, not the user's. */
    val sampleData: Boolean = false,
    /** Saving tips are hidden from Home until this time (the end of the budget month they were hidden in). */
    val tipsHiddenUntil: Long = 0,
    /** Light, dark, or follow the phone (the default). */
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /**
     * The SMS parser version the inbox was last read with in full. When the app's parser is newer, the next sync
     * looks back over the import period once for messages of the kinds it has newly learned. Before 1.5.1: 1.
     */
    val parserVersion: Int = 1,
    /**
     * The built-in categories the payments were last sorted into. When the app adds one (2: Holiday, taken out of
     * Travel), payments the usual rules now put there are moved once (see ImportEngine.sortIntoNewCategories).
     */
    val categoriesVersion: Int = 1,
    /** How payments are grouped into payees: 1 since a payment with only a UPI ID is its UPI ID's, not its bank's (1.9.5). */
    val keysVersion: Int = 0,
    /** Pays for children: the Kids categories are shown and payments are sorted into them. Asked once at setup. */
    val kids: Boolean = true,
    /**
     * Cards and accounts whose type the user set by hand on Paid by: "HDFC Bank card ••9110" to "credit", "debit" or
     * "account". Holds card and account digits, so it's never put in a backup (backups carry only two settings).
     */
    val paidKinds: Map<String, String> = emptyMap(),
    /** Credit cards' billing cycles, as JSON (`CardBills.cyclesFromJson`); card digits, so never in backups. */
    val cardCycles: String = "{}",
    /** Payments the user recorded against card bills, as JSON (`CardBills.paymentsFromJson`); never in backups. */
    val cardPayments: String = "{}",
    /** Card credits the user marked as a bill payment or a refund (JSON payment id → "payment"/"refund"). Not in backups. */
    val cardCreditKinds: String = "{}",
    /** Savings goals, as JSON (`Goals.fromJson`): name, picture, target, set aside, by when. Kept in backups. */
    val goals: String = "[]",
    /** The colours picked in Settings (`Palette.key`). */
    val palette: String = "cream",
    /** Repeating payments the user stopped tracking (their merchant keys, JSON array). */
    val ignoredBills: String = "[]",
    /**
     * The card or account the user picked for a bank's messages that don't say which ("icici" to "ICICI Bank ••1234"),
     * used for new ones. Holds account digits, so never in backups.
     */
    val accountFor: Map<String, String> = emptyMap(),
) {
    fun toMap(): Map<String, String> = mapOf(
        "onboarded" to onboarded.toString(),
        "sms_enabled" to smsEnabled.toString(),
        "filters" to filtersToJson(filters),
        "budget_start_day" to budgetStartDay.toString(),
        "app_lock" to appLock.toString(),
        "screen_privacy" to screenPrivacy.toString(),
        "last_sync_at" to lastSyncAt.toString(),
        "last_sms_ts" to lastSmsTimestamp.toString(),
        "sample_data" to sampleData.toString(),
        "tips_hidden_until" to tipsHiddenUntil.toString(),
        "theme" to theme.key,
        "parser_version" to parserVersion.toString(),
        "categories_version" to categoriesVersion.toString(),
        "keys_version" to keysVersion.toString(),
        "kids" to if (kids) "on" else "off",
        "paid_kinds" to org.json.JSONObject(paidKinds as Map<*, *>).toString(),
        "card_cycles" to cardCycles,
        "card_payments" to cardPayments,
        "card_credit_kinds" to cardCreditKinds,
        "goals" to goals,
        "palette" to palette,
        "ignored_bills" to ignoredBills,
        "account_for" to org.json.JSONObject(accountFor as Map<*, *>).toString(),
    )

    companion object {
        fun fromMap(m: Map<String, String>) = AppSettings(
            onboarded = m["onboarded"]?.toBoolean() ?: false,
            smsEnabled = m["sms_enabled"]?.toBoolean() ?: false,
            filters = m["filters"]?.let { runCatching { filtersFromJson(it) }.getOrNull() } ?: ImportFilters(),
            budgetStartDay = m["budget_start_day"]?.toIntOrNull() ?: 1,
            appLock = m["app_lock"]?.toBoolean() ?: false,
            screenPrivacy = m["screen_privacy"]?.toBoolean() ?: true,
            lastSyncAt = m["last_sync_at"]?.toLongOrNull() ?: 0,
            lastSmsTimestamp = m["last_sms_ts"]?.toLongOrNull() ?: 0,
            sampleData = m["sample_data"]?.toBoolean() ?: false,
            tipsHiddenUntil = m["tips_hidden_until"]?.toLongOrNull() ?: 0,
            theme = ThemeMode.from(m["theme"]),
            parserVersion = m["parser_version"]?.toIntOrNull() ?: 1,
            paidKinds = m["paid_kinds"]?.let { raw ->
                runCatching { org.json.JSONObject(raw).let { o -> o.keys().asSequence().associateWith { k -> o.getString(k) } } }.getOrNull()
            } ?: emptyMap(),
            cardCycles = m["card_cycles"] ?: "{}",
            cardPayments = m["card_payments"] ?: "{}",
            cardCreditKinds = m["card_credit_kinds"] ?: "{}",
            goals = m["goals"] ?: "[]",
            palette = m["palette"] ?: "cream",
            ignoredBills = m["ignored_bills"] ?: "[]",
            accountFor = m["account_for"]?.let { raw ->
                runCatching { org.json.JSONObject(raw).let { o -> o.keys().asSequence().associateWith { k -> o.getString(k) } } }.getOrNull()
            } ?: emptyMap(),
            categoriesVersion = m["categories_version"]?.toIntOrNull() ?: 1,
            keysVersion = m["keys_version"]?.toIntOrNull() ?: 0,
            kids = m["kids"] != "off",
        )

        fun filtersToJson(f: ImportFilters): String = JSONObject()
            .put("range", f.rangeMonths)
            .put("disabled", JSONArray(f.disabledSenders.toList()))
            .put("out", f.includeOut)
            .put("in", f.includeIn)
            .put("min", f.minAmountPaise)
            .put("words", JSONArray(f.skipWords))
            .toString()

        fun filtersFromJson(s: String): ImportFilters {
            val o = JSONObject(s)
            val disabled = o.optJSONArray("disabled")
            val words = o.optJSONArray("words")
            return ImportFilters(
                rangeMonths = o.optInt("range", 6),
                disabledSenders = (0 until (disabled?.length() ?: 0)).map { disabled!!.getString(it) }.toSet(),
                includeOut = o.optBoolean("out", true),
                includeIn = o.optBoolean("in", true),
                minAmountPaise = o.optLong("min", 0),
                skipWords = (0 until (words?.length() ?: 0)).map { words!!.getString(it) },
            )
        }
    }
}
