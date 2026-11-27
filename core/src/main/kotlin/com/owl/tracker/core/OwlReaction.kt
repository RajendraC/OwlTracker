package com.owl.tracker.core

/**
 * How the owl reacts to a payment just added for today, against today's share of the budget (everyday spending:
 * EMIs and rent left out).
 *
 * - [OVER]: this payment took today past its share (it was within before).
 * - [BIG]: one payment of at least three quarters of the share, with today still within it.
 * - [ON_TRACK]: anything else within the share.
 * - [PLAIN]: no share to measure against, a set-aside payment (EMI, rent), a day that was already over, or a
 *   second strong note on one day; the owl just says it's saved.
 */
enum class OwlReaction {
    ON_TRACK, BIG, OVER, PLAIN;

    companion object {
        /** A single payment this big a part of the day's share counts as a big one. */
        const val BIG_PART = 0.75

        /**
         * [amount] the payment; [todayBefore] and [todayAfter] today's everyday spending without and with it;
         * [share] today's share (0 without a budget); [setAside] true for EMI and rent; [strongShownToday] true when
         * a big-spend or over note was already shown today.
         */
        fun pick(amount: Long, todayBefore: Long, todayAfter: Long, share: Long, setAside: Boolean, strongShownToday: Boolean): OwlReaction {
            if (share <= 0 || setAside || amount <= 0) return PLAIN
            if (todayBefore > share) return PLAIN
            if (todayAfter > share) return if (strongShownToday) PLAIN else OVER
            if (amount >= share * BIG_PART) return if (strongShownToday) ON_TRACK else BIG
            return ON_TRACK
        }

        /** Going [over] today's share, spread across [daysLeft] days after today: what comes off each, rounded up to a
         * whole rupee (0 on the last day). */
        fun lessEachDay(over: Long, daysLeft: Int): Long {
            if (daysLeft <= 0 || over <= 0) return 0
            val step = daysLeft * 100L
            return (over + step - 1) / step * 100
        }
    }
}
