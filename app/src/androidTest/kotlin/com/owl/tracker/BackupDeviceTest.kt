package com.owl.tracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.backup.BackupCodec
import com.owl.tracker.backup.BackupContent
import com.owl.tracker.backup.BackupException
import com.owl.tracker.backup.BackupFixture
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.data.NewTxn
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * Backup encryption with the phone's own crypto (Conscrypt on Android, not the JVM's), on every
 * Android version CI runs: a backup made on one phone must open on another.
 */
@RunWith(AndroidJUnit4::class)
class BackupDeviceTest {

    private val now = System.currentTimeMillis()

    private val content = BackupContent(
        createdAt = now, appVersion = "device-test", budgetStartDay = 1,
        categories = emptyList(), budgets = mapOf(Schema.OVERALL to 2_000_000L),
        rules = emptyList(),
        transactions = listOf(
            NewTxn(48_600, Direction.DEBIT, now - 86_400_000L, "Swiggy", "swiggy", "in.swiggy.android", null, Cat.FOOD, null, null, null, null, Source.BACKUP, null, false),
            NewTxn(9_900, Direction.DEBIT, now - 3_600_000L, "Tea stall", "tea_stall", null, null, Cat.FOOD, null, null, null, null, Source.MANUAL, null, false, note = "Chai ☕"),
        ),
    )

    @Test
    fun encryptsAndDecryptsOnThisPhone() {
        val password = "pässwörd ₹ 50".toCharArray()
        val bytes = BackupCodec.encode(content, password, ZoneId.systemDefault())
        assertFalse(String(bytes, Charsets.UTF_8).contains("Swiggy"))
        val back = BackupCodec.decode(bytes, "pässwörd ₹ 50".toCharArray(), now)
        assertEquals(2, back.transactions.size)
        assertEquals("Chai ☕", back.transactions.first { it.merchantName == "Tea stall" }.note)
        try {
            BackupCodec.decode(bytes, "password".toCharArray(), now)
            fail("Wrong password accepted")
        } catch (e: BackupException) {
            assertEquals(BackupException.Problem.WRONG_PASSWORD, e.problem)
        }
    }

    @Test
    fun opensABackupMadeElsewhere() {
        val b = BackupCodec.decode(BackupFixture.JSON.toByteArray(), BackupFixture.PASSWORD.toCharArray(), now)
        assertEquals(2, b.transactions.size)
        assertEquals("Pets", b.categories.single().name)
        assertTrue(b.budgets.containsKey(Schema.OVERALL))
    }
}
