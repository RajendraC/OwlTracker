package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OwlReactionTest {
    private val share = 2_317_00L

    @Test fun smallPaymentIsOnTrack() =
        assertEquals(OwlReaction.ON_TRACK, OwlReaction.pick(240_00, 1_000_00, 1_240_00, share, setAside = false, strongShownToday = false))

    @Test fun threeQuartersOfTheShareIsBig() {
        val big = share * 3 / 4 + 1
        assertEquals(OwlReaction.BIG, OwlReaction.pick(big, 0, big, share, false, false))
        // just under three quarters is still on track
        val notBig = share * 3 / 4 - 100
        assertEquals(OwlReaction.ON_TRACK, OwlReaction.pick(notBig, 0, notBig, share, false, false))
    }

    @Test fun goingPastTheShareIsOver() =
        assertEquals(OwlReaction.OVER, OwlReaction.pick(900_00, 1_900_00, 2_800_00, share, false, false))

    @Test fun onceADayForStrongNotes() {
        assertEquals(OwlReaction.PLAIN, OwlReaction.pick(900_00, 1_900_00, 2_800_00, share, false, strongShownToday = true))
        assertEquals(OwlReaction.ON_TRACK, OwlReaction.pick(2_000_00, 0, 2_000_00, share, false, strongShownToday = true))
    }

    @Test fun alreadyOverOrNoBudgetOrSetAsideIsPlain() {
        assertEquals(OwlReaction.PLAIN, OwlReaction.pick(100_00, 2_500_00, 2_600_00, share, false, false))
        assertEquals(OwlReaction.PLAIN, OwlReaction.pick(100_00, 0, 100_00, 0, false, false))
        assertEquals(OwlReaction.PLAIN, OwlReaction.pick(20_000_00, 0, 0, share, setAside = true, strongShownToday = false))
    }

    @Test fun overIsSpreadOverTheDaysLeft() {
        assertEquals(53_00L, OwlReaction.lessEachDay(420_00, 8))
        assertEquals(0L, OwlReaction.lessEachDay(420_00, 0))
    }
}
