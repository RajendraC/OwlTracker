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

/** EXPENSE counts toward budgets. SAVING, TRANSFER and INCOME never do. */
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
) {
    val countsAsSpend: Boolean get() = kind == CategoryKind.EXPENSE
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
