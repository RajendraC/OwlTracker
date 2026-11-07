package com.owl.tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.backup.BackupCodec
import com.owl.tracker.backup.BackupContent
import com.owl.tracker.backup.BackupException
import com.owl.tracker.backup.BackupFixture
import com.owl.tracker.backup.BackupManager
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.ImportFilters
import com.owl.tracker.core.RuleMatch
import com.owl.tracker.data.NewTxn
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Source
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/** Backup files: what goes in, what never does, encryption, and restoring safely. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class BackupTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = TEST_CLOCK.millis()

    /** A phone with SMS-imported payments carrying every kind of sensitive detail, a manual one, a custom category, budgets and rules. */
    private fun phoneWithData(): AppContainer = runBlocking {
        val c = testContainer(context)
        c.repo.open()
        val pets = c.repo.saveCategory(null, "Pets", "others", 0xFF2A6FB5, countsAsSpend = true)
        c.repo.insertAll(
            listOf(
                NewTxn(
                    amountPaise = 48_600, direction = Direction.DEBIT, occurredAt = now - 3 * DAY_MS,
                    merchantName = "Swiggy", merchantKey = "swiggy", merchantPackage = "in.swiggy.android", vpa = "swiggy.upi@axisbank",
                    categoryId = Cat.FOOD, accountLabel = "ACCTLABELX ••7788", senderGroup = "hdfc", sender = "SENDERXYZ",
                    refNo = "REFQ629112345678", source = Source.SMS, smsId = 4242L, needsReview = false,
                    smsBody = "BODYTEXT Sent Rs.486.00 From HDFC Bank A/C *7788 To SWIGGY Ref 629112345678",
                ),
                NewTxn(
                    amountPaise = 50_000, direction = Direction.DEBIT, occurredAt = now - 2 * DAY_MS,
                    merchantName = "9876543210@ybl", merchantKey = "9876543210@ybl", merchantPackage = null, vpa = "9876543210@ybl",
                    categoryId = Cat.TRANSFERS, accountLabel = "ACCTLABELX ••7788", senderGroup = "hdfc", sender = "SENDERXYZ",
                    refNo = "REFQ555566667777", source = Source.SMS, smsId = 4243L, needsReview = true,
                    smsBody = "BODYTEXT Paid Rs.500 to 9876543210@ybl",
                ),
                NewTxn(
                    amountPaise = 120_000, direction = Direction.DEBIT, occurredAt = now - DAY_MS,
                    merchantName = "Vet clinic", merchantKey = "vet_clinic", merchantPackage = null, vpa = null,
                    categoryId = pets, accountLabel = null, senderGroup = null, sender = null, refNo = null,
                    source = Source.MANUAL, smsId = null, needsReview = false, note = "Annual shots",
                ),
            ),
        )
        c.repo.setBudget(Schema.OVERALL, 4_000_000)
        c.repo.setBudget(Cat.FOOD, 800_000)
        c.repo.setBudget(pets, 300_000)
        c.repo.setCategory(c.repo.transactions(0, now + 1).first { it.merchantKey == "swiggy" }.id, Cat.FOOD, rememberForMerchant = true)
        c.db.get().execSQL(
            "INSERT INTO rules(match_type, pattern, category_id, created_at) VALUES('VPA', '9876543210@ybl', 'transfers', 1)",
        )
        c.repo.updateSettings { it.copy(onboarded = true, budgetStartDay = 5) }
        c
    }

    private fun backupOf(c: AppContainer): BackupContent = runBlocking {
        BackupCodec.fromSnapshot(c.repo.snapshot(), now, "test")
    }

    private fun expect(problem: BackupException.Problem, block: () -> Unit) {
        try {
            block()
            fail("Expected $problem")
        } catch (e: BackupException) {
            assertEquals(problem, e.problem)
        }
    }

    // ---------------- what goes in, what never does ----------------

    @Test
    fun noSmsDetailsEverReachTheFile() {
        val json = String(BackupCodec.encode(backupOf(phoneWithData()), null, zone), Charsets.UTF_8)
        listOf("BODYTEXT", "SENDERXYZ", "REFQ", "629112345678", "555566667777", "ACCTLABELX", "9876543210", "@ybl", "@axisbank", "smsId", "hdfc").forEach {
            assertFalse("Backup must not contain \"$it\"", json.lowercase().contains(it.lowercase()))
        }
        // The user's own data is there
        listOf("Swiggy", "Vet clinic", "Annual shots", "Pets").forEach { assertTrue("Backup should contain \"$it\"", json.contains(it)) }
    }

    @Test
    fun rulesKeyedOnAUpiIdAreLeftOut() {
        val b = backupOf(phoneWithData())
        assertTrue(b.rules.none { it.match == RuleMatch.VPA })
        assertTrue(b.rules.any { it.match == RuleMatch.MERCHANT && it.pattern == "swiggy" })
    }

    @Test
    fun plainBackupIsReadableAndRoundTrips() {
        val bytes = BackupCodec.encode(backupOf(phoneWithData()), null, zone)
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        assertEquals("owl-tracker-backup", root.getString("format"))
        assertFalse(root.getBoolean("encrypted"))
        assertEquals(3, BackupCodec.peek(bytes).transactions)

        val back = BackupCodec.decode(bytes, null, now)
        assertEquals(3, back.transactions.size)
        assertEquals(5, back.budgetStartDay)
        assertEquals(4_000_000L, back.budgets[Schema.OVERALL])
        val masked = back.transactions.first { it.amountPaise == 50_000L }
        assertEquals("•••••", masked.merchantName)
        assertTrue(back.transactions.all { it.smsId == null && it.refNo == null && it.sender == null && it.smsBody == null && it.vpa == null && it.accountLabel == null })
        assertEquals(Source.MANUAL, back.transactions.first { it.merchantName == "Vet clinic" }.source)
        assertEquals(Source.BACKUP, back.transactions.first { it.merchantName == "Swiggy" }.source)
    }

    // ---------------- encryption ----------------

    @Test
    fun encryptedBackupHidesEverythingAndNeedsThePassword() {
        val bytes = BackupCodec.encode(backupOf(phoneWithData()), "correct horse".toCharArray(), zone)
        val text = String(bytes, Charsets.UTF_8)
        listOf("Swiggy", "Vet clinic", "Annual shots", "48600", "Pets").forEach { assertFalse("Encrypted file shows \"$it\"", text.contains(it)) }

        val header = BackupCodec.peek(bytes)
        assertTrue(header.encrypted)
        assertNull("An encrypted file doesn't even say how many payments it holds", header.transactions)

        expect(BackupException.Problem.NEEDS_PASSWORD) { BackupCodec.decode(bytes, null, now) }
        expect(BackupException.Problem.WRONG_PASSWORD) { BackupCodec.decode(bytes, "wrong horse".toCharArray(), now) }
        val back = BackupCodec.decode(bytes, "correct horse".toCharArray(), now)
        assertEquals(3, back.transactions.size)
    }

    @Test
    fun aChangedFileIsRefused() {
        val bytes = BackupCodec.encode(backupOf(phoneWithData()), "correct horse".toCharArray(), zone)
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val payload = root.getString("payload")
        val flipped = (if (payload[10] == 'A') 'B' else 'A').toString()
        root.put("payload", payload.substring(0, 10) + flipped + payload.substring(11))
        try {
            BackupCodec.decode(root.toString().toByteArray(), "correct horse".toCharArray(), now)
            fail("A changed file must be refused")
        } catch (e: BackupException) {
            assertTrue(e.problem == BackupException.Problem.WRONG_PASSWORD || e.problem == BackupException.Problem.DAMAGED)
        }
    }

    @Test
    fun opensABackupMadeByAnIndependentImplementation() {
        val bytes = BackupFixture.JSON.toByteArray()
        assertTrue(BackupCodec.peek(bytes).encrypted)
        val b = BackupCodec.decode(bytes, BackupFixture.PASSWORD.toCharArray(), now)
        assertEquals(2, b.transactions.size)
        assertEquals(5, b.budgetStartDay)
        assertEquals("Pets", b.categories.single().name)
        assertEquals("Annual shots", b.transactions.first { it.source == Source.MANUAL }.note)
        assertEquals("in.swiggy.android", b.transactions.first { it.merchantKey == "swiggy" }.merchantPackage)
    }

    @Test
    fun shortPasswordsAreRejected() {
        try {
            BackupCodec.encode(backupOf(phoneWithData()), "12345".toCharArray(), zone)
            fail("Short password accepted")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ---------------- reading untrusted files ----------------

    @Test
    fun filesThatArentBackupsAreRefused() {
        expect(BackupException.Problem.NOT_A_BACKUP) { BackupCodec.peek("not json at all".toByteArray()) }
        expect(BackupException.Problem.NOT_A_BACKUP) { BackupCodec.peek("""{"hello":"world"}""".toByteArray()) }
        expect(BackupException.Problem.NEWER_VERSION) { BackupCodec.peek("""{"format":"owl-tracker-backup","version":99}""".toByteArray()) }
        expect(BackupException.Problem.TOO_LARGE) {
            BackupManager.readLimited(object : java.io.InputStream() {
                override fun read(): Int = 'x'.code
                override fun read(b: ByteArray, off: Int, len: Int): Int { java.util.Arrays.fill(b, off, off + len, 'x'.code.toByte()); return len }
            })
        }
    }

    @Test
    fun aHandEditedFileIsCheckedAndScrubbed() {
        val evil = JSONObject()
            .put("format", "owl-tracker-backup").put("version", 1).put("encrypted", false)
            .put(
                "payload",
                JSONObject()
                    .put("settings", JSONObject().put("budgetStartDay", 99))
                    .put("categories", org.json.JSONArray()
                        .put(JSONObject().put("id", "x'; DROP TABLE transactions;--").put("name", "Evil"))
                        .put(JSONObject().put("id", "c_ok").put("name", "A".repeat(500)).put("icon", "../../etc").put("color", "zzz")))
                    .put("rules", org.json.JSONArray().put(JSONObject().put("match", "VPA").put("pattern", "98765@ybl").put("category", "food")))
                    .put("transactions", org.json.JSONArray()
                        .put(JSONObject().put("amountPaise", -500).put("direction", "DEBIT").put("time", now).put("merchant", "Negative"))
                        .put(JSONObject().put("amountPaise", 500).put("direction", "DEBIT").put("time", 631_152_000_000L).put("merchant", "1990"))
                        .put(JSONObject().put("amountPaise", 500).put("direction", "SIDEWAYS").put("time", now).put("merchant", "Bad direction"))
                        .put(
                            JSONObject().put("amountPaise", 700).put("direction", "DEBIT").put("time", now - DAY_MS)
                                .put("merchant", "Call me 9876543210\u0007").put("merchantKey", "9876543210@ybl")
                                .put("category", "x'; DROP").put("app", "com.evil.app").put("note", "line\u0000break"),
                        )),
            )
        val b = BackupCodec.decode(evil.toString().toByteArray(), null, now)
        assertEquals(28, b.budgetStartDay)
        assertEquals(listOf("c_ok"), b.categories.map { it.id })
        assertEquals(40, b.categories.single().name.length)
        assertEquals("others", b.categories.single().icon)
        assertTrue(b.rules.isEmpty())
        val t = b.transactions.single()
        assertEquals("Call me •••••", t.merchantName)
        assertEquals("xxxxx", t.merchantKey)
        assertEquals(Cat.OTHERS, t.categoryId)
        assertNull(t.merchantPackage)
        assertEquals("linebreak", t.note)
    }

    // ---------------- restoring ----------------

    @Test
    fun restoresOntoANewPhone() = runBlocking {
        val b = BackupCodec.decode(BackupCodec.encode(backupOf(phoneWithData()), "correct horse".toCharArray(), zone), "correct horse".toCharArray(), now)
        val fresh = testContainer(context)
        fresh.repo.open()
        val r = fresh.repo.restore(b, replace = true, finishOnboarding = true)
        assertEquals(3, r.added)
        assertEquals(0, r.skipped)
        val s = fresh.repo.settings()
        assertTrue(s.onboarded)
        assertFalse("SMS import stays off until the person turns it on", s.smsEnabled)
        assertEquals(5, s.budgetStartDay)
        assertTrue(fresh.repo.categories().any { it.name == "Pets" })
        assertEquals(Cat.FOOD, fresh.repo.rules().single { it.pattern == "swiggy" }.categoryKey)
        val txns = fresh.repo.transactions(0, now + 1)
        assertEquals(3, txns.size)
        assertEquals("Pets", fresh.repo.categories().first { it.id == txns.first { t -> t.merchantName == "Vet clinic" }.categoryId }.name)
    }

    @Test
    fun addingTheSameBackupTwiceAddsNothingTheSecondTime() = runBlocking {
        val b = backupOf(phoneWithData())
        val other = testContainer(context)
        other.repo.open()
        assertEquals(3, other.repo.restore(b, replace = false, finishOnboarding = false).added)
        val again = other.repo.restore(b, replace = false, finishOnboarding = false)
        assertEquals(0, again.added)
        assertEquals(3, again.skipped)
        assertEquals(3, other.repo.transactions(0, now + 1).size)
    }

    @Test
    fun replaceSwapsThisPhonesDataForTheBackups() = runBlocking {
        val b = backupOf(phoneWithData())
        val other = testContainer(context)
        other.repo.open()
        other.repo.insertAll(
            listOf(NewTxn(9_900, Direction.DEBIT, now, "Old shop", "old_shop", null, null, Cat.SHOPPING, null, null, null, null, Source.MANUAL, null, false)),
        )
        other.repo.restore(b, replace = true, finishOnboarding = false)
        val names = other.repo.transactions(0, now + 1).map { it.merchantName }.toSet()
        assertFalse("Old shop" in names)
        assertEquals(3, names.size)
        assertTrue("Built-in categories are always there", other.repo.categories().map { it.id }.containsAll(Schema.DEFAULT_CATEGORIES.map { it.id }))
    }

    @Test
    fun importingSmsAfterARestoreDoesNotDuplicatePayments() = runBlocking {
        // Old phone: imports the sample inbox, then backs up.
        val old = testContainer(context)
        val e = old.importEngine
        val scan = e.scan()
        assertEquals(13, e.commit(e.selectable(scan, ImportFilters()), ImportFilters(), leftOut = 0, replaceRange = false).saved)
        val b = backupOf(old)

        // New phone: same messages arrive (moved inbox), restore first, then import SMS.
        val fresh = testContainer(context)
        fresh.repo.open()
        assertEquals(13, fresh.repo.restore(b, replace = true, finishOnboarding = true).added)
        val r = fresh.importEngine.commit(fresh.importEngine.selectable(fresh.importEngine.scan(), ImportFilters()), ImportFilters(), leftOut = 0, replaceRange = false)
        assertEquals("Every SMS matches a restored payment", 0, r.saved)
        assertEquals(13, fresh.repo.transactions(0, now + 1).size)

        // A re-import from Settings never deletes restored history.
        fresh.importEngine.commit(emptyList(), ImportFilters(), leftOut = 0, replaceRange = true)
        assertEquals(13, fresh.repo.transactions(0, now + 1).size)
    }
}
