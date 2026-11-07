package com.owl.tracker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Source
import com.owl.tracker.core.Cat
import com.owl.tracker.core.TxKind
import com.owl.tracker.data.Repository
import com.owl.tracker.sms.FakeSmsSource
import com.owl.tracker.sms.ImportEngine
import com.owl.tracker.core.ImportFilters
import com.owl.tracker.core.SmsMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The import pipeline end to end, against a real (in-memory) SQLite database. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ImportEngineTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun month(c: AppContainer) = runBlocking {
        val now = TEST_CLOCK.millis()
        c.repo.transactions(now - 40L * 24 * 3600_000, now + 1)
    }

    @Test
    fun scanFindsOnlyTransactionsAndGroupsSenders() = runBlocking {
        val c = testContainer(context)
        val scan = c.importEngine.scan()
        assertEquals(13, scan.candidates.size)
        assertEquals(setOf("hdfc", "icici", "sbi", "axis", "kotak"), scan.groups.map { it.id }.toSet())
        assertEquals(13, c.importEngine.selectable(scan, ImportFilters()).size)
        // Personal, OTP, promotional, reminder, request, failed and government messages are not candidates.
        assertTrue(scan.messagesRead.size > scan.candidates.size)
    }

    @Test
    fun filtersNarrowWhatIsImported() = runBlocking {
        val c = testContainer(context)
        val scan = c.importEngine.scan()
        val e = c.importEngine
        assertEquals(5, e.selectable(scan, ImportFilters(disabledSenders = setOf("hdfc", "unknown"))).size)
        assertEquals(12, e.selectable(scan, ImportFilters(minAmountPaise = 20_000)).size) // drops the ₹148 Rapido ride
        assertEquals(12, e.selectable(scan, ImportFilters(skipWords = listOf("NEFT"))).size) // drops the NEFT salary credit
        assertEquals(10, e.selectable(scan, ImportFilters(includeIn = false)).size) // refund, salary and card-bill credits left out
    }

    @Test
    fun commitSavesCategorisesAndNeverDuplicates() = runBlocking {
        val c = testContainer(context)
        val e = c.importEngine
        val scan = e.scan()
        val chosen = e.selectable(scan, ImportFilters())
        val r = e.commit(chosen, ImportFilters(), leftOut = 0, replaceRange = false)
        assertEquals(13, r.saved)
        val txns = month(c)
        assertEquals(13, txns.size)
        assertEquals(Cat.FOOD, txns.first { it.merchantName == "Swiggy" }.categoryId)
        assertEquals("in.swiggy.android", txns.first { it.merchantName == "Swiggy" }.merchantPackage)
        assertEquals(Cat.CASH, txns.first { it.amountPaise == 500000L }.categoryId)
        assertEquals(Cat.TRANSFERS, txns.first { it.amountPaise == 1840000L }.categoryId)
        // Running the same import again adds nothing.
        val again = e.commit(chosen, ImportFilters(), leftOut = 0, replaceRange = false)
        assertEquals(0, again.saved)
        assertEquals(13, month(c).size)
    }

    @Test
    fun bankAndUpiAppAlertsForOnePaymentAreMerged() = runBlocking {
        val now = TEST_CLOCK.millis()
        val dup = SmsMessage(9001, "JX-PAYTMB-S", "Paid Rs.486.00 to swiggy@icici from Paytm Payments Bank. UPI Ref 629112345678.", now - 119 * 60_000)
        val c = testContainer(context, sampleInbox(listOf(dup)))
        val e = c.importEngine
        val scan = e.scan()
        assertEquals(14, scan.candidates.size)
        val r = e.commit(e.selectable(scan, ImportFilters(disabledSenders = emptySet())), ImportFilters(), 0, false)
        assertEquals(1, r.duplicates)
        assertEquals(1, month(c).count { it.amountPaise == 48600L })
    }

    @Test
    fun deletedTransactionNeverComesBackButUndoRestoresIt() = runBlocking {
        val c = testContainer(context)
        val e = c.importEngine
        val chosen = e.selectable(e.scan(), ImportFilters())
        e.commit(chosen, ImportFilters(), 0, false)
        val swiggy = month(c).first { it.merchantName == "Swiggy" }
        val deleted = c.repo.deleteTransaction(swiggy.id)!!
        assertEquals(12, month(c).size)
        e.commit(chosen, ImportFilters(), 0, false)
        assertEquals("Re-import must not resurrect a deleted transaction", 12, month(c).size)
        c.repo.restoreTransaction(deleted)
        assertEquals(13, month(c).size)
    }

    @Test
    fun reimportRemovesWhatIsNoLongerChosenAndKeepsEdits() = runBlocking {
        val c = testContainer(context)
        val e = c.importEngine
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), 0, false)
        val zepto = month(c).first { it.merchantName == "Zepto" }
        c.repo.setCategory(zepto.id, Cat.FOOD, rememberForMerchant = false)
        val narrower = ImportFilters(disabledSenders = setOf("hdfc", "unknown"))
        val r = e.commit(e.selectable(e.scan(), narrower), narrower, 0, replaceRange = true)
        assertEquals(8, r.removed)
        val left = month(c)
        assertEquals(5, left.size)
        assertEquals("Edits to transactions that stay are kept", Cat.FOOD, left.first { it.merchantName == "Zepto" }.categoryId)
    }

    @Test
    fun rememberedRuleRecategorisesPastAndFuture() = runBlocking {
        val c = testContainer(context)
        val e = c.importEngine
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), 0, false)
        val swiggy = month(c).first { it.merchantName == "Swiggy" }
        c.repo.setCategory(swiggy.id, Cat.GROCERIES, rememberForMerchant = true)
        assertEquals(Cat.GROCERIES, month(c).first { it.merchantName == "Swiggy" }.categoryId)
        assertEquals(1, c.repo.rules().size)
    }

    @Test
    fun incrementalSyncPicksUpNewMessagesWithSavedFilters() = runBlocking {
        val inbox = sampleInbox()
        val c = testContainer(context, inbox)
        val e = c.importEngine
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), 0, false)
        c.repo.updateSettings { it.copy(onboarded = true) }
        inbox += SmsMessage(5000, "VM-HDFCBK-S", "Sent Rs.220.00 From HDFC Bank A/C *4821 To BLINKIT On 18/10/26 Ref 629999999999", TEST_CLOCK.millis() - 60_000)
        inbox += SmsMessage(5001, "+919999999999", "Rs 500 sent to you", TEST_CLOCK.millis() - 30_000)
        assertEquals(1, e.syncNew())
        assertEquals(0, e.syncNew())
        assertTrue(month(c).any { it.merchantName == "Blinkit" })
    }

    @Test
    fun chosenNamesAndIconsApplyToPastAndNewPayments() = runBlocking {
        val inbox = sampleInbox()
        val c = testContainer(context, inbox)
        val e = c.importEngine
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), 0, false)
        c.repo.updateSettings { it.copy(onboarded = true) }
        val swiggy = month(c).first { it.merchantKey == "swiggy" }
        val count = month(c).count { it.merchantKey == "swiggy" }

        // Just this one payment: no remembered name.
        assertEquals(1, c.repo.renameMerchant(swiggy.id, "Friday dinner", all = false))
        assertEquals("Friday dinner", c.repo.transaction(swiggy.id)!!.merchantName)
        assertTrue(c.repo.merchantOverrides().isEmpty())

        // Every payment from the merchant, and remembered for new ones.
        assertEquals(count, c.repo.renameMerchant(swiggy.id, "  Food delivery  ", all = true))
        assertTrue(month(c).filter { it.merchantKey == "swiggy" }.all { it.merchantName == "Food delivery" })
        assertEquals(count, c.repo.setMerchantLogo(swiggy.id, "com.application.zomato"))
        assertEquals(0, c.repo.setMerchantLogo(swiggy.id, "com.example.not.recognised"))

        inbox += SmsMessage(5100, "VM-HDFCBK-S", "Sent Rs.410.00 From HDFC Bank A/C *4821 To SWIGGY On 18/10/26 Ref 629999990001", TEST_CLOCK.millis() - 60_000)
        assertEquals(1, e.syncNew())
        val fresh = month(c).first { it.smsId == 5100L }
        assertEquals("Food delivery", fresh.merchantName)
        assertEquals("com.application.zomato", fresh.merchantPackage)
        assertEquals(Cat.FOOD, fresh.categoryId) // renaming never changes how it's categorised

        // No app icon, then back to automatic (Swiggy's own app).
        c.repo.setMerchantLogo(swiggy.id, "")
        assertTrue(month(c).filter { it.merchantKey == "swiggy" }.all { it.merchantPackage == null })
        c.repo.setMerchantLogo(swiggy.id, null)
        assertTrue(month(c).filter { it.merchantKey == "swiggy" }.all { it.merchantPackage == "in.swiggy.android" })
        assertEquals(null, c.repo.merchantOverrides()["swiggy"]?.logo)
        assertEquals("Food delivery", c.repo.merchantOverrides()["swiggy"]?.name)
    }

    @Test
    fun cardBillsShowTheBanksAppAndOlderPaymentsGetNewIcons() = runBlocking {
        val inbox = sampleInbox()
        val c = testContainer(context, inbox)
        var hdfcApp: String? = null
        val bankApp: (String?) -> String? = { if (it == "hdfc") hdfcApp else null }
        val repo = Repository(c.db, TEST_CLOCK, bankApp)
        val e = ImportEngine(FakeSmsSource({ inbox.toList() }, true), repo, TEST_CLOCK, bankApp)
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), 0, false)
        repo.updateSettings { it.copy(onboarded = true) }
        fun all() = runBlocking { repo.transactions(TEST_CLOCK.millis() - 40L * DAY_MS, TEST_CLOCK.millis() + 1) }

        // No HDFC app on the phone yet: the card bill has no icon
        val bill = all().first { it.kind == TxKind.CARD_BILL_PAYMENT }
        assertEquals("hdfc", bill.senderGroup)
        assertNull(bill.merchantPackage)

        // Payments saved by an older version, before it knew these apps
        c.db.get().execSQL("UPDATE transactions SET merchant_package = NULL WHERE merchant_key IN ('swiggy', 'zomato')")
        val zomato = all().first { it.merchantKey == "zomato" }
        repo.setMerchantLogo(zomato.id, "") // the user chose no icon for Zomato

        // The HDFC app is installed; the next sync fills in what's missing and nothing else
        hdfcApp = "com.hdfcbank.android.now"
        e.syncNew()
        assertEquals("com.hdfcbank.android.now", repo.transaction(bill.id)!!.merchantPackage)
        assertTrue(all().filter { it.merchantKey == "swiggy" }.all { it.merchantPackage == "in.swiggy.android" })
        assertTrue(all().filter { it.merchantKey == "zomato" }.all { it.merchantPackage == null })
        assertEquals(0, repo.fillMissingIcons())

        // No icon, then automatic again: a card bill goes back to its bank's app
        repo.setMerchantLogo(bill.id, "")
        assertNull(repo.transaction(bill.id)!!.merchantPackage)
        repo.setMerchantLogo(bill.id, null)
        assertEquals("com.hdfcbank.android.now", repo.transaction(bill.id)!!.merchantPackage)

        // New card bills get the icon as they arrive
        inbox += SmsMessage(6000, "VM-HDFCBK-T", "Payment of Rs.2,100.00 received towards your HDFC Bank Credit Card ending 9012 on 18-10-26. Thank you.", TEST_CLOCK.millis() - 60_000)
        assertEquals(1, e.syncNew())
        assertEquals("com.hdfcbank.android.now", all().first { it.smsId == 6000L }.merchantPackage)
    }

    @Test
    fun sportsAndFitnessIsBuiltIn() = runBlocking {
        val c = testContainer(context)
        val ids = c.repo.categories().map { it.id }
        assertEquals(ids.indexOf(Cat.ENTERTAINMENT) + 1, ids.indexOf(Cat.SPORTS))
        assertEquals("Sports & Fitness", c.repo.categories().first { it.id == Cat.SPORTS }.name)
        assertTrue(c.repo.categories().first { it.id == Cat.SPORTS }.countsAsSpend)
    }

    @Test
    fun noSyncWithoutPermissionOrInManualMode() = runBlocking {
        val c = testContainer(context, granted = false)
        c.repo.updateSettings { it.copy(onboarded = true, smsEnabled = true) }
        assertEquals(0, c.importEngine.syncNew())
        assertEquals(0, month(c).size)
    }

    @Test
    fun bulkDeletesKeepManualEntries() = runBlocking {
        val c = testContainer(context)
        val e = c.importEngine
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), 0, false)
        c.repo.addManual(com.owl.tracker.sms.ImportEngine.manual(15000, "Tea stall", Cat.FOOD, TEST_CLOCK.millis()))
        assertEquals(8, c.repo.countSmsInGroup("hdfc"))
        assertEquals(8, c.repo.deleteSmsInGroup("hdfc"))
        assertEquals(5, c.repo.deleteAllSms())
        val left = month(c)
        assertEquals(1, left.size)
        assertEquals(Source.MANUAL, left.single().source)
    }

    @Test
    fun skippedMessagesAreListedWithoutPersonalTextsOrOtpCodes() = runBlocking {
        val c = testContainer(context)
        val scan = c.importEngine.scan()
        assertEquals(scan.messagesRead.size - scan.candidates.size, scan.skipped.size)
        assertTrue(scan.skipped.isNotEmpty())
        scan.skipped.filter { it.reason == com.owl.tracker.core.RejectReason.PERSONAL_SENDER }.forEach {
            assertEquals(null, it.sender); assertEquals(null, it.body)
        }
        scan.skipped.filter { it.reason == com.owl.tracker.core.RejectReason.OTP }.forEach { assertEquals(null, it.body) }
    }

    @Test
    fun maskDigitsHidesAccountAndReferenceNumbersButKeepsAmounts() {
        assertEquals(
            "Sent Rs.486.00 From A/C *##21 Ref ##########78 on 18/10/26",
            com.owl.tracker.ui.AppViewModel.maskDigits("Sent Rs.486.00 From A/C *4821 Ref 629112345678 on 18/10/26"),
        )
        assertEquals("Rs 1,240.00", com.owl.tracker.ui.AppViewModel.maskDigits("Rs 1,240.00"))
    }

    @Test
    fun sampleDataNeverReadsTheInboxOrTurnsOnSync() = runBlocking {
        val c = testContainer(context, inbox = mutableListOf())
        val e = c.importEngine
        val scan = e.scan(sample = true)
        assertEquals(13, scan.candidates.size)
        assertTrue(scan.candidates.all { it.smsId < 0 })
        c.repo.updateSettings { it.copy(onboarded = true) }
        e.commit(e.selectable(scan, ImportFilters()), ImportFilters(), 0, replaceRange = false, sample = true)
        val s = c.repo.settings()
        assertTrue(s.sampleData)
        assertFalse(s.smsEnabled)
        assertEquals(13, month(c).size)
        assertEquals(0, e.syncNew())
    }

    @Test
    fun eraseEverythingStartsFresh() = runBlocking {
        val c = testContainer(context)
        val e = c.importEngine
        e.commit(e.selectable(e.scan(), ImportFilters()), ImportFilters(), 0, false)
        c.repo.setBudget(Schema.OVERALL, 75_000_00)
        c.repo.updateSettings { it.copy(onboarded = true, appLock = true) }
        c.eraseEverything()
        assertEquals(0, month(c).size)
        assertFalse(c.repo.settings().onboarded)
        assertTrue(c.repo.observeBudgets().first().isEmpty())
        assertEquals(Schema.DEFAULT_CATEGORIES.size, c.repo.categories().size)
    }
}
