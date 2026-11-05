package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialTest {
    private val start = 1_760_000_000_000L
    private val hour = 60 * 60 * 1000L

    @Test fun sevenDaysRightAfterInstall() {
        assertEquals(7, Trial.daysLeft(start, start))
        assertEquals(7, Trial.daysLeft(start, start + hour))
        assertFalse(Trial.isOver(start, start))
    }

    @Test fun countsDownInWholeDaysRoundedUp() {
        assertEquals(6, Trial.daysLeft(start, start + Trial.DAY_MS))
        assertEquals(6, Trial.daysLeft(start, start + Trial.DAY_MS + hour))
        assertEquals(1, Trial.daysLeft(start, start + 6 * Trial.DAY_MS))
        assertEquals(1, Trial.daysLeft(start, Trial.endsAt(start) - 1))
    }

    @Test fun endsExactlySevenDaysLater() {
        assertEquals(start + 7 * Trial.DAY_MS, Trial.endsAt(start))
        assertEquals(0, Trial.daysLeft(start, Trial.endsAt(start)))
        assertTrue(Trial.isOver(start, Trial.endsAt(start)))
        assertEquals(0, Trial.daysLeft(start, start + 30 * Trial.DAY_MS))
    }

    @Test fun aClockBeforeTheStartNeverGivesMoreThanSevenDays() {
        assertEquals(7, Trial.daysLeft(start, start - 100 * Trial.DAY_MS))
    }
}
