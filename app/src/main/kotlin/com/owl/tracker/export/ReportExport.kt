package com.owl.tracker.export

import android.content.Context
import com.owl.tracker.core.Direction
import com.owl.tracker.data.Category
import com.owl.tracker.data.CategoryTree
import com.owl.tracker.data.Repository
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Txn
import com.owl.tracker.ui.components.categoryArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How much a shared report covers, counted in calendar months up to today. */
enum class ReportPeriod(val label: String) {
    THIS_MONTH("This month"), LAST_MONTH("Last month"), LAST_3_MONTHS("Last 3 months"), THIS_YEAR("This year"), EVERYTHING("Everything")
}

/** The file a report is made as: an interactive page that opens in any browser, or an Excel workbook. */
enum class ReportFormat(val ext: String, val mime: String) {
    PAGE("html", "text/html"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

/**
 * A report of payments to share with family or open on a computer: the period's payments (a split one as its parts),
 * with their categories, groups and pictures, and the month's budget. Card and account numbers are left out unless
 * [showAccounts]; the SMS text never goes in. Made on the phone, sends nothing anywhere.
 */
class ReportExport(private val context: Context, private val repo: Repository, private val clock: Clock) {

    /** One payment (or one part of a split payment) as the report shows it. */
    data class Row(
        val date: LocalDate, val payee: String, val category: Category?, val group: Category?, val paise: Long,
        val out: Boolean, val account: String?, val note: String?, val spend: Boolean, val income: Boolean, val part: Boolean,
    )

    data class Data(
        val from: LocalDate, val to: LocalDate, val made: LocalDate, val rows: List<Row>,
        val cats: List<Category>, val tree: CategoryTree, val monthlyBudget: Long, val accountsHidden: Boolean,
    )

    suspend fun collect(period: ReportPeriod, showAccounts: Boolean): Data {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val earliest = repo.earliestTransaction()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
        val from = when (period) {
            ReportPeriod.THIS_MONTH -> today.withDayOfMonth(1)
            ReportPeriod.LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1)
            ReportPeriod.LAST_3_MONTHS -> today.withDayOfMonth(1).minusMonths(2)
            ReportPeriod.THIS_YEAR -> today.withDayOfYear(1)
            ReportPeriod.EVERYTHING -> earliest.withDayOfMonth(1)
        }
        val cats = repo.categories()
        val tree = CategoryTree(cats)
        // Last month ends where this one starts; the rest run up to today
        val end = if (period == ReportPeriod.LAST_MONTH) today.withDayOfMonth(1) else today.plusDays(1)
        val txns = repo.transactions(from.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli())
        val rows = txns.flatMap { t -> t.portions().map { p -> row(p, tree, showAccounts, zone) } }.sortedByDescending { it.date }
        val budget = repo.budgets()[Schema.OVERALL] ?: 0L
        return Data(from, end.minusDays(1), today, rows, cats, tree, budget, !showAccounts)
    }

    private fun row(t: Txn, tree: CategoryTree, showAccounts: Boolean, zone: java.time.ZoneId): Row {
        val c = tree.byId[t.categoryId]
        val g = c?.parent?.let { tree.byId[it] }
        return Row(
            date = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate(),
            payee = t.merchantName, category = c, group = g, paise = t.amountPaise, out = t.direction == Direction.DEBIT,
            account = t.accountLabel?.let { if (showAccounts) it else hideNumber(it) },
            note = t.note?.takeIf { it.isNotBlank() }, spend = t.isSpending(c), income = t.isIncome(c), part = t.isPortion,
        )
    }

    /** The interactive page: the template with the report's data put in. */
    suspend fun page(d: Data): ByteArray = withContext(Dispatchers.IO) {
        val template = context.assets.open("report/report.html").use { it.readBytes().decodeToString() }
        template.replace("/*OWL_DATA*/", json(d)).encodeToByteArray()
    }

    suspend fun excel(d: Data): ByteArray = withContext(Dispatchers.Default) { ReportWorkbook.build(d) }

    /** A copy for the share sheet, where the backups' shared copies go (the only place other apps can read from). */
    suspend fun writeForSharing(bytes: ByteArray, name: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith("owl-tracker-report") }?.forEach { it.delete() }
        File(dir, name).apply { writeBytes(bytes) }
    }

    suspend fun write(uri: android.net.Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        (context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")).use { it.write(bytes) }
    }

    fun fileName(d: Data, format: ReportFormat): String = "owl-tracker-report-${d.from}-to-${d.to}.${format.ext}"

    /** The data the page reads: plain JSON, with "</" written so it can't close the script it sits in. */
    fun json(d: Data): String {
        val used = d.rows.mapNotNull { it.category }.distinctBy { it.id }
        val groups = (used.mapNotNull { c -> c.parent?.let { d.tree.byId[it] } } + used.filter { it.parent == null && d.tree.isGroup(it.id) }).distinctBy { it.id }
        val pics = HashMap<String, String>()
        fun pic(icon: String?): String = pics.getOrPut(icon ?: "") { picture(icon) }
        val o = JSONObject()
            .put("title", "Owl Tracker report")
            .put("made", d.made.toString()).put("from", d.from.toString()).put("to", d.to.toString())
            .put("budget", d.monthlyBudget).put("accountsHidden", d.accountsHidden)
            .put("groups", JSONArray(groups.map { g -> JSONObject().put("id", g.id).put("name", g.name).put("color", hex(g.color)).put("pic", pic(g.icon)) }))
            .put("cats", JSONArray(used.map { c ->
                JSONObject().put("id", c.id).put("name", c.name).put("group", c.parent ?: JSONObject.NULL).put("color", hex(c.color)).put("pic", pic(c.icon))
            }))
            .put("tx", JSONArray(d.rows.map { r ->
                JSONObject().put("d", r.date.toString()).put("p", r.payee).put("c", r.category?.id ?: "").put("a", r.paise).put("o", r.out)
                    .put("acc", r.account ?: JSONObject.NULL).put("n", r.note ?: JSONObject.NULL).put("s", r.spend).put("i", r.income).put("sp", r.part)
            }))
        return o.toString().replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    }

    /** The category's 3D picture as a data URI, or "" when it has none. */
    private fun picture(icon: String?): String = runCatching {
        val id = categoryArt(icon) ?: return ""
        val bytes = context.resources.openRawResource(id).use { it.readBytes() }
        "data:image/webp;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }.getOrDefault("")

    companion object {
        /** "HDFC Bank card ••3643" → "HDFC Bank card": the bank stays, the number doesn't. */
        fun hideNumber(label: String): String = label.replace(Regex("""\s*••\s*\d+"""), "").trim().ifEmpty { "Account" }

        fun hex(argb: Long): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }
}
