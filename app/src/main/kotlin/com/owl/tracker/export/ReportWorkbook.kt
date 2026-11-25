package com.owl.tracker.export

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The report as an Excel workbook (.xlsx), written by hand (no library): Summary, Payments (every payment, with a
 * filter on each column and the header frozen), By category and By month (spending, a column per month). Amounts are
 * numbers in rupees with an Indian ₹ format, dates are real dates, and text is never a formula.
 */
object ReportWorkbook {
    private const val HEADER = 1
    private const val MONEY = 2
    private const val DATE = 3
    private const val BOLD = 4
    private const val BOLD_MONEY = 5
    private const val TITLE = 6

    private class Sheet(val name: String, val widths: List<Double>) {
        val rows = ArrayList<List<Cell?>>()
        var filter: String? = null
        var freeze = false
    }

    private sealed interface Cell { val style: Int }
    private data class Text(val v: String, override val style: Int = 0) : Cell
    private data class Num(val v: Double, override val style: Int = 0) : Cell

    private fun t(v: String, s: Int = 0) = Text(v, s)
    private fun money(paise: Long, bold: Boolean = false) = Num(paise / 100.0, if (bold) BOLD_MONEY else MONEY)
    private fun date(d: LocalDate) = Num((d.toEpochDay() + 25569).toDouble(), DATE)   // Excel counts days from 1900

    fun build(d: ReportExport.Data): ByteArray {
        val months = generateSequence(YearMonth.from(d.from)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(d.to)) }.toList()
        val monthName = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val spend = d.rows.filter { it.spend }
        val spent = spend.sumOf { it.paise }
        val income = d.rows.filter { it.income }.sumOf { it.paise }

        val summary = Sheet("Summary", listOf(30.0, 18.0, 12.0)).apply {
            rows += listOf(t("Owl Tracker report", TITLE))
            rows += listOf(t("${d.from.format(ReportExport.DAY)} to ${d.to.format(ReportExport.DAY)}"))
            rows += listOf()
            rows += listOf(t("Spent", BOLD), money(spent, true))
            rows += listOf(t("Money in", BOLD), money(income, true))
            if (d.monthlyBudget > 0) {
                rows += listOf(t("Budget (${months.size} ${if (months.size == 1) "month" else "months"})", BOLD), money(d.monthlyBudget * months.size, true))
                rows += listOf(t(if (d.monthlyBudget * months.size >= spent) "Left" else "Over", BOLD), money(kotlin.math.abs(d.monthlyBudget * months.size - spent), true))
            }
            rows += listOf(t("Payments", BOLD), Num(d.rows.size.toDouble()))
            rows += listOf()
            rows += listOf(t("Biggest categories", HEADER), t("Spent", HEADER), t("Share", HEADER))
            spend.groupBy { it.category?.name ?: "Other" }.mapValues { e -> e.value.sumOf { it.paise } }.entries.sortedByDescending { it.value }.take(12).forEach { (n, v) ->
                rows += listOf(t(n), money(v), t("${if (spent > 0) Math.round(v * 100.0 / spent) else 0}%"))
            }
            rows += listOf()
            rows += listOf(t("Made on the phone by Owl Tracker." + if (d.accountsHidden) " Card and account numbers are left out." else ""))
        }

        val payments = Sheet("Payments", listOf(13.0, 28.0, 24.0, 18.0, 22.0, 13.0, 13.0, 11.0, 30.0)).apply {
            rows += listOf("Date", "Payee", "Category", "Group", "Paid from", "Money out", "Money in", "Counts in spending", "Note").map { t(it, HEADER) }
            d.rows.forEach { r ->
                rows += listOf(
                    date(r.date), t(r.payee), t((r.category?.name ?: "") + if (r.part) " (part)" else ""), t(r.group?.name ?: ""), t(r.account ?: ""),
                    if (r.out) money(r.paise) else null, if (!r.out) money(r.paise) else null, t(if (r.spend) "Yes" else "No"), t(r.note ?: ""),
                )
            }
            filter = "A1:I${rows.size}"
            freeze = true
        }

        fun byMonth(name: String, first: String, key: (ReportExport.Row) -> String, withGroup: Boolean) = Sheet(name, listOf(28.0) + (if (withGroup) listOf(18.0) else emptyList()) + months.map { 13.0 } + 14.0).apply {
            rows += (listOf(t(first, HEADER)) + (if (withGroup) listOf(t("Group", HEADER)) else emptyList()) + months.map { t(it.format(monthName), HEADER) } + t("Total", HEADER))
            spend.groupBy(key).entries.sortedByDescending { e -> e.value.sumOf { it.paise } }.forEach { (k, list) ->
                val byM = list.groupBy { YearMonth.from(it.date) }
                rows += listOf(t(k)) + (if (withGroup) listOf(t(list.first().group?.name ?: "")) else emptyList()) +
                    months.map { m -> money(byM[m].orEmpty().sumOf { it.paise }) } + money(list.sumOf { it.paise }, true)
            }
            val byM = spend.groupBy { YearMonth.from(it.date) }
            rows += listOf(t("Total", BOLD)) + (if (withGroup) listOf(t("")) else emptyList()) + months.map { m -> money(byM[m].orEmpty().sumOf { it.paise }, true) } + money(spent, true)
            freeze = true
        }
        val byCategory = byMonth("By category", "Category", { it.category?.name ?: "Other" }, withGroup = true)
        val byGroup = byMonth("By month", "Group", { (it.group ?: it.category)?.name ?: "Other" }, withGroup = false)

        return zip(listOf(summary, payments, byCategory, byGroup))
    }

    private fun zip(sheets: List<Sheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(path: String, xml: String) { z.putNextEntry(ZipEntry(path)); z.write(xml.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } + "</Types>")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                sheets.mapIndexed { i, s -> """<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") + "</sheets>" +
                sheets.withIndex().filter { it.value.filter != null }.joinToString("", "<definedNames>", "</definedNames>") { (i, s) ->
                    val (a, b) = s.filter!!.split(":")
                    """<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(s.name)}'!${abs(a)}:${abs(b)}</definedName>"""
                } + "</workbook>")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                """<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return out.toByteArray()
    }

    private fun abs(ref: String): String { val col = ref.takeWhile { it.isLetter() }; return "$" + col + "$" + ref.drop(col.length) }

    private fun sheetXml(s: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (s.freeze) append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        s.widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        s.rows.forEachIndexed { r, cells ->
            append("""<row r="${r + 1}">""")
            cells.forEachIndexed { c, cell ->
                if (cell == null) return@forEachIndexed
                val ref = col(c) + (r + 1)
                when (cell) {
                    is Num -> append("""<c r="$ref" s="${cell.style}"><v>${if (cell.v % 1.0 == 0.0) cell.v.toLong() else String.format(Locale.ROOT, "%.2f", cell.v)}</v></c>""")
                    is Text -> if (cell.v.isNotEmpty()) append("""<c r="$ref" s="${cell.style}" t="inlineStr"><is><t xml:space="preserve">${esc(cell.v)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        s.filter?.let { append("""<autoFilter ref="$it"/>""") }
        append("</worksheet>")
    }

    private fun col(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }; return sb.toString() }

    /** XML text: escaped, and without the control characters XML can't hold. */
    internal fun esc(v: String): String = buildString {
        for (ch in v) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> {}
            ch == '￾' || ch == '￿' -> {}
            else -> append(ch)
        }
    }

    // 0 plain · 1 header (bold, white on green) · 2 ₹ amount · 3 date · 4 bold · 5 bold ₹ amount · 6 title
    private const val INR = """[&gt;=10000000]&quot;₹&quot;##\,##\,##\,##0.00;[&gt;=100000]&quot;₹&quot;##\,##\,##0.00;&quot;₹&quot;#,##0.00"""
    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="$INR"/><numFmt numFmtId="165" formatCode="dd\-mmm\-yyyy"/></numFmts>
<fonts count="4"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="16"/><color rgb="FF4E2716"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF4E2716"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="7">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="164" fontId="2" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
