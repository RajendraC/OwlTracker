package com.owl.tracker.data

import com.owl.tracker.core.Direction
import com.owl.tracker.core.ImportFilters
import com.owl.tracker.core.TxKind
import org.json.JSONArray
import org.json.JSONObject

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
) {
    val isMoneyIn: Boolean get() = direction == Direction.CREDIT

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
)

/**
 * A merchant the user renamed or chose an icon for, by merchant key. [name] null keeps the name from the
 * SMS. [logo]: null = automatic (the recognised app's icon, if any), "" = no app icon, else an app package.
 */
data class MerchantOverride(val key: String, val name: String?, val logo: String?)

data class StoredRule(val id: Long, val matchType: String, val pattern: String, val categoryId: String)

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
    /** The see-through "glass" look (on by default); off is the plain solid look. */
    val glass: Boolean = true,
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
        "glass" to glass.toString(),
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
            glass = m["glass"]?.toBoolean() ?: true,
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
