package com.owl.tracker.ui

import com.owl.tracker.ui.components.LaunchAct
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owl.tracker.AppContainer
import com.owl.tracker.data.AppSettings
import com.owl.tracker.data.Category
import com.owl.tracker.data.Repository
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Txn
import com.owl.tracker.backup.BackupCodec
import com.owl.tracker.backup.BackupContent
import com.owl.tracker.backup.BackupException
import com.owl.tracker.backup.BackupHeader
import com.owl.tracker.licence.Access
import com.owl.tracker.licence.StoreState
import com.owl.tracker.sms.ImportEngine
import com.owl.tracker.core.BudgetMath
import com.owl.tracker.core.BudgetPeriod
import com.owl.tracker.core.BudgetState
import com.owl.tracker.core.BudgetStatus
import com.owl.tracker.core.Direction
import com.owl.tracker.core.ImportFilters
import com.owl.tracker.core.Institutions
import com.owl.tracker.core.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Tab { HOME, ACTIVITY, BUDGETS, MORE }

/** An owl badge: earned or still to earn. */
data class Badge(val id: String, val name: String, val earnedText: String, val howTo: String, val earned: Boolean)

/** Streaks and badges together. */
data class Streak(val info: com.owl.tracker.core.StreakInfo, val badges: List<Badge>)

/** A month's recap: the story of cards shown on its first days, built on the phone from that month's payments. */
data class Recap(
    val month: String, val previousMonth: String, val period: BudgetPeriod, val spent: Long, val budget: Long, val payments: Int,
    val biggestDay: LocalDate?, val biggestDayPaise: Long, val biggestDayMerchant: String?,
    val topMerchant: String?, val topMerchantCount: Int, val topMerchantPaise: Long,
    val topCategory: Category?, val topCategoryPaise: Long,
    val noSpendDays: Int, val daysWithinShare: Int, val days: Int, val invested: Long, val previousSpent: Long,
)

enum class Screen(val tab: Tab? = null) {
    WELCOME, DISCLOSURE, IMPORT_FILTERS, IMPORT_REVIEW, IMPORTING, BUDGET_SETUP,
    HOME(Tab.HOME), ACTIVITY(Tab.ACTIVITY), BUDGETS(Tab.BUDGETS), MORE(Tab.MORE),
    CATEGORIES, SETTINGS, INSIGHTS, PRIVACY, DB_ERROR, UNLOCK, BACKUP, RULES, ADJUST_BUDGETS, STREAKS,
}

sealed interface Sheet {
    data class TxnDetail(val id: Long) : Sheet
    data class CategoryPick(val txId: Long) : Sheet
    /** "Was this right?" for a new payment: its category, or one of a few others, in one tap. */
    data class QuickCheck(val txId: Long) : Sheet
    data class Split(val txId: Long) : Sheet
    data class Rename(val txId: Long) : Sheet
    data class MerchantIcon(val txId: Long) : Sheet
    /** Which card or account a payment came from, when its SMS didn't say (or said wrong). */
    data class AccountPick(val txId: Long) : Sheet
    /** Share a report: period, page or Excel, with or without card and account numbers. */
    data object ShareReport : Sheet
    data class BudgetEdit(val key: String) : Sheet
    /** [inGroup]: a new category starts in this group. */
    data class CategoryEdit(val id: String?, val inGroup: String? = null) : Sheet
    data object AddExpense : Sheet
    data object DeleteData : Sheet
    data object Skipped : Sheet
    /** Every category budget that is over, with its buttons (opened from Home's one-line note or the Over budget figure). */
    data object OverBudget : Sheet
}

enum class ConfirmKind { DELETE, RESET }
enum class ImportMode { ONBOARDING, SETTINGS }
enum class ReviewChip { ALL, OUT, IN, UNSURE }
enum class DeleteOption { OLDER_3, OLDER_6, ACCOUNT, ALL_SMS }
enum class ActivityFilter { ALL, SPENT, RECEIVED, REVIEW }

class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class CategorySpend(val category: Category, val paise: Long)
data class CategoryBudgetRow(val category: Category, val spent: Long, val limit: Long, val status: BudgetStatus)
data class OverItem(
    val title: String, val subtitle: String, val categoryId: String?, val budgetKey: String,
    val name: String = "", val spent: Long = 0L, val limit: Long = 0L, val daysLeftText: String = "",
)

data class Dashboard(
    val period: BudgetPeriod,
    val label: String,
    val isCurrent: Boolean,
    val dayNumber: Int,
    val spent: Long,
    val overall: Long,
    val status: BudgetStatus?,
    val byCategory: List<CategorySpend>,
    val recent: List<Txn>,
    val reviewCount: Int,
    val over: List<OverItem>,
    val categoryRows: List<CategoryBudgetRow>,
    val unbudgeted: List<CategorySpend>,
    /**
     * What's inside each group in [byCategory], biggest first: its categories, and the group itself for payments not
     * sorted yet (an Amazon order). Groups only; categories on their own aren't here.
     */
    val children: Map<String, List<CategorySpend>> = emptyMap(),
)

/**
 * Spending by group (and categories on their own), and what's inside each group: Home's ring, budgets and the week
 * are by group. [spends]: payments that count as spending, split ones as their parts.
 */
fun rollUp(spends: List<Txn>, cats: List<Category>): Pair<List<CategorySpend>, Map<String, List<CategorySpend>>> {
    val tree = com.owl.tracker.data.CategoryTree(cats)
    val byLeaf = spends.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }.filterValues { it > 0 }
    val tops = byLeaf.entries.groupBy({ tree.top(it.key) }, { it.value }).mapValues { it.value.sum() }
    val byTop = tops.mapNotNull { (id, v) -> tree.byId[id]?.let { CategorySpend(it, v) } }.sortedByDescending { it.paise }
    val children = byLeaf.entries.groupBy { tree.top(it.key) }.filterKeys { tree.isGroup(it) }
        .mapValues { (_, l) -> l.mapNotNull { (id, v) -> tree.byId[id]?.let { CategorySpend(it, v) } }.sortedByDescending { it.paise } }
    return byTop to children
}


data class ImportUi(
    val mode: ImportMode = ImportMode.ONBOARDING,
    val scanning: Boolean = false,
    val scan: ImportEngine.Scan? = null,
    val draft: ImportFilters = ImportFilters(),
    val wordInput: String = "",
    val reviewOff: Set<Long> = emptySet(),
    val monthsOff: Set<YearMonth> = emptySet(),
    val chip: ReviewChip = ReviewChip.ALL,
    val running: Boolean = false,
    val progress: Float = 0f,
    val result: ImportEngine.Result? = null,
    /** Importing the built-in sample messages instead of the inbox. */
    val sample: Boolean = false,
)

/** The Backup & restore screen. Passwords live only here, in memory, while the screen is open. */
data class BackupUi(
    val protect: Boolean = true,
    val password: String = "",
    val confirm: String = "",
    /** What's running right now ("Encrypting your backup…"); null when idle. */
    val working: String? = null,
    val exported: String? = null,
    val fileName: String? = null,
    val header: BackupHeader? = null,
    val restorePassword: String = "",
    /** The unlocked, checked backup, ready to restore. */
    val content: BackupContent? = null,
    val replace: Boolean = false,
    val error: String? = null,
    val restored: Repository.RestoreCounts? = null,
) {
    val passwordProblem: String?
        get() = when {
            !protect -> null
            password.length < BackupCodec.MIN_PASSWORD -> "Use at least ${BackupCodec.MIN_PASSWORD} characters"
            confirm != password -> "The two passwords don't match"
            else -> null
        }
}

data class DeleteUi(
    val option: DeleteOption = DeleteOption.OLDER_3,
    val group: String? = null,
    val stopImporting: Boolean = true,
    val counts: Map<DeleteOption, Int> = emptyMap(),
    val groups: List<Pair<String, Int>> = emptyList(),
) {
    val selectedCount: Int get() = counts[option] ?: 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val c: AppContainer) : ViewModel() {

    private val repo = c.repo
    private val engine = c.importEngine
    private val zone: ZoneId get() = c.clock.zone
    fun today(): LocalDate = LocalDate.now(c.clock)

    /** A new version from Google Play (the card on Home), and What's new after updating. */
    val appUpdate get() = c.updates.state
    val whatsNew get() = c.updates.whatsNew
    fun updateNow() = c.updates.update()
    fun updateLater() = c.updates.later()
    fun restartToUpdate() = c.updates.restart()
    fun finishUpdateInPlay() = c.updates.openPlay()
    fun closeWhatsNew() = c.updates.closeWhatsNew()
    val timeZone: ZoneId get() = zone
    fun now(): Long = c.clock.millis()
    val appIcons get() = c.appIcons

    // ---------------- navigation ----------------

    private val _screen = MutableStateFlow<Screen?>(null)
    val screen: StateFlow<Screen?> = _screen.asStateFlow()
    private val history = ArrayDeque<Screen>()

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    private val _confirm = MutableStateFlow<ConfirmKind?>(null)
    val confirm: StateFlow<ConfirmKind?> = _confirm.asStateFlow()

    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val locked = MutableStateFlow(false)
    private var backgroundAt: Long? = null

    fun go(s: Screen) {
        val cur = _screen.value
        if (cur != null && cur != s) {
            if (s.tab != null) history.clear()
            else if (cur != Screen.IMPORTING) history.addLast(cur)
        }
        _screen.value = s
    }

    /** Returns false when there is nowhere to go back to, so the system can close the app. */
    fun back(): Boolean {
        if (_confirm.value != null) { _confirm.value = null; return true }
        if (_sheet.value != null) { closeSheet(); return true }
        val cur = _screen.value ?: return false
        if (cur == Screen.IMPORTING) return true
        if (history.isNotEmpty()) { _screen.value = history.removeLast(); return true }
        if (cur.tab != null && cur != Screen.HOME) { _screen.value = Screen.HOME; return true }
        return false
    }

    fun openSheet(s: Sheet) {
        val edits = s is Sheet.AddExpense || s is Sheet.BudgetEdit || s is Sheet.CategoryEdit || s is Sheet.CategoryPick ||
            s is Sheet.Rename || s is Sheet.MerchantIcon
        if (edits && !editable()) return
        _sheet.value = s
    }
    fun closeSheet() { _sheet.value = null }
    fun dismissConfirm() { _confirm.value = null }

    private fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        messages.tryEmit(UiMessage(text, actionLabel, action))
    }

    // ---------------- data ----------------

    val settings: StateFlow<AppSettings?> = repo.observeSettings()
        .catch { _screen.value = Screen.DB_ERROR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<Map<String, Long>> = repo.observeBudgets()
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val storage: StateFlow<Repository.Storage> = repo.observeStorage()
        .catch { emit(Repository.Storage(0, null, emptyMap())) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Repository.Storage(0, null, emptyMap()))

    val ruleCount: StateFlow<Int> = repo.observeRuleCount().catch { emit(0) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val monthOffset = MutableStateFlow(0)

    val period: StateFlow<BudgetPeriod> = combine(settings, monthOffset) { s, off -> periodAt(s?.budgetStartDay ?: 1, off) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, periodAt(1, 0))

    val canGoForward: StateFlow<Boolean> = monthOffset.map { it < 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun previousMonth() = monthOffset.update { (it - 1).coerceAtLeast(-24) }
    fun nextMonth() = monthOffset.update { (it + 1).coerceAtMost(0) }

    val periodTxns: StateFlow<List<Txn>> = period
        .flatMapLatest { p -> repo.observeTransactions(startMs(p.start), startMs(p.endExclusive)) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dashboard: StateFlow<Dashboard?> = combine(periodTxns, categories, budgets, period) { t, cats, b, p -> buildDashboard(t, cats, b, p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------------- Home: a month or a week ----------------

    val homeMode = MutableStateFlow(HomeMode.MONTH)
    private val weekStart = MutableStateFlow(com.owl.tracker.core.Weeks.startOf(today()))

    val canGoForwardWeek: StateFlow<Boolean> = weekStart.map { it.isBefore(com.owl.tracker.core.Weeks.startOf(today())) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Week view: this week if the month on screen is the current one, else that month's last week. */
    fun showWeek() {
        val p = period.value
        val t = today()
        weekStart.value = com.owl.tracker.core.Weeks.startOf(if (p.contains(t)) t else p.endExclusive.minusDays(1))
        homeMode.value = HomeMode.WEEK
    }

    /** A week from the month's calendar. */
    fun openWeek(start: LocalDate) {
        val current = com.owl.tracker.core.Weeks.startOf(today())
        weekStart.value = com.owl.tracker.core.Weeks.startOf(start).let { if (it.isAfter(current)) current else it }
        homeMode.value = HomeMode.WEEK
    }

    /** Month view: the budget month the week on screen ends in (or today, for this week). */
    fun showMonth() {
        val t = today()
        val day = weekStart.value.plusDays(6).let { if (it.isAfter(t)) t else it }
        val startDay = settings.value?.budgetStartDay ?: 1
        var p = BudgetMath.periodFor(t, startDay)
        var off = 0
        while (!p.contains(day) && off > -24) { p = p.previous(startDay); off-- }
        monthOffset.value = off
        homeMode.value = HomeMode.MONTH
    }

    private fun earlier(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) a else b

    fun previousWeek() = weekStart.update { if (it.isAfter(today().minusYears(2))) it.minusDays(7) else it }
    fun nextWeek() = weekStart.update { if (it.isBefore(com.owl.tracker.core.Weeks.startOf(today()))) it.plusDays(7) else it }

    /**
     * The week's transactions and the four weeks before (for the comparison, and a usual day when there's no budget),
     * reaching back to the start of the week's budget month for what's left this month. Only read in week view.
     */
    private val weekWindow: StateFlow<Pair<LocalDate?, List<Txn>>> = combine(homeMode, weekStart, settings) { m, s, st ->
        if (m == HomeMode.WEEK) s to (st?.budgetStartDay ?: 1) else null
    }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            val window: Flow<Pair<LocalDate?, List<Txn>>> = if (key == null) kotlinx.coroutines.flow.flowOf(null to emptyList())
            else {
                val (s, startDay) = key
                val from = earlier(s.minusDays(28), BudgetMath.periodFor(earlier(s.plusDays(6), today()), startDay).start)
                repo.observeTransactions(startMs(from), startMs(s.plusDays(7))).map { s to it }
            }
            window
        }
        .catch { emit(null to emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null to emptyList())

    val weekView: StateFlow<WeekView?> = combine(weekWindow, categories, budgets, settings, weekStart) { (s, t), cats, b, st, ws ->
        if (s == null || s != ws) null else buildWeekView(s, t, cats, b, st?.budgetStartDay ?: 1)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun spendsOf(txns: List<Txn>, catMap: Map<String, Category>) = txns.flatMap { it.portions() }.filter { counts(it, catMap) }
        .map { com.owl.tracker.core.DaySpend(dateOf(it.occurredAt), it.amountPaise, it.categoryId in FIXED_CATEGORIES, catMap[it.categoryId]?.isInvestment == true) }

    private fun buildWeekView(start: LocalDate, window: List<Txn>, cats: List<Category>, budgets: Map<String, Long>, startDay: Int): WeekView {
        val w = com.owl.tracker.core.Weeks
        val catMap = cats.associateBy { it.id }
        val today = today()
        val end = start.plusDays(7)
        val spends = spendsOf(window, catMap)
        // EMIs and rent are left out of the days, so their part of the budget is set aside before sharing it out
        val overall = w.everydayBudget(budgets[Schema.OVERALL] ?: 0L, FIXED_CATEGORIES.sumOf { budgets[it] ?: 0L }, spends.filter { it.fixed }.sumOf { it.paise })
        val usual = w.usualDay(spends, start.minusDays(28), start).takeIf { it > 0 }
            ?: w.usualDay(spends, start, earlier(end, today.plusDays(1)))
        val share = if (overall > 0) DayShare(w.dailyShare(overall, BudgetMath.periodFor(earlier(end.minusDays(1), today), startDay).days), true) else DayShare(usual, false)
        val shareOf: (LocalDate) -> Long = if (overall > 0) { d -> w.dailyShare(overall, BudgetMath.periodFor(d, startDay).days) } else { _ -> usual }
        val days = w.totals(w.daysOf(start), spends, today, shareOf)
        val inWeek = window.filter { val d = dateOf(it.occurredAt); !d.isBefore(start) && d.isBefore(end) }
        val counted = inWeek.flatMap { it.portions() }.filter { counts(it, catMap) }
        val byCategory = rollUp(counted, cats).first
        val periodDays = BudgetMath.periodFor(earlier(end.minusDays(1), today), startDay).days
        val isCurrent = !today.isBefore(start) && today.isBefore(end)
        val mon = { d: LocalDate -> d.format(SHORT) }
        val title = when {
            isCurrent -> "This week so far"
            start == w.startOf(today).minusDays(7) -> "Last week"
            else -> "Week of ${mon(start)}"
        }
        // What's left this budget month, as the month card would say it
        var monthLeft: Long? = null
        var safe = 0L
        var daysLeft = 0
        val month = BudgetMath.periodFor(today, startDay)
        if (isCurrent && overall > 0) {
            val spentMonth = spends.filter { month.contains(it.date) }.sumOf { it.paise }
            val status = BudgetMath.status(spentMonth, overall, month.dayNumber(today), month.days)
            monthLeft = overall - spentMonth
            safe = status.safePerDayPaise
            daysLeft = month.days - month.dayNumber(today)
        }
        return WeekView(
            start = start, label = w.label(start), title = title, isCurrent = isCurrent, days = days, txns = inWeek,
            spent = counted.sumOf { it.amountPaise }, fixed = counted.filter { it.categoryId in FIXED_CATEGORIES }.sumOf { it.amountPaise },
            compare = w.compare(start, spends, today), share = share, byCategory = byCategory,
            weekShare = if (overall > 0) overall * 7 / periodDays else 0L,
            categoryShares = budgets.filterKeys { it != Schema.OVERALL }.mapValues { it.value * 7 / periodDays },
            monthLeft = monthLeft, safePerDay = safe, daysLeft = daysLeft,
            monthName = if (month.start.dayOfMonth == 1) month.start.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)) else "this budget month",
        )
    }

    /** The budget month on screen as a calendar, a row a week, each day coloured against its share of the budget. */
    fun monthCalendar(dash: Dashboard, txns: List<Txn>, cats: List<Category>): MonthCalendar {
        val w = com.owl.tracker.core.Weeks
        val catMap = cats.associateBy { it.id }
        val today = today()
        val p = dash.period
        val spends = spendsOf(txns, catMap)
        // EMIs and rent are left out of the days, so their part of the budget is set aside before sharing it out
        val everyday = w.everydayBudget(dash.overall, FIXED_CATEGORIES.sumOf { budgets.value[it] ?: 0L }, spends.filter { it.fixed }.sumOf { it.paise })
        val share = if (dash.overall > 0) DayShare(w.dailyShare(everyday, p.days), true)
        else DayShare(w.usualDay(spends, p.start, earlier(p.endExclusive, today.plusDays(1))), false)
        val title = if (p.start.dayOfMonth == 1) p.start.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)) else dash.label
        return MonthCalendar(w.calendar(p.start, p.endExclusive, spends, today) { share.paise }, share, title, firstPaymentDay.value)
    }

    // ---------------- Streaks, badges and no-spend days ----------------

    /** The first day with a recorded payment: days before it are neither no-spend days nor streak days. */
    val firstPaymentDay: StateFlow<LocalDate?> = repo.observeStorage()
        .map { runCatching { repo.earliestTransaction()?.let { ms -> dateOf(ms) } }.getOrNull() }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The last 400 days of payments, for streaks and badges. */
    private val streakTxns: StateFlow<List<Txn>> = repo.observeTransactions(startMs(today().minusDays(400)), startMs(today().plusDays(1)))
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Streaks against today's share (the budget after EMIs and rent, over this month's days; the same share is
     * used for earlier days), and the badges earned. Null without a monthly budget.
     */
    val streaks: StateFlow<Streak?> = combine(streakTxns, categories, budgets, settings, firstPaymentDay) { txns, cats, b, s, first ->
        val overall = b[Schema.OVERALL] ?: 0L
        if (overall <= 0 || s == null) return@combine null
        val catMap = cats.associateBy { it.id }
        val today = today()
        val period = BudgetMath.periodFor(today, s.budgetStartDay)
        val counted = txns.flatMap { it.portions() }.filter { counts(it, catMap) }
        val inMonth = counted.filter { val d = dateOf(it.occurredAt); !d.isBefore(period.start) && d.isBefore(period.endExclusive) }
        val everydayBudget = com.owl.tracker.core.Weeks.everydayBudget(overall, FIXED_CATEGORIES.sumOf { b[it] ?: 0L }, inMonth.filter { it.categoryId in FIXED_CATEGORIES }.sumOf { it.amountPaise })
        val share = com.owl.tracker.core.Weeks.dailyShare(everydayBudget, period.days)
        val everyday = counted.filter { it.categoryId !in FIXED_CATEGORIES }.groupBy { dateOf(it.occurredAt) }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        val months = generateSequence(period.previous(s.budgetStartDay)) { it.previous(s.budgetStartDay) }.take(12)
            .takeWhile { first != null && it.endExclusive.isAfter(first) }.toList()
        val info = com.owl.tracker.core.Streaks.compute(everyday, share, first, today, period, months)
        // Finished months within the budget, newest first
        val monthSpent = months.map { m -> counted.filter { val d = dateOf(it.occurredAt); !d.isBefore(m.start) && d.isBefore(m.endExclusive) }.sumOf { it.amountPaise } }
        val under = monthSpent.map { it in 1..overall }
        val twoInARow = under.zipWithNext().any { (a, c) -> a && c }
        val invested = counted.any { catMap[it.categoryId]?.isInvestment == true }
        val reviewLeft = txns.count { it.needsReview }
        val badges = listOf(
            Badge("wise_start", "Wise start", "A month of tracking", "Track your spending for a month", first != null && !first.isAfter(today.minusDays(30))),
            Badge("thrifty", "Thrifty owl", "7 days in a row within your share", "7 days in a row within your share", info.bestDays >= 7),
            Badge("on_target", "On target", "3 weeks in a row within budget", "3 weeks in a row within budget", info.bestWeeks >= 3),
            Badge("saver", "Saver", "A month under budget", "Finish a month under budget", under.any { it }),
            Badge("zero_day", "Zero day", "5 no-spend days in a month", "5 no-spend days in one month", info.bestNoSpendMonth >= 5),
            Badge("sorted", "Sorted owl", "Nothing left to sort", "Sort every payment", reviewLeft == 0 && txns.size >= 30),
            Badge("steady", "Steady", "30 days in a row within your share", "30 days in a row within your share", info.bestDays >= 30),
            Badge("two_months", "Two in a row", "Two months under budget in a row", "Two months under budget in a row", twoInARow),
            Badge("investor", "Investor", "Put money into investments", "Put money into investments", invested),
        )
        Streak(info, badges)
    }.catch { emit(null) }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Yesterday, if nothing went out (for Home's little celebration), and not yet put away. */
    fun noSpendYesterday(st: Streak?): LocalDate? {
        if (!homeOffersOn || st == null) return null
        val y = st.info.lastSeven.getOrNull(5) ?: return null
        return y.first.takeIf { y.second == com.owl.tracker.core.DayMark.NO_SPEND && !nudgePrefs.getBoolean("nospend_seen_${it}", false) }
    }

    fun dismissNoSpend(d: LocalDate) = nudgePrefs.edit().putBoolean("nospend_seen_$d", true).apply()

    /** A day's share of the month's budget and of each category's budget, for the owl when one day is picked. */
    fun dayShares(day: LocalDate): Pair<Long, Map<String, Long>> {
        val b = budgets.value
        val days = BudgetMath.periodFor(day, settings.value?.budgetStartDay ?: 1).days
        return (b[Schema.OVERALL] ?: 0L) / days to b.filterKeys { it != Schema.OVERALL }.mapValues { it.value / days }
    }

    fun txnFlow(id: Long): Flow<Txn?> = repo.observeTransaction(id)

    // Activity tab
    val query = MutableStateFlow("")
    val activityFilter = MutableStateFlow(ActivityFilter.ALL)
    val categoryFilter = MutableStateFlow<String?>(null)
    /** One day picked from Home's calendar: Activity shows only its payments. */
    val dayFilter = MutableStateFlow<LocalDate?>(null)

    val activity: StateFlow<List<Txn>> = combine(combine(periodTxns, dayFilter) { t, d -> if (d == null) t else t.filter { dateOf(it.occurredAt) == d } }, query, activityFilter, categoryFilter, categories) { txns, q, f, cf, cats ->
        val names = cats.associate { it.id to it.name.lowercase() }
        val tree = com.owl.tracker.data.CategoryTree(cats)
        // A group picked: everything in it
        val needle = q.trim().lowercase()
        txns.filter { t ->
            (f != ActivityFilter.SPENT || t.direction == Direction.DEBIT) &&
                (f != ActivityFilter.RECEIVED || t.direction == Direction.CREDIT) &&
                (f != ActivityFilter.REVIEW || t.needsReview) &&
                (cf == null || tree.top(t.categoryId) == cf || t.categoryId == cf || t.parts.any { it.categoryId == cf || tree.top(it.categoryId) == cf }) &&
                (needle.isEmpty() || t.merchantName.lowercase().contains(needle) || (names[t.categoryId] ?: "").contains(needle) ||
                    t.parts.any { (names[it.categoryId] ?: "").contains(needle) } ||
                    (t.note ?: "").lowercase().contains(needle))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Activity with only [day]'s payments, from a tap on Home's calendar. */
    fun showActivityDay(day: LocalDate) {
        showActivity()
        dayFilter.value = day
    }

    fun showActivity(category: String? = null, filter: ActivityFilter = ActivityFilter.ALL) {
        dayFilter.value = null
        categoryFilter.value = category
        activityFilter.value = filter
        query.value = ""
        go(Screen.ACTIVITY)
    }

    // Reports
    val reportRange = MutableStateFlow(ReportRange.ONE)
    val reportFilter = MutableStateFlow(ReportFilter())

    val report: StateFlow<Report?> = combine(settings, budgets, reportRange) { s, b, r -> Triple(s?.budgetStartDay ?: 1, b[Schema.OVERALL] ?: 0L, r) }
        .distinctUntilChanged()
        .flatMapLatest { (startDay, budget, range) ->
            val periods = (range.months - 1 downTo 0).map { periodAt(startDay, -it) }
            val previous = (2 * range.months - 1 downTo range.months).map { periodAt(startDay, -it) }
            combine(
                repo.observeTransactions(startMs(previous.first().start), startMs(periods.last().endExclusive)),
                reportFilter,
                categories,
            ) { txns, filter, cats ->
                ReportBuilder.build(txns, cats, periods, previous, budget, range, filter, today(), zone) as Report?
            }.flowOn(Dispatchers.Default)
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Ways to save, from the last 3 budget months and this one; for Home and Reports. */
    val savingPlan: StateFlow<SavingPlan?> = settings.map { it?.budgetStartDay ?: 1 }
        .distinctUntilChanged()
        .flatMapLatest { startDay ->
            val current = periodAt(startDay, 0)
            val history = (SavingTips.HISTORY_MONTHS downTo 1).map { periodAt(startDay, -it) }
            combine(repo.observeTransactions(startMs(history.first().start), startMs(current.endExclusive)), categories) { txns, cats ->
                SavingTips.build(txns, cats, history, current, today(), zone) as SavingPlan?
            }.flowOn(Dispatchers.Default)
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The last six complete budget months and this one, by category: what the budget sliders start from and Reports'
     * trend by category draws. Null while loading; [MonthHistory.EMPTY] if it can't be read.
     */
    val monthHistory: StateFlow<MonthHistory?> = settings.map { it?.budgetStartDay ?: 1 }
        .distinctUntilChanged()
        .flatMapLatest { startDay ->
            val current = periodAt(startDay, 0)
            val months = (com.owl.tracker.core.Trends.MONTHS downTo 1).map { periodAt(startDay, -it) }
            combine(repo.observeTransactions(startMs(months.first().start), startMs(current.endExclusive)), categories) { txns, cats ->
                // One bad read shouldn't end the flow: the next change tries again
                runCatching { MonthHistory.build(txns, cats, months, current, zone) }
                    .onFailure { android.util.Log.w("OwlHistory", "Couldn't add up the months", it) }
                    .getOrDefault(MonthHistory.EMPTY) as MonthHistory?
            }.flowOn(Dispatchers.Default)
        }
        .catch { android.util.Log.w("OwlHistory", "Month history stopped", it); emit(MonthHistory.EMPTY) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** First-time setup with the sliders: the month budget is the total of the categories; ₹0 means no budget. */
    fun finishOnboardingWithBudgets(values: Map<String, Long>) = viewModelScope.launch {
        writeBudgets(values)
        repo.updateSettings { it.copy(onboarded = true) }
        history.clear()
        _screen.value = Screen.HOME
    }

    /** Budgets → Adjust budgets: the same, then back to Budgets. */
    fun saveBudgets(values: Map<String, Long>) = viewModelScope.launch {
        if (!editable()) return@launch
        val total = writeBudgets(values)
        back()
        say("Budgets saved · ${Money.formatInr(total)} a month")
    }

    private suspend fun writeBudgets(values: Map<String, Long>): Long {
        val total = values.values.filter { it > 0 }.sum()
        values.forEach { (id, v) -> repo.setBudget(id, v.takeIf { it > 0 }) }
        repo.setBudget(Schema.OVERALL, total.takeIf { it > 0 })
        return total
    }

    /** True while the user has hidden saving tips from Home for this budget month. */
    fun tipsHidden(s: AppSettings?): Boolean = (s?.tipsHiddenUntil ?: 0) > startMs(today())

    fun hideSavingTips() = viewModelScope.launch {
        val until = startMs(periodAt(settings.value?.budgetStartDay ?: 1, 0).endExclusive)
        repo.updateSettings { it.copy(tipsHiddenUntil = until) }
        say("Saving tips hidden until next month. They're always in Reports.")
    }

    /** Set by [openWaysToSave]: Reports scrolls to that card once, then clears it. */
    val reportScrollTo = MutableStateFlow<String?>(null)

    /** Reports for this month with nothing picked, scrolled to Ways to save (from Home's "Could save" and nudge). */
    // ---------------- The month's recap, and opening from nudges and widgets ----------------

    /** The recap being shown (a story of cards), or null. */
    val recap = MutableStateFlow<Recap?>(null)

    private val nudgePrefs get() = com.owl.tracker.nudge.Nudges.prefs(c.context)

    /** The month just ended, while its recap is new (the first week of the next) and not yet seen or put away. */
    fun recapOffer(settings: AppSettings?): BudgetPeriod? {
        val s = settings ?: return null
        if (!homeOffersOn || !s.onboarded) return null
        val now = BudgetMath.periodFor(today(), s.budgetStartDay)
        if (now.dayNumber(today()) > 7) return null
        val last = now.previous(s.budgetStartDay)
        return last.takeIf { !nudgePrefs.getBoolean("recap_seen_${it.start}", false) }
    }

    /** Android 13+: whether to ask, on Home, if the owl may nudge (once; never again after Not now). */
    fun askForNudges(settings: AppSettings?): Boolean =
        homeOffersOn && settings?.onboarded == true && android.os.Build.VERSION.SDK_INT >= 33 &&
            !com.owl.tracker.nudge.Nudges.allowed(c.context) && !nudgePrefs.getBoolean("ask_done", false)

    fun nudgeAskDone() = nudgePrefs.edit().putBoolean("ask_done", true).apply()

    fun dismissRecapOffer(p: BudgetPeriod) = nudgePrefs.edit().putBoolean("recap_seen_${p.start}", true).apply()

    /** Opens the recap of [p] (by default the month just ended). */
    fun openRecap(p: BudgetPeriod? = null) {
        viewModelScope.launch {
            val start = settings.value?.budgetStartDay ?: 1
            val period = p ?: BudgetMath.periodFor(today(), start).previous(start)
            runCatching { recap.value = buildRecap(period, start) }
            dismissRecapOffer(period)
        }
    }

    fun closeRecap() { recap.value = null }

    /** From a nudge or a widget. */
    fun openFromOutside(what: String) {
        // "check:<id>": the Change button on a new payment's nudge
        what.removePrefix("check:").toLongOrNull()?.takeIf { what.startsWith("check:") }?.let { id ->
            go(Screen.HOME); openSheet(Sheet.QuickCheck(id)); return
        }
        when (what) {
            com.owl.tracker.nudge.Nudges.OPEN_BUDGETS -> go(Screen.BUDGETS)
            com.owl.tracker.nudge.Nudges.OPEN_ADD -> { go(Screen.HOME); openSheet(Sheet.AddExpense) }
            com.owl.tracker.nudge.Nudges.OPEN_RECAP -> { go(Screen.HOME); openRecap() }
            com.owl.tracker.nudge.Nudges.OPEN_ACTIVITY -> showActivity()
            else -> go(Screen.HOME)
        }
    }

    private suspend fun buildRecap(p: BudgetPeriod, startDay: Int): Recap {
        val cats = repo.categories()
        val catMap = cats.associateBy { it.id }
        val txns = repo.transactions(startMs(p.start), startMs(p.endExclusive))
        val counted = txns.flatMap { it.portions() }.filter { counts(it, catMap) }
        val spent = counted.sumOf { it.amountPaise }
        val budget = repo.budgets()[Schema.OVERALL] ?: 0L
        val byDay = counted.groupBy { dateOf(it.occurredAt) }
        val biggest = byDay.maxByOrNull { (_, l) -> l.sumOf { it.amountPaise } }
        val biggestMerchant = biggest?.value?.maxByOrNull { it.amountPaise }?.merchantName
        val merchants = counted.filter { it.merchantKey.isNotBlank() }.groupBy { it.merchantKey }
        val top = merchants.maxByOrNull { (_, l) -> l.sumOf { it.amountPaise } }?.value
        val tree = com.owl.tracker.data.CategoryTree(cats)
        val byTop = counted.groupBy { tree.top(it.categoryId) }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        val topCat = byTop.maxByOrNull { it.value }
        val lastDay = minOf(p.endExclusive.minusDays(1), today())
        val allDays = generateSequence(p.start) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
        val fixedSpent = counted.filter { it.categoryId in FIXED_CATEGORIES }.sumOf { it.amountPaise }
        val share = com.owl.tracker.core.Weeks.dailyShare(
            com.owl.tracker.core.Weeks.everydayBudget(budget, FIXED_CATEGORIES.sumOf { repo.budgets()[it] ?: 0L }, fixedSpent), p.days,
        )
        val everydayByDay = allDays.associateWith { d -> byDay[d].orEmpty().filter { it.categoryId !in FIXED_CATEGORIES }.sumOf { it.amountPaise } }
        val previous = p.previous(startDay)
        val prevSpent = repo.transactions(startMs(previous.start), startMs(previous.endExclusive))
            .flatMap { it.portions() }.filter { counts(it, catMap) }.sumOf { it.amountPaise }
        return Recap(
            month = com.owl.tracker.nudge.Nudges.monthName(p), previousMonth = com.owl.tracker.nudge.Nudges.monthName(previous),
            period = p, spent = spent, budget = budget, payments = counted.size,
            biggestDay = biggest?.key, biggestDayPaise = biggest?.value?.sumOf { it.amountPaise } ?: 0, biggestDayMerchant = biggestMerchant,
            topMerchant = top?.first()?.merchantName, topMerchantCount = top?.size ?: 0, topMerchantPaise = top?.sumOf { it.amountPaise } ?: 0,
            topCategory = topCat?.key?.let { catMap[it] }, topCategoryPaise = topCat?.value ?: 0,
            noSpendDays = everydayByDay.count { it.value == 0L }, daysWithinShare = if (share > 0) everydayByDay.count { it.value <= share } else 0,
            days = allDays.size, invested = counted.filter { catMap[it.categoryId]?.isInvestment == true }.sumOf { it.amountPaise },
            previousSpent = prevSpent,
        )
    }

    fun openWaysToSave() {
        reportRange.value = ReportRange.ONE
        reportFilter.value = ReportFilter()
        reportScrollTo.value = "ways"
        go(Screen.INSIGHTS)
    }

    /**
     * Home's Where it went tab: "month", "six" or "paid". Kept while the app is open, so leaving Home and coming back
     * keeps it, but the app always opens on This month.
     */
    private val _whereView = MutableStateFlow("month")
    val whereView: StateFlow<String> = _whereView.asStateFlow()

    /** Where it went: this month or six months. */
    fun setWhereSixMonths(on: Boolean) { _whereView.value = if (on) "six" else "month" }

    /** Where it went's Paid by tab; turning it off shows This month. */
    fun setWherePaidBy(on: Boolean) { _whereView.value = if (on) "paid" else "month" }

    /** A credit card's billing cycle; null forgets it. */
    fun setCardCycle(label: String, cycle: CardCycle?) = viewModelScope.launch {
        repo.updateSettings { s ->
            val m = CardBills.cyclesFromJson(s.cardCycles)
            s.copy(cardCycles = CardBills.cyclesToJson(if (cycle == null) m - label else m + (label to cycle)))
        }
        say(if (cycle == null) "Billing cycle removed" else "Billing cycle saved: bill on ${cycle.dayWords}")
    }

    /** Takes back "paid in full" on a card's bill dated [bill]. */
    fun unmarkCardPaid(label: String, bill: LocalDate) {
        val idx = CardBills.paymentsFromJson(settings.value?.cardPayments)[label].orEmpty().indexOfLast { it.full && it.bill == bill }
        if (idx >= 0) removeCardPayment(label, idx)
    }

    /** Records a payment towards a card's bill dated [bill]: [paise], or the whole bill when [full]. */
    fun addCardPayment(label: String, bill: LocalDate, paise: Long, full: Boolean) = viewModelScope.launch {
        repo.updateSettings { s ->
            val m = CardBills.paymentsFromJson(s.cardPayments)
            val list = m[label].orEmpty().filter { it.bill.isAfter(bill.minusYears(1)) } + CardPayment(bill, paise, now(), full)
            s.copy(cardPayments = CardBills.paymentsToJson(m + (label to list)))
        }
        say(if (full) "Marked as paid in full" else "Payment of ${Money.formatInr(paise)} added")
    }

    /** Takes back a payment the user recorded ([index] in the card's list). */
    fun removeCardPayment(label: String, index: Int) = viewModelScope.launch {
        repo.updateSettings { s ->
            val m = CardBills.paymentsFromJson(s.cardPayments)
            val list = m[label].orEmpty().filterIndexed { i, _ -> i != index }
            s.copy(cardPayments = CardBills.paymentsToJson(if (list.isEmpty()) m - label else m + (label to list)))
        }
    }

    /**
     * A credit on a credit card counts as a bill payment ([payment] true) or a refund (false); null goes back to
     * what the app works out from the SMS.
     */
    fun setCardCreditKind(txnId: Long, payment: Boolean?) = viewModelScope.launch {
        repo.updateSettings { s ->
            val m = CardBills.creditKindsFromJson(s.cardCreditKinds)
            s.copy(cardCreditKinds = CardBills.creditKindsToJson(if (payment == null) m - txnId else m + (txnId to payment)))
        }
        say(when (payment) { true -> "Counted as a bill payment"; false -> "Counted as a refund"; null -> "Back to what the SMS says" })
    }

    /** Every payment from [from] to now, for a card's bills (they can reach back two months). */
    suspend fun txnsSince(from: LocalDate): List<Txn> =
        repo.transactions(from.atStartOfDay(zone).toInstant().toEpochMilli(), now() + 1)

    /**
     * Sets what a card or account is (credit card, debit card or bank account), for all its payments; null goes back to
     * what the app worked out from its SMS.
     */
    fun setPaidKind(label: String, kind: com.owl.tracker.ui.PaidKind?) = viewModelScope.launch {
        repo.updateSettings { s -> s.copy(paidKinds = if (kind == null) s.paidKinds - label else s.paidKinds + (label to kind.key)) }
        say(if (kind == null) "$label: back to automatic" else "$label is now a ${kind.label.lowercase()}")
    }

    /** Home's "What's changing" → All: Reports, scrolled to the trend by category. */
    fun openCategoryTrends() {
        reportRange.value = ReportRange.ONE
        reportFilter.value = ReportFilter()
        reportScrollTo.value = "trends"
        go(Screen.INSIGHTS)
    }

    fun setReportRange(r: ReportRange) {
        reportRange.value = r
        reportFilter.update { it.copy(bucket = null, day = null) }
    }

    /** Tapping a bar, slice, merchant or weekday picks it; tapping it again lets go. */
    fun tapReportBucket(i: Int) = reportFilter.update { it.copy(bucket = if (it.bucket == i) null else i, day = null) }
    /** Inside a drilled-into month: picks one of its days, or lets go. */
    fun tapReportDay(i: Int) = reportFilter.update { it.copy(day = if (it.day == i) null else i) }
    fun tapReportCategory(id: String) = reportFilter.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    fun tapReportMerchant(key: String) = reportFilter.update { it.copy(merchantKey = if (it.merchantKey == key) null else key) }
    fun tapReportWeekday(d: java.time.DayOfWeek) = reportFilter.update { it.copy(weekday = if (it.weekday == d) null else d) }

    /** Back inside Reports: up one level of what was opened or picked: merchant, category, day, month, weekday. */
    fun reportBack() = reportFilter.update { f ->
        when {
            f.merchantKey != null -> f.copy(merchantKey = null)
            f.categoryId != null -> f.copy(categoryId = null)
            f.day != null -> f.copy(day = null)
            f.bucket != null -> f.copy(bucket = null)
            else -> f.copy(weekday = null)
        }
    }

    fun clearReportFilter(kind: FilterKind? = null) = reportFilter.update {
        when (kind) {
            null -> ReportFilter()
            FilterKind.BUCKET -> it.copy(bucket = null, day = null)
            FilterKind.DAY -> it.copy(day = null)
            FilterKind.CATEGORY -> it.copy(categoryId = null)
            FilterKind.MERCHANT -> it.copy(merchantKey = null)
            FilterKind.WEEKDAY -> it.copy(weekday = null)
        }
    }

    // ---------------- backup & restore ----------------

    val backupUi = MutableStateFlow(BackupUi())

    /** Backup files to hand to the share sheet; the screen passes them to the platform. */
    val shareRequests = MutableSharedFlow<java.io.File>(extraBufferCapacity = 1)

    private var pickedBytes: ByteArray? = null

    fun backupFileName(): String = c.backups.fileName()

    fun openBackup() {
        backupUi.value = BackupUi()
        pickedBytes = null
        go(Screen.BACKUP)
    }

    fun setBackupProtect(on: Boolean) = backupUi.update { it.copy(protect = on, error = null) }
    fun setBackupPassword(s: String) = backupUi.update { it.copy(password = s, error = null) }
    fun setBackupConfirm(s: String) = backupUi.update { it.copy(confirm = s, error = null) }
    fun setRestorePassword(s: String) = backupUi.update { it.copy(restorePassword = s, error = null) }
    fun setRestoreReplace(on: Boolean) = backupUi.update { it.copy(replace = on) }

    private suspend fun buildBackup(): ByteArray? {
        val ui = backupUi.value
        if (ui.passwordProblem != null) return null
        val pw = if (ui.protect) ui.password.toCharArray() else null
        backupUi.update { it.copy(working = if (pw != null) "Encrypting your backup\u2026" else "Preparing your backup\u2026", error = null, exported = null) }
        return try {
            c.backups.build(pw)
        } catch (e: Exception) {
            backupUi.update { it.copy(working = null, error = "Couldn't make the backup. Please try again.") }
            null
        } finally {
            pw?.fill(' ')
        }
    }

    /** "Save to a file": [uri] is the file the person just created in Android's picker. */
    fun exportTo(uri: android.net.Uri) = viewModelScope.launch {
        val bytes = buildBackup() ?: return@launch
        runCatching { c.backups.write(uri, bytes) }
            .onSuccess {
                backupUi.update { it.copy(working = null, exported = "Backup saved. Keep it somewhere safe, or send it to your other phone.") }
                say("Backup saved")
            }
            .onFailure { backupUi.update { it.copy(working = null, error = "Couldn't save there. Try another folder.") } }
    }

    // ---------------- sharing a report ----------------

    /** A report made for the share sheet or a file: [file] is ready, or [error] says why not. */
    data class ReportShare(val file: java.io.File, val format: com.owl.tracker.export.ReportFormat)

    val reportShares = MutableSharedFlow<ReportShare>(extraBufferCapacity = 1)
    val reportWorking = MutableStateFlow(false)
    private var reportBytes: ByteArray? = null

    private suspend fun makeReport(period: com.owl.tracker.export.ReportPeriod, format: com.owl.tracker.export.ReportFormat, showAccounts: Boolean): Pair<ByteArray, String>? {
        reportWorking.value = true
        return try {
            val d = c.reports.collect(period, showAccounts)
            val bytes = if (format == com.owl.tracker.export.ReportFormat.PAGE) c.reports.page(d) else c.reports.excel(d)
            bytes to c.reports.fileName(d, format)
        } catch (e: Exception) {
            say("Couldn't make the report. Please try again.")
            null
        } finally {
            reportWorking.value = false
        }
    }

    /** Makes the report and opens the share sheet (WhatsApp, email, Drive…). */
    fun shareReport(period: com.owl.tracker.export.ReportPeriod, format: com.owl.tracker.export.ReportFormat, showAccounts: Boolean) = viewModelScope.launch {
        val (bytes, name) = makeReport(period, format, showAccounts) ?: return@launch
        runCatching { c.reports.writeForSharing(bytes, name) }
            .onSuccess { reportShares.tryEmit(ReportShare(it, format)) }
            .onFailure { say("Couldn't prepare the report for sharing.") }
    }

    /** The file name for "Save to a file", made ready before Android's picker opens. */
    suspend fun prepareReport(period: com.owl.tracker.export.ReportPeriod, format: com.owl.tracker.export.ReportFormat, showAccounts: Boolean): String? {
        val (bytes, name) = makeReport(period, format, showAccounts) ?: return null
        reportBytes = bytes
        return name
    }

    /** [uri]: the file the person just created in Android's picker. */
    fun saveReport(uri: android.net.Uri) = viewModelScope.launch {
        val bytes = reportBytes ?: return@launch
        runCatching { c.reports.write(uri, bytes) }
            .onSuccess { reportBytes = null; say("Report saved") }
            .onFailure { say("Couldn't save there. Try another folder.") }
    }

    /** "Share": makes a copy and opens the share sheet. */
    fun shareBackup() = viewModelScope.launch {
        val bytes = buildBackup() ?: return@launch
        runCatching { c.backups.writeForSharing(bytes) }
            .onSuccess { file ->
                backupUi.update { it.copy(working = null, exported = "Choose where to send your backup.") }
                shareRequests.tryEmit(file)
            }
            .onFailure { backupUi.update { it.copy(working = null, error = "Couldn't prepare the backup for sharing.") } }
    }

    /** A file picked to restore from. A readable backup is checked straight away; an encrypted one waits for its password. */
    fun pickBackup(uri: android.net.Uri) = viewModelScope.launch {
        pickedBytes = null
        backupUi.update { BackupUi(protect = it.protect, password = it.password, confirm = it.confirm, working = "Reading the file\u2026") }
        val name = c.backups.displayName(uri)
        val bytes = try {
            c.backups.read(uri)
        } catch (e: BackupException) {
            backupUi.update { it.copy(working = null, fileName = name, error = backupProblemText(e.problem)) }
            return@launch
        } catch (e: Exception) {
            backupUi.update { it.copy(working = null, fileName = name, error = "Couldn't open that file.") }
            return@launch
        }
        val header = try {
            BackupCodec.peek(bytes)
        } catch (e: BackupException) {
            backupUi.update { it.copy(working = null, fileName = name, error = backupProblemText(e.problem)) }
            return@launch
        }
        pickedBytes = bytes
        backupUi.update { it.copy(working = null, fileName = name, header = header) }
        if (!header.encrypted) unlockBackup()
    }

    /** Opens the picked file with the typed password (if any) and checks every entry. */
    fun unlockBackup() = viewModelScope.launch {
        val bytes = pickedBytes ?: return@launch
        val encrypted = backupUi.value.header?.encrypted == true
        val pw = backupUi.value.restorePassword.toCharArray().takeIf { encrypted && it.isNotEmpty() }
        if (encrypted && pw == null) {
            backupUi.update { it.copy(error = backupProblemText(BackupException.Problem.NEEDS_PASSWORD)) }
            return@launch
        }
        backupUi.update { it.copy(working = if (encrypted) "Unlocking\u2026" else "Checking the backup\u2026", error = null) }
        try {
            val content = withContext(Dispatchers.Default) { BackupCodec.decode(bytes, pw, now()) }
            backupUi.update { it.copy(working = null, content = content, restorePassword = "") }
        } catch (e: BackupException) {
            backupUi.update { it.copy(working = null, error = backupProblemText(e.problem)) }
        } finally {
            pw?.fill(' ')
        }
    }

    fun restoreBackup() = viewModelScope.launch {
        val content = backupUi.value.content ?: return@launch
        if (!editable()) return@launch
        val s = settings.value
        val newPhone = s?.onboarded != true
        // A phone that's new, or only showing the built-in examples, gets the backup instead.
        val replace = backupUi.value.replace || newPhone || s?.sampleData == true
        backupUi.update { it.copy(working = "Restoring\u2026", error = null) }
        val result = try {
            repo.restore(content, replace = replace, finishOnboarding = newPhone)
        } catch (e: Exception) {
            backupUi.update { it.copy(working = null, error = "Couldn't restore. Nothing was changed.") }
            return@launch
        }
        pickedBytes = null
        backupUi.update { it.copy(working = null, content = null, header = null, fileName = null, restored = result) }
        say(
            "Restored ${result.added} ${if (result.added == 1) "payment" else "payments"}" +
                if (result.skipped > 0) " (${result.skipped} already here)" else "",
        )
        if (newPhone) {
            history.clear()
            _screen.value = Screen.HOME
        }
    }

    private fun backupProblemText(p: BackupException.Problem): String = when (p) {
        BackupException.Problem.NOT_A_BACKUP -> "That file isn't an Owl Tracker backup."
        BackupException.Problem.NEWER_VERSION -> "This backup was made by a newer Owl Tracker. Update the app, then try again."
        BackupException.Problem.NEEDS_PASSWORD -> "This backup is password protected. Enter its password."
        BackupException.Problem.WRONG_PASSWORD -> "That password doesn't open this backup."
        BackupException.Problem.DAMAGED -> "This backup is damaged or was changed, so it can't be restored."
        BackupException.Problem.TOO_LARGE -> "That file is too large to be an Owl Tracker backup."
    }

    // ---------------- free trial & unlock ----------------

    /** Trial (days left), unlocked, or trial ended (read-only). */
    val access: StateFlow<Access> = c.licence.access

    /** Google Play's side: price, pending payment, problems. */
    val store: StateFlow<StoreState> = c.licence.store.state

    /** The lifetime unlock is bought (Play said so, now or before). */
    val lifetime: StateFlow<Boolean> = c.licence.lifetime

    fun openUnlock() = go(Screen.UNLOCK)

    /** "Restore purchase": asks Google Play again which purchases this account has. */
    fun restorePurchase() {
        c.licence.refresh()
        say("Checking Google Play for your purchase\u2026")
    }

    /** Debug builds only: end the trial now to see read-only mode and the unlock screen. */
    val canSimulateTrialEnd: Boolean get() = c.licence.trial.isDebuggable
    val debugTrialEnded = MutableStateFlow(c.licence.trial.debugEnded)

    fun setDebugTrialEnded(ended: Boolean) {
        c.licence.setDebugTrialEnded(ended)
        debugTrialEnded.value = ended
    }

    /**
     * The read-only gate. After the trial, until the unlock is bought, anything that adds or changes
     * data sends the person to the unlock screen instead. Viewing, deleting and resetting always work.
     */
    private fun editable(): Boolean {
        if (c.licence.current().canEdit) return true
        _sheet.value = null
        go(Screen.UNLOCK)
        say("Unlock Owl Tracker to add or change anything")
        return false
    }

    // ---------------- startup, lock, sync ----------------

    /** Where the database key lives, worked out once off the main thread (Keystore calls are slow). */
    val keyProtection = MutableStateFlow("Checking\u2026")

    init {
        viewModelScope.launch {
            try {
                repo.open()
            } catch (e: Throwable) {
                _screen.value = Screen.DB_ERROR
                return@launch
            }
            launch(Dispatchers.IO) { keyProtection.value = runCatching { c.keyProtection() }.getOrDefault("Android Keystore") }
            c.licence.refresh()
            launch { runCatching { c.backups.cleanUpShared() } }
            val s = repo.settings()
            if (s.appLock) locked.value = true
            _screen.value = if (s.onboarded) Screen.HOME else Screen.WELCOME
            if (s.onboarded) syncQuietly()
        }
    }

    /**
     * The owl's hello (one of five acts, at random): when the app starts, and when it comes back after
     * [GREET_AFTER_MS] away. Shown once the app lock (if any) has been passed; [greeted] clears it.
     */
    val greeting = MutableStateFlow(if (greetingsOn) LaunchAct.entries.random() else null)
    fun greeted() { greeting.value = null }

    fun onAppBackground() { backgroundAt = now() }

    fun onAppForeground() {
        val s = settings.value ?: return
        val at = backgroundAt
        if (s.appLock && at != null && now() - at > LOCK_AFTER_MS) locked.value = true
        if (greetingsOn && at != null && now() - at > GREET_AFTER_MS) greeting.value = LaunchAct.entries.random()
        backgroundAt = null
        c.licence.refresh()
        if (s.onboarded && !locked.value) syncQuietly()
    }

    fun lockNow() { if (settings.value?.appLock == true) locked.value = true }
    fun unlocked() { locked.value = false }

    fun setAppLock(on: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(appLock = on) }
        say(if (on) "App lock on. Owl Tracker locks a minute after you leave it." else "App lock off")
    }

    fun setScreenPrivacy(on: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(screenPrivacy = on) } }

    fun setTheme(mode: com.owl.tracker.data.ThemeMode) = viewModelScope.launch { repo.updateSettings { it.copy(theme = mode) } }

    fun setStartDay(day: Int) = viewModelScope.launch {
        repo.updateSettings { it.copy(budgetStartDay = day.coerceIn(1, 28)) }
        monthOffset.value = 0
    }

    private fun syncQuietly() = viewModelScope.launch {
        // Reminders older versions counted as spending are removed even when the app is read-only: it's a fix, not an edit
        val gone = runCatching { engine.dropReminders() }.getOrDefault(0)
        // So is sorting payments into new categories, once: stays into Holiday, then into each group's finer categories
        val before = settings.value?.categoriesVersion ?: runCatching { repo.settings().categoriesVersion }.getOrDefault(ImportEngine.CATEGORIES_VERSION)
        val sorted = runCatching { engine.sortIntoNewCategories() }.getOrDefault(0)
        if (sorted > 0) say(
            when {
                before >= 2 -> "$sorted ${if (sorted == 1) "payment" else "payments"} sorted into the new categories: Fashion & Clothing, Daily Commute and more"
                sorted == 1 -> "1 payment moved to the new categories, like Holiday"
                else -> "$sorted payments moved to the new categories, like Holiday"
            },
        )
        // And giving payments that named only a UPI ID their own payee, once (they were all named after the bank)
        val separated = runCatching { engine.separateUpiPayees() }.getOrDefault(0)
        if (separated > 0) say(
            "$separated UPI ${if (separated == 1) "payment" else "payments"} with no shop name had been grouped under the bank's name. " +
                "Each is now its own, so rename them one at a time.",
        )
        if (gone > 0) say(if (gone == 1) "Removed 1 payment reminder that was counted as spending" else "Removed $gone payment reminders that were counted as spending")
        if (c.licence.current().canEdit) runCatching { engine.syncNew() }
        // Payments seen in the app aren't asked about later by a nudge
        runCatching { com.owl.tracker.nudge.PaymentChecks.markSeen(c.context) }
    }

    fun rescan() = viewModelScope.launch {
        if (!editable()) return@launch
        val s = settings.value
        if (s == null || !s.smsEnabled || !c.sms.hasPermission()) { say("Turn on SMS access first"); return@launch }
        val n = runCatching { engine.syncNew() }.getOrDefault(0)
        runCatching { com.owl.tracker.nudge.PaymentChecks.markSeen(c.context) }
        say(if (n == 0) "Scanned your inbox: no new transactions" else "Added $n new ${if (n == 1) "transaction" else "transactions"}")
    }

    fun smsPermissionGranted(): Boolean = c.sms.hasPermission()

    // ---------------- onboarding & import ----------------

    val importUi = MutableStateFlow(ImportUi())

    fun chooseManualMode() = viewModelScope.launch {
        repo.updateSettings { it.copy(smsEnabled = false) }
        val s = repo.settings()
        if (s.onboarded) {
            _screen.value = Screen.SETTINGS
            say("SMS import is off. You can still add expenses yourself.")
        } else {
            // No snackbar here: it would sit over the Save button. The budget screen explains manual mode.
            go(Screen.BUDGET_SETUP)
        }
    }

    fun onSmsPermission(granted: Boolean) {
        if (granted) startImport(if (settings.value?.onboarded == true) ImportMode.SETTINGS else ImportMode.ONBOARDING)
        else {
            chooseManualMode()
        }
    }

    /** "Explore with sample data": the normal import flow, fed with built-in example messages. */
    fun startSampleImport() = startImport(ImportMode.ONBOARDING, sample = true)

    fun startImport(mode: ImportMode, sample: Boolean = false) {
        if (!editable()) return
        val current = settings.value?.filters ?: ImportFilters()
        importUi.value = ImportUi(mode = mode, scanning = true, draft = current, sample = sample)
        go(Screen.IMPORT_FILTERS)
        viewModelScope.launch {
            val scan = runCatching { engine.scan(sample = sample) }.getOrElse { ImportEngine.Scan(emptyList(), emptyList(), emptyList()) }
            importUi.update { ui ->
                // Unrecognised senders start switched off, but when they are all the inbox has, a
                // first-time import would select nothing. Switch them on; they are flagged Unsure.
                val onlyUnrecognised = scan.groups.isNotEmpty() && scan.groups.all { it.id == Institutions.UNRECOGNISED }
                val draft = if (mode == ImportMode.ONBOARDING && onlyUnrecognised) {
                    ui.draft.copy(disabledSenders = ui.draft.disabledSenders - Institutions.UNRECOGNISED)
                } else ui.draft
                ui.copy(scanning = false, scan = scan, draft = draft)
            }
        }
    }

    fun updateDraft(transform: (ImportFilters) -> ImportFilters) = importUi.update { it.copy(draft = transform(it.draft)) }
    fun setWordInput(s: String) = importUi.update { it.copy(wordInput = s) }

    fun addSkipWord() {
        val ui = importUi.value
        val w = ui.wordInput.trim()
        when {
            w.isEmpty() -> say("Type a word or phrase first")
            ui.draft.skipWords.any { it.equals(w, ignoreCase = true) } -> say("“$w” is already on the list")
            else -> importUi.update { it.copy(draft = it.draft.copy(skipWords = it.draft.skipWords + w), wordInput = "") }
        }
    }

    fun removeSkipWord(w: String) = updateDraft { it.copy(skipWords = it.skipWords - w) }

    fun toggleSender(group: String) = updateDraft { f ->
        f.copy(disabledSenders = if (group in f.disabledSenders) f.disabledSenders - group else f.disabledSenders + group)
    }

    fun selectable(): List<ImportEngine.Candidate> {
        val ui = importUi.value
        val scan = ui.scan ?: return emptyList()
        return engine.selectable(scan, ui.draft)
    }

    fun monthOf(cand: ImportEngine.Candidate): YearMonth = engine.monthOf(cand)
    fun messagesFor(group: String): Int = importUi.value.scan?.let { engine.messagesInRange(it, importUi.value.draft, group) } ?: 0
    fun ignoredCount(): Int = importUi.value.scan?.let { engine.ignoredInRange(it, importUi.value.draft) } ?: 0

    fun skipped(): List<ImportEngine.Skipped> = importUi.value.scan?.let { engine.skippedInRange(it, importUi.value.draft) }.orEmpty()

    /** The skipped list as text to share for support, with long digit runs (account, card, phone, reference numbers) hidden. */
    fun skippedReport(): String {
        val items = skipped()
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        return buildString {
            append("Owl Tracker skipped ${items.size} messages\n")
            items.groupBy { it.reason }.toSortedMap(compareBy<com.owl.tracker.core.RejectReason> { SKIP_ORDER.indexOf(it) }).forEach { (reason, list) ->
                append("\n== ${skipLabel(reason)} (${list.size})\n")
                if (reason == com.owl.tracker.core.RejectReason.PERSONAL_SENDER) return@forEach
                list.take(30).forEach { sk ->
                    append("- ").append(sk.sender ?: "?").append(" · ").append(dateOf(sk.timestamp).format(fmt))
                    sk.body?.let { append("\n  ").append(maskDigits(it.replace('\n', ' '))) }
                    append('\n')
                }
                if (list.size > 30) append("  …and ${list.size - 30} more\n")
            }
        }
    }

    fun toReview() {
        if (selectable().isEmpty()) { say("Nothing to import yet. Scroll up to tick a sender, or continue without importing."); return }
        importUi.update { it.copy(chip = ReviewChip.ALL) }
        go(Screen.IMPORT_REVIEW)
    }

    fun setChip(chip: ReviewChip) = importUi.update { it.copy(chip = chip) }

    fun toggleReview(smsId: Long) = importUi.update { ui ->
        ui.copy(reviewOff = if (smsId in ui.reviewOff) ui.reviewOff - smsId else ui.reviewOff + smsId)
    }

    fun toggleMonth(m: YearMonth) = importUi.update { ui ->
        ui.copy(monthsOff = if (m in ui.monthsOff) ui.monthsOff - m else ui.monthsOff + m)
    }

    /** Select or clear every transaction in [smsIds]. */
    fun setReviewed(smsIds: Collection<Long>, selected: Boolean) = importUi.update { ui ->
        ui.copy(reviewOff = if (selected) ui.reviewOff - smsIds.toSet() else ui.reviewOff + smsIds)
    }

    fun chosenForImport(withReview: Boolean): List<ImportEngine.Candidate> {
        val ui = importUi.value
        val all = selectable()
        return if (!withReview) all else all.filter { it.smsId !in ui.reviewOff && engine.monthOf(it) !in ui.monthsOff }
    }

    fun runImport(withReview: Boolean) {
        if (!editable()) return
        val ui = importUi.value
        val all = selectable()
        val chosen = chosenForImport(withReview)
        if (chosen.isEmpty() && ui.mode == ImportMode.ONBOARDING) { say("Tick at least one transaction"); return }
        importUi.update { it.copy(running = true, progress = 0f, result = null) }
        _screen.value = Screen.IMPORTING
        viewModelScope.launch {
            val result = runCatching {
                engine.commit(chosen, ui.draft, all.size - chosen.size, replaceRange = ui.mode == ImportMode.SETTINGS, sample = ui.sample) { p ->
                    importUi.update { it.copy(progress = p) }
                }
            }.getOrElse {
                say("Import stopped: ${it.message ?: "unknown error"}")
                ImportEngine.Result(0, 0, 0, 0, 0, emptyMap())
            }
            importUi.update { it.copy(running = false, progress = 1f, result = result) }
        }
    }

    fun finishImport() {
        val mode = importUi.value.mode
        history.clear()
        if (mode == ImportMode.ONBOARDING) {
            _screen.value = if (budgets.value[Schema.OVERALL] != null && settings.value?.onboarded == true) Screen.HOME else Screen.BUDGET_SETUP
        } else {
            history.addLast(Screen.MORE)
            _screen.value = Screen.SETTINGS
            say("Re-import complete")
        }
    }

    fun saveFiltersOnly() = viewModelScope.launch {
        val f = importUi.value.draft
        repo.updateSettings { it.copy(filters = f) }
        history.clear(); history.addLast(Screen.MORE)
        _screen.value = Screen.SETTINGS
        say("Saved. New messages will follow these filters.")
    }

    fun finishOnboarding(budgetPaise: Long) = viewModelScope.launch {
        repo.setBudget(Schema.OVERALL, budgetPaise)
        repo.updateSettings { it.copy(onboarded = true) }
        history.clear()
        _screen.value = Screen.HOME
    }

    // ---------------- transactions ----------------

    fun setCategory(txId: Long, categoryId: String, remember: Boolean) = viewModelScope.launch {
        if (!editable()) return@launch
        val t = repo.transaction(txId) ?: return@launch
        val n = repo.setCategory(txId, categoryId, remember)
        val name = categories.value.firstOrNull { it.id == categoryId }?.name ?: "category"
        _sheet.value = Sheet.TxnDetail(txId)
        say(if (remember) "Rule saved: ${t.merchantName} → $name" + (if (n > 1) " ($n updated)" else "") else "Moved to $name")
    }

    fun sameMerchantCount(txId: Long): Flow<Int> = repo.observeSameMerchantCount(txId)

    /** For "Was this right?": the payment's own category first, then the three used most in the last three months. */
    suspend fun checkChoices(txId: Long): List<Category> {
        val t = repo.transaction(txId) ?: return emptyList()
        val cats = categories.value.ifEmpty { repo.categories() }
        val byId = cats.associateBy { it.id }
        val recent = repo.transactions(startMs(today().minusDays(90)), startMs(today().plusDays(1)))
        val used = recent.filter { it.direction == com.owl.tracker.data.Direction.DEBIT }.groupingBy { it.categoryId }.eachCount()
            .entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }
            .filter { it.id != t.categoryId && it.countsAsSpend && !it.archived }
        val fallback = com.owl.tracker.data.CategoryTree(cats).spendTops.filter { it.id != t.categoryId }
        return listOfNotNull(byId[t.categoryId]) + (used + fallback).distinctBy { it.id }.take(3)
    }

    /** The answer to "Was this right?": the same category confirms it; another moves it (and, if asked, all from that merchant). */
    fun checkAnswer(txId: Long, categoryId: String, remember: Boolean) = viewModelScope.launch {
        if (!editable()) return@launch
        val t = repo.transaction(txId) ?: return@launch
        val name = categories.value.firstOrNull { it.id == categoryId }?.name ?: "category"
        if (categoryId == t.categoryId && !remember) {
            repo.confirmReview(txId)
            say("Thanks: ${t.merchantName} stays in $name")
        } else {
            val n = repo.setCategory(txId, categoryId, remember)
            say(if (remember) "Done: ${t.merchantName} always goes to $name" + (if (n > 1) " ($n updated)" else "") else "Moved to $name")
        }
        _sheet.value = null
    }

    suspend fun sameMerchant(txId: Long): List<Txn> = repo.sameMerchant(txId)

    /** The payments ticked in "Review and choose", each to its own category; [newOnesTo]: also for new ones. */
    fun moveChosen(txId: Long, moves: Map<Long, String>, newOnesTo: String?) = viewModelScope.launch {
        if (!editable()) return@launch
        val t = repo.transaction(txId) ?: return@launch
        val n = repo.moveChosen(txId, moves, newOnesTo)
        _sheet.value = Sheet.TxnDetail(txId)
        val where = moves.values.distinct().map { categoryName(it) }
        say(
            (if (n == 1) "Moved 1 payment" else "Moved $n payments") + " to " + where.joinToString(" and ") +
                (newOnesTo?.let { " · new ones from ${t.merchantName} too" } ?: ""),
        )
    }

    fun categoryName(id: String): String = categories.value.firstOrNull { it.id == id }?.name ?: "Others"

    // ---------------- splitting a payment ----------------

    /** Splits a payment into [parts] (two or more, adding up to its amount). */
    fun saveSplit(txId: Long, parts: List<com.owl.tracker.data.TxnPart>) = viewModelScope.launch {
        if (!editable()) return@launch
        if (!repo.setParts(txId, parts)) { say("The parts need to add up to the payment"); return@launch }
        _sheet.value = Sheet.TxnDetail(txId)
        say("Split: " + parts.joinToString(" and ") { categoryName(it.categoryId) })
    }

    /** Joins a split payment back into one category (its biggest part's). */
    fun joinSplit(txId: Long) = viewModelScope.launch {
        if (!editable()) return@launch
        repo.setParts(txId, emptyList())
        _sheet.value = Sheet.TxnDetail(txId)
        val t = repo.transaction(txId)
        say("Joined back into " + (t?.let { categoryName(it.categoryId) } ?: "one category"))
    }

    // ---------------- rules ----------------

    val rules: StateFlow<List<com.owl.tracker.data.RuleInfo>> = repo.observeRules().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** What deleting a rule could put back: each covered payment and the category it would usually get. */
    data class RuleDeletePlan(val rule: com.owl.tracker.data.RuleInfo, val moves: Map<Long, String>, val mainCategory: String?)

    suspend fun planRuleDelete(rule: com.owl.tracker.data.RuleInfo): RuleDeletePlan {
        val moves = engine.usualCategories(rule)
        val main = moves.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return RuleDeletePlan(rule, moves, main?.let { categoryName(it) })
    }

    fun deleteRule(plan: RuleDeletePlan, putBack: Boolean) = viewModelScope.launch {
        if (!editable()) return@launch
        val before = repo.deleteRule(plan.rule, if (putBack) plan.moves else emptyMap())
        val label = "${plan.rule.name} → ${categoryName(plan.rule.categoryId)}"
        say(
            if (before.isNotEmpty()) "Rule deleted · ${before.size} ${if (before.size == 1) "payment" else "payments"} put back" else "Deleted the rule $label",
            "Undo",
        ) { viewModelScope.launch { repo.restoreRule(plan.rule, before) } }
    }

    /** Renames a payment, or every payment from its merchant (and new ones from it) when [all]. */
    fun renameTxn(txId: Long, name: String, all: Boolean) = viewModelScope.launch {
        if (!editable()) return@launch
        val clean = name.trim()
        if (clean.isEmpty()) { say("Give it a name"); return@launch }
        val n = repo.renameMerchant(txId, clean, all)
        _sheet.value = Sheet.TxnDetail(txId)
        say(if (all && n > 1) "Renamed $n payments to $clean" else "Renamed to $clean")
    }

    /** Recognised apps installed on this phone, for choosing a merchant's icon. */
    suspend fun pickableApps(): List<com.owl.tracker.KnownApp> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.appIcons.pickableApps() }

    /** A picture (a small square PNG) as the icon for this payment's merchant, now and for future payments. */
    fun setMerchantPicture(txId: Long, png: ByteArray) = viewModelScope.launch {
        if (!editable()) return@launch
        val n = runCatching { repo.setMerchantPicture(txId, png) }.getOrElse { say("That picture couldn't be used"); return@launch }
        _sheet.value = Sheet.TxnDetail(txId)
        say(if (n > 1) "Picture set for $n payments" else "Picture set")
    }

    fun pictureNotOpened() = say("That picture couldn't be opened. Try a PNG, JPEG or screenshot.")

    /** [logo]: null = automatic, "" = no app icon, else a recognised app's package. */
    fun setMerchantIcon(txId: Long, logo: String?) = viewModelScope.launch {
        if (!editable()) return@launch
        repo.setMerchantLogo(txId, logo)
        _sheet.value = Sheet.TxnDetail(txId)
        say(
            when (logo) {
                null -> "Icon set to automatic"
                "" -> "App icon removed"
                else -> "Icon updated"
            }
        )
    }

    fun setNote(txId: Long, note: String) = viewModelScope.launch { if (editable()) repo.setNote(txId, note) }
    suspend fun knownAccounts(): List<com.owl.tracker.data.KnownAccount> = repo.knownAccounts()
    suspend fun withoutAccount(senderGroup: String): Int = repo.countWithoutAccount(senderGroup)

    /**
     * The card or account a payment came from ([label]; null: not sure). With [forBank], also every other payment from
     * the same bank's messages that didn't say which, and new ones.
     */
    fun setAccount(txId: Long, label: String?, forBank: Boolean) = viewModelScope.launch {
        if (!editable()) return@launch
        val tx = repo.transaction(txId) ?: return@launch
        val bank = tx.senderGroup?.takeIf { it != com.owl.tracker.core.Institutions.UNRECOGNISED }
        repo.setAccount(txId, label)
        var others = 0
        if (bank != null) {
            if (label != null && forBank) {
                others = repo.setAccountWhereMissing(bank, label)
                repo.updateSettings { s -> s.copy(accountFor = s.accountFor + (bank to label)) }
            } else if (label == null || repo.settings().accountFor[bank] == tx.accountLabel) {
                repo.updateSettings { s -> s.copy(accountFor = s.accountFor - bank) }
            }
        }
        _sheet.value = Sheet.TxnDetail(txId)
        say(
            when {
                label == null -> "Account cleared"
                others > 0 -> "$label · this and $others more ${com.owl.tracker.core.Institutions.nameFor(bank)} payments"
                else -> "Paid from $label"
            },
        )
    }

    fun setExcluded(txId: Long, excluded: Boolean) = viewModelScope.launch { if (editable()) repo.setExcluded(txId, excluded) }

    fun confirmReview(txId: Long) = viewModelScope.launch {
        if (!editable()) return@launch
        repo.confirmReview(txId)
        say("Confirmed")
    }

    fun deleteTxn(txId: Long) = viewModelScope.launch {
        val t = repo.deleteTransaction(txId) ?: return@launch
        closeSheet()
        say("Transaction deleted", "Undo") { viewModelScope.launch { repo.restoreTransaction(t); say("Transaction restored") } }
    }

    fun addManual(amountPaise: Long, merchant: String, categoryId: String, daysAgo: Int) = viewModelScope.launch {
        if (!editable()) return@launch
        val day = today().minusDays(daysAgo.toLong())
        val at = if (daysAgo == 0) now() else day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        repo.addManual(ImportEngine.manual(amountPaise, merchant, categoryId, at))
        closeSheet()
        val name = categories.value.firstOrNull { it.id == categoryId }?.name ?: ""
        say("Added ${Money.formatInr(amountPaise)} to $name" + when (daysAgo) {
            0 -> ""
            1 -> " for yesterday"
            else -> " on " + day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
        })
    }

    // ---------------- budgets & categories ----------------

    fun saveBudget(key: String, paise: Long?) = viewModelScope.launch {
        if (!editable()) return@launch
        repo.setBudget(key, paise)
        closeSheet()
        val what = if (key == Schema.OVERALL) "Budget" else "${categoryName(key)} budget"
        say(if (paise == null || paise <= 0) "$what removed" else if (key == Schema.OVERALL) "Budget set to ${Money.formatInr(paise)}" else "$what: ${Money.formatInr(paise)} a month")
    }

    /** From the month budget's sheet: every category with its slider. */
    fun openAdjustBudgets() {
        closeSheet()
        go(Screen.ADJUST_BUDGETS)
    }

    /** [parent]: the group to put it in, or null for a category on its own. */
    fun saveCategory(id: String?, name: String, icon: String, color: Long, countsAsSpend: Boolean, parent: String? = null) = viewModelScope.launch {
        if (!editable()) return@launch
        if (name.isBlank()) { say("Give the category a name"); return@launch }
        repo.saveCategory(id, name.trim(), icon, color, countsAsSpend, parent)
        closeSheet()
        say(if (id == null) "${name.trim()} added" else "Category updated")
    }

    /** Kids categories on or off (More › Categories, and the question at setup). */
    fun setKids(on: Boolean) = viewModelScope.launch {
        if (!editable()) return@launch
        val moved = repo.setKids(on)
        say(
            if (on) "Kids categories are on"
            else "Kids categories are off" + if (moved > 0) " · $moved ${if (moved == 1) "payment" else "payments"} moved" else "",
        )
    }

    fun archiveCategory(id: String) = viewModelScope.launch {
        if (!editable()) return@launch
        if (id == com.owl.tracker.core.Cat.OTHERS || id == com.owl.tracker.core.Cat.INCOME) { say("This category can't be archived"); return@launch }
        val name = categories.value.firstOrNull { it.id == id }?.name ?: "Category"
        val moved = repo.archiveCategory(id)
        closeSheet()
        say("$name archived" + if (moved > 0) " · $moved moved to Others" else "")
    }

    // ---------------- delete & reset ----------------

    val deleteUi = MutableStateFlow(DeleteUi())

    fun openDelete() {
        _sheet.value = Sheet.DeleteData
        refreshDeleteCounts()
    }

    private fun cutoff(months: Long) = startMs(today().minusMonths(months))

    private fun refreshDeleteCounts() = viewModelScope.launch {
        val groups = storage.value.bySenderGroup.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val group = deleteUi.value.group?.takeIf { g -> groups.any { it.first == g } } ?: groups.firstOrNull()?.first
        val counts = mapOf(
            DeleteOption.OLDER_3 to repo.countSmsBefore(cutoff(3)),
            DeleteOption.OLDER_6 to repo.countSmsBefore(cutoff(6)),
            DeleteOption.ACCOUNT to (group?.let { repo.countSmsInGroup(it) } ?: 0),
            DeleteOption.ALL_SMS to repo.countAllSms(),
        )
        deleteUi.update { it.copy(counts = counts, groups = groups, group = group) }
    }

    fun setDeleteOption(o: DeleteOption) { deleteUi.update { it.copy(option = o) }; refreshDeleteCounts() }
    fun setDeleteGroup(g: String) { deleteUi.update { it.copy(group = g) }; refreshDeleteCounts() }
    fun setStopImporting(on: Boolean) = deleteUi.update { it.copy(stopImporting = on) }

    fun requestDelete() {
        if (deleteUi.value.selectedCount == 0) { say("Nothing to delete for this choice"); return }
        _confirm.value = ConfirmKind.DELETE
    }

    fun confirmDelete() = viewModelScope.launch {
        val d = deleteUi.value
        val n = when (d.option) {
            DeleteOption.OLDER_3 -> repo.deleteSmsBefore(cutoff(3))
            DeleteOption.OLDER_6 -> repo.deleteSmsBefore(cutoff(6))
            DeleteOption.ACCOUNT -> d.group?.let { g ->
                val removed = repo.deleteSmsInGroup(g)
                if (d.stopImporting) repo.updateSettings { s -> s.copy(filters = s.filters.copy(disabledSenders = s.filters.disabledSenders + g)) }
                removed
            } ?: 0
            DeleteOption.ALL_SMS -> repo.deleteAllSms()
        }
        _confirm.value = null
        closeSheet()
        say("Deleted $n ${if (n == 1) "transaction" else "transactions"}")
    }

    fun requestReset() { _confirm.value = ConfirmKind.RESET }

    /** Erases the database and its key, then starts again at the welcome screen. */
    fun performReset() = viewModelScope.launch {
        _confirm.value = null
        _sheet.value = null
        c.eraseEverything()
        importUi.value = ImportUi()
        deleteUi.value = DeleteUi()
        monthOffset.value = 0
        homeMode.value = HomeMode.MONTH
        locked.value = false
        history.clear()
        _screen.value = Screen.WELCOME
        say("Owl Tracker was reset. Everything it stored on this phone is gone.")
    }

    // ---------------- helpers ----------------

    fun startMs(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun periodAt(startDay: Int, offset: Int): BudgetPeriod {
        var p = BudgetMath.periodFor(today(), startDay)
        repeat(-offset) { p = p.previous(startDay) }
        return p
    }

    fun periodLabel(p: BudgetPeriod): String =
        if (p.start.dayOfMonth == 1) p.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        else p.start.format(SHORT) + " – " + p.endExclusive.minusDays(1).format(SHORT)

    /** Money out that counts as spending. Money in never does (see [Txn.isSpending]). */
    fun counts(t: Txn, cats: Map<String, Category>): Boolean = t.isSpending(cats[t.categoryId])

    private fun buildDashboard(txns: List<Txn>, cats: List<Category>, budgets: Map<String, Long>, p: BudgetPeriod): Dashboard {
        val catMap = cats.associateBy { it.id }
        val today = today()
        val isCurrent = p.contains(today)
        val dayNumber = if (isCurrent) p.dayNumber(today) else p.days
        // A split payment counts as its parts, each in its own category
        val counted = txns.flatMap { it.portions() }.filter { counts(it, catMap) }
        val spent = counted.sumOf { it.amountPaise }
        // By group: a budget, a slice of the ring and a row are a group (or a category on its own)
        val (byCategory, children) = rollUp(counted, cats)
        val byCat = byCategory.associate { it.category.id to it.paise }
        val overall = budgets[Schema.OVERALL] ?: 0L
        val status = if (overall > 0) BudgetMath.status(spent, overall, dayNumber, p.days) else null
        val rows = budgets.filterKeys { it != Schema.OVERALL }.mapNotNull { (id, lim) ->
            val cat = catMap[id]?.takeIf { !it.archived && it.parent == null } ?: return@mapNotNull null
            val sp = byCat[id] ?: 0L
            CategoryBudgetRow(cat, sp, lim, BudgetMath.status(sp, lim, dayNumber, p.days))
        }.sortedByDescending { it.spent.toDouble() / it.limit }
        val unbudgeted = com.owl.tracker.data.CategoryTree(cats).spendTops.filter { budgets[it.id] == null }.map { CategorySpend(it, byCat[it.id] ?: 0L) }
        val daysLeftText = if (isCurrent) "${p.days - dayNumber} days left this month" else "This budget month has ended"
        // Category budgets that are over. The month itself being over is said once, in the budget card's own line.
        val over = buildList {
            rows.filter { it.status.state == BudgetState.OVER }.forEach {
                add(OverItem(
                    "${Money.formatInr(it.spent - it.limit)} over on ${it.category.name}", "${Money.formatInr(it.spent)} spent of ${Money.formatInr(it.limit)} · $daysLeftText",
                    it.category.id, it.category.id, it.category.name, it.spent, it.limit, daysLeftText,
                ))
            }
        }
        return Dashboard(
            period = p, label = periodLabel(p), isCurrent = isCurrent, dayNumber = dayNumber, spent = spent, overall = overall, status = status,
            byCategory = byCategory, recent = txns.take(5), reviewCount = txns.count { it.needsReview }, over = over,
            categoryRows = rows, unbudgeted = unbudgeted, children = children,
        )
    }

    fun senderName(group: String?): String = Institutions.nameFor(group)

    companion object {
        const val LOCK_AFTER_MS = 60_000L

        /** Away this long, the owl says hello again when you come back. */
        const val GREET_AFTER_MS = 30 * 60_000L

        /**
         * The owl's hello is off under tests (Robolectric, or the instrumented tests on emulators), so their first
         * screen is the app itself; a test can switch it back on.
         */
        @Volatile internal var greetingsOn = !runningTests()

        /** Home's "allow nudges" and recap cards are off under tests too (they'd move everything down); a test can turn them on. */
        @Volatile internal var homeOffersOn = !runningTests()

        private fun runningTests(): Boolean =
            android.os.Build.FINGERPRINT == "robolectric" ||
                runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry"); true }.getOrDefault(false)

        /** Most useful first: messages that may be real transactions the parser missed. */
        val SKIP_ORDER = listOf(
            com.owl.tracker.core.RejectReason.NO_AMOUNT, com.owl.tracker.core.RejectReason.NOT_A_TRANSACTION,
            com.owl.tracker.core.RejectReason.FUTURE_OR_REMINDER, com.owl.tracker.core.RejectReason.REQUEST,
            com.owl.tracker.core.RejectReason.FAILED, com.owl.tracker.core.RejectReason.PROMOTIONAL,
            com.owl.tracker.core.RejectReason.GOVERNMENT, com.owl.tracker.core.RejectReason.OTP,
            com.owl.tracker.core.RejectReason.PERSONAL_SENDER,
        )

        fun skipLabel(r: com.owl.tracker.core.RejectReason): String = when (r) {
            com.owl.tracker.core.RejectReason.NO_AMOUNT -> "No amount found"
            com.owl.tracker.core.RejectReason.NOT_A_TRANSACTION -> "No money moved (no debit or credit wording)"
            com.owl.tracker.core.RejectReason.FUTURE_OR_REMINDER -> "Reminders and scheduled payments"
            com.owl.tracker.core.RejectReason.REQUEST -> "Payment requests"
            com.owl.tracker.core.RejectReason.FAILED -> "Failed or declined payments"
            com.owl.tracker.core.RejectReason.PROMOTIONAL -> "Promotions and offers"
            com.owl.tracker.core.RejectReason.GOVERNMENT -> "Government messages"
            com.owl.tracker.core.RejectReason.OTP -> "OTPs and codes (text hidden)"
            com.owl.tracker.core.RejectReason.PERSONAL_SENDER -> "Messages from people (never shown)"
        }

        private val LONG_DIGITS = Regex("""\d{4,}""")

        /** 4821 -> ##21, 629112345678 -> ##########78. Amounts like 1,240.00 keep their digits. */
        fun maskDigits(s: String): String = LONG_DIGITS.replace(s) { m -> "#".repeat(m.value.length - 2) + m.value.takeLast(2) }
        private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
}
