package com.owl.tracker.ui

import com.owl.tracker.core.CalendarRow
import com.owl.tracker.core.Cat
import com.owl.tracker.core.DayTotal
import com.owl.tracker.core.WeekCompare
import com.owl.tracker.data.Txn
import java.time.LocalDate

/** Home shows a budget month (with its calendar) or one week at a time. */
enum class HomeMode { MONTH, WEEK }

/**
 * Big payments that go out once a month whatever you do. A day's colour and the week-to-week comparison leave them
 * out, so the day an EMI goes out isn't painted red every month.
 */
val FIXED_CATEGORIES: Set<String> = setOf(Cat.EMI, Cat.RENT)

/** What a day's colour is measured against: the day's share of the month's budget, or with no budget, a usual day. */
data class DayShare(val paise: Long, val fromBudget: Boolean)

/** One Sunday-to-Saturday week on Home. */
data class WeekView(
    val start: LocalDate,
    /** "8–14 Nov". */
    val label: String,
    /** "This week so far", "Last week", "Week of 1 Nov". */
    val title: String,
    val isCurrent: Boolean,
    val days: List<DayTotal>,
    /** Every transaction in the week, money in too, newest first. */
    val txns: List<Txn>,
    val spent: Long,
    val fixed: Long,
    val compare: WeekCompare?,
    val share: DayShare,
    val byCategory: List<CategorySpend>,
    /** The week's share of the month's budget, and of each category's, for the owl. Zero with no budget. */
    val weekShare: Long,
    val categoryShares: Map<String, Long>,
    /** This budget month, while the week is the current one and a budget is set: what's left and what's safe a day. */
    val monthLeft: Long?,
    val safePerDay: Long,
    val daysLeft: Int,
    val monthName: String,
)

/** The budget month on Home as a calendar, one row a week. */
data class MonthCalendar(val rows: List<CalendarRow>, val share: DayShare, val title: String)
