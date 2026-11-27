package com.owl.tracker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.core.ImportFilters
import com.owl.tracker.core.SmsMessage
import com.owl.tracker.export.ReportExport
import com.owl.tracker.export.ReportPeriod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** The shared report: the page and the Excel workbook, from the sample SMS. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ReportExportTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun container(): AppContainer = runBlocking {
        // A payee whose name looks like markup and a formula: it must stay plain text everywhere
        val odd = SmsMessage(
            8001, "AD-HDFCBK-S",
            "Rs 123.45 debited from a/c **4321 on 24-11-26 to VPA x@ybl (=HYPERLINK(\"x\")</script><b>Shop). Ref 999888777666 -HDFC Bank",
            TEST_CLOCK.millis() - DAY_MS,
        )
        val c = testContainer(context, inbox = sampleInbox(listOf(odd)))
        val e = c.importEngine
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), leftOut = 0, replaceRange = false)
        c
    }

    @Test
    fun thePageCarriesEveryPaymentAndHidesAccountNumbers() = runBlocking {
        val c = container()
        val d = c.reports.collect(ReportPeriod.EVERYTHING, showAccounts = false)
        assertTrue(d.rows.size > 10)
        assertTrue(d.rows.none { it.account?.contains("••") == true })
        val page = c.reports.page(d).decodeToString()
        assertFalse(page.contains("/*OWL_DATA*/"))
        // Only the page's own two closing script tags: data can't end the script early
        assertEquals(2, Regex("</script", RegexOption.IGNORE_CASE).findAll(page).count())
        val json = org.json.JSONObject(page.substringAfter("type=\"application/json\">").substringBefore("</script>"))
        assertEquals(d.rows.size, json.getJSONArray("tx").length())
        assertTrue(json.getBoolean("accountsHidden"))

        val shown = c.reports.collect(ReportPeriod.EVERYTHING, showAccounts = true)
        assertTrue(shown.rows.any { it.account?.contains("••") == true })
    }

    @Test
    fun theWorkbookIsAValidXlsxWithEveryPayment() = runBlocking {
        val c = container()
        val d = c.reports.collect(ReportPeriod.EVERYTHING, showAccounts = false)
        val bytes = c.reports.excel(d)
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.forEach { parts[it.name] = z.readBytes() } }
        for (p in listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet4.xml")) {
            assertTrue("missing $p", parts.containsKey(p))
        }
        // Every part is well-formed XML
        val db = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (n, b) -> runCatching { db.parse(ByteArrayInputStream(b)) }.onFailure { throw AssertionError("$n: ${it.message}") } }
        // Payments: a header and a row per payment
        val sheet = db.parse(ByteArrayInputStream(parts.getValue("xl/worksheets/sheet2.xml")))
        assertEquals(d.rows.size + 1, sheet.getElementsByTagName("row").length)
        assertEquals(0, sheet.getElementsByTagName("f").length)   // no formulas, whatever a payee is called
    }

    @Test
    fun accountNumbersComeOffTheLabel() {
        assertEquals("HDFC Bank card", ReportExport.hideNumber("HDFC Bank card ••3643"))
        assertEquals("ICICI Bank", ReportExport.hideNumber("ICICI Bank ••2210"))
        assertEquals("Cash", ReportExport.hideNumber("Cash"))
    }

    @Test
    fun lastMonthRunsFromItsFirstToItsLastDay() = runBlocking {
        val c = container()
        val d = c.reports.collect(ReportPeriod.LAST_MONTH, showAccounts = false)
        assertEquals(java.time.LocalDate.of(2026, 9, 1), d.from)
        assertEquals(java.time.LocalDate.of(2026, 9, 30), d.to)
        assertTrue(d.rows.all { !it.date.isBefore(d.from) && !it.date.isAfter(d.to) })
    }
}
