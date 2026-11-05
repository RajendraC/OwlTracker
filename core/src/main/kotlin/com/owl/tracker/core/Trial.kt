package com.owl.tracker.core

/**
 * The free trial: 7 days from install, then a one-time unlock.
 * Pure arithmetic; the app decides where "start" and "now" come from.
 */
object Trial {
    const val DAYS = 7
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val LENGTH_MS = DAYS * DAY_MS

    fun endsAt(startMs: Long): Long = startMs + LENGTH_MS

    /**
     * Whole days left, rounded up: 7 just after install, 1 during the last 24 hours, 0 once it has
     * ended. A clock set before the start never gives more than 7.
     */
    fun daysLeft(startMs: Long, nowMs: Long): Int {
        val left = endsAt(startMs) - nowMs
        if (left <= 0) return 0
        return ((left + DAY_MS - 1) / DAY_MS).toInt().coerceAtMost(DAYS)
    }

    fun isOver(startMs: Long, nowMs: Long): Boolean = nowMs >= endsAt(startMs)
}
