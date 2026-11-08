package com.owl.tracker.ui

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

enum class Screen(val tab: Tab? = null) {
    WELCOME, DISCLOSURE, IMPORT_FILTERS, IMPORT_REVIEW, IMPORTING, BUDGET_SETUP,
    HOME(Tab.HOME), ACTIVITY(Tab.ACTIVITY), BUDGETS(Tab.BUDGETS), MORE(Tab.MORE),
    CATEGORIES, SETTINGS, INSIGHTS, PRIVACY, DB_ERROR, UNLOCK, BACKUP,
}

sealed interface Sheet {
    data class TxnDetail(val id: Long) : Sheet
    data class CategoryPick(val txId: Long) : Sheet
    data class Rename(val txId: Long) : Sheet
    data class MerchantIcon(val txId: Long) : Sheet
    data class BudgetEdit(val key: String) : Sheet
    data class CategoryEdit(val id: String?) : Sheet
    data object AddExpense : Sheet
    data object DeleteData : Sheet
    data object Skipped : Sheet
}

enum class ConfirmKind { DELETE, RESET }
enum class ImportMode { ONBOARDING, SETTINGS }
enum class ReviewChip { ALL, OUT, IN, UNSURE }
enum class DeleteOption { OLDER_3, OLDER_6, ACCOUNT, ALL_SMS }
enum class ActivityFilter { ALL, SPENT, RECEIVED, REVIEW }

class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class CategorySpend(val category: Category, val paise: Long)
data class CategoryBudgetRow(val category: Category, val spent: Long, val limit: Long, val status: BudgetStatus)
data class OverItem(val title: String, val subtitle: String, val categoryId: String?, val budgetKey: String)

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
)


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

    fun txnFlow(id: Long): Flow<Txn?> = repo.observeTransaction(id)

    // Activity tab
    val query = MutableStateFlow("")
    val activityFilter = MutableStateFlow(ActivityFilter.ALL)
    val categoryFilter = MutableStateFlow<String?>(null)

    val activity: StateFlow<List<Txn>> = combine(periodTxns, query, activityFilter, categoryFilter, categories) { txns, q, f, cf, cats ->
        val names = cats.associate { it.id to it.name.lowercase() }
        val needle = q.trim().lowercase()
        txns.filter { t ->
            (f != ActivityFilter.SPENT || t.direction == Direction.DEBIT) &&
                (f != ActivityFilter.RECEIVED || t.direction == Direction.CREDIT) &&
                (f != ActivityFilter.REVIEW || t.needsReview) &&
                (cf == null || t.categoryId == cf) &&
                (needle.isEmpty() || t.merchantName.lowercase().contains(needle) || (names[t.categoryId] ?: "").contains(needle) ||
                    (t.note ?: "").lowercase().contains(needle))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun showActivity(category: String? = null, filter: ActivityFilter = ActivityFilter.ALL) {
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

    fun setReportRange(r: ReportRange) {
        reportRange.value = r
        reportFilter.update { it.copy(bucket = null) }
    }

    /** Tapping a bar, slice, merchant or weekday picks it; tapping it again lets go. */
    fun tapReportBucket(i: Int) = reportFilter.update { it.copy(bucket = if (it.bucket == i) null else i) }
    fun tapReportCategory(id: String) = reportFilter.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    fun tapReportMerchant(key: String) = reportFilter.update { it.copy(merchantKey = if (it.merchantKey == key) null else key) }
    fun tapReportWeekday(d: java.time.DayOfWeek) = reportFilter.update { it.copy(weekday = if (it.weekday == d) null else d) }

    fun clearReportFilter(kind: FilterKind? = null) = reportFilter.update {
        when (kind) {
            null -> ReportFilter()
            FilterKind.BUCKET -> it.copy(bucket = null)
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

    fun onAppBackground() { backgroundAt = now() }

    fun onAppForeground() {
        val s = settings.value ?: return
        val at = backgroundAt
        if (s.appLock && at != null && now() - at > LOCK_AFTER_MS) locked.value = true
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

    fun setStartDay(day: Int) = viewModelScope.launch {
        repo.updateSettings { it.copy(budgetStartDay = day.coerceIn(1, 28)) }
        monthOffset.value = 0
    }

    private fun syncQuietly() = viewModelScope.launch {
        if (c.licence.current().canEdit) runCatching { engine.syncNew() }
    }

    fun rescan() = viewModelScope.launch {
        if (!editable()) return@launch
        val s = settings.value
        if (s == null || !s.smsEnabled || !c.sms.hasPermission()) { say("Turn on SMS access first"); return@launch }
        val n = runCatching { engine.syncNew() }.getOrDefault(0)
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
        say("Added ${Money.formatInr(amountPaise)} to $name")
    }

    // ---------------- budgets & categories ----------------

    fun saveBudget(key: String, paise: Long?) = viewModelScope.launch {
        if (!editable()) return@launch
        repo.setBudget(key, paise)
        closeSheet()
        say(if (paise == null || paise <= 0) "Budget removed" else "Budget set to ${Money.formatInr(paise)}")
    }

    fun saveCategory(id: String?, name: String, icon: String, color: Long, countsAsSpend: Boolean) = viewModelScope.launch {
        if (!editable()) return@launch
        if (name.isBlank()) { say("Give the category a name"); return@launch }
        repo.saveCategory(id, name.trim(), icon, color, countsAsSpend)
        closeSheet()
        say(if (id == null) "${name.trim()} added" else "Category updated")
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

    fun counts(t: Txn, cats: Map<String, Category>): Boolean = !t.excluded && (cats[t.categoryId]?.countsAsSpend ?: true)

    private fun buildDashboard(txns: List<Txn>, cats: List<Category>, budgets: Map<String, Long>, p: BudgetPeriod): Dashboard {
        val catMap = cats.associateBy { it.id }
        val today = today()
        val isCurrent = p.contains(today)
        val dayNumber = if (isCurrent) p.dayNumber(today) else p.days
        val counted = txns.filter { counts(it, catMap) }
        val spent = counted.sumOf { it.signedPaise }
        val byCat = counted.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.signedPaise } }
        val overall = budgets[Schema.OVERALL] ?: 0L
        val status = if (overall > 0) BudgetMath.status(spent, overall, dayNumber, p.days) else null
        val byCategory = byCat.filter { it.value > 0 }.mapNotNull { (id, v) -> catMap[id]?.let { CategorySpend(it, v) } }.sortedByDescending { it.paise }
        val rows = budgets.filterKeys { it != Schema.OVERALL }.mapNotNull { (id, lim) ->
            val cat = catMap[id]?.takeIf { !it.archived } ?: return@mapNotNull null
            val sp = byCat[id] ?: 0L
            CategoryBudgetRow(cat, sp, lim, BudgetMath.status(sp, lim, dayNumber, p.days))
        }.sortedByDescending { it.spent.toDouble() / it.limit }
        val unbudgeted = cats.filter { it.countsAsSpend && !it.archived && budgets[it.id] == null }.map { CategorySpend(it, byCat[it.id] ?: 0L) }
        val daysLeftText = if (isCurrent) "${p.days - dayNumber} days left this month" else "This budget month has ended"
        val over = buildList {
            if (status?.state == BudgetState.OVER) add(OverItem("${Money.formatInr(spent - overall)} over your monthly budget", daysLeftText, null, Schema.OVERALL))
            rows.filter { it.status.state == BudgetState.OVER }.forEach {
                add(OverItem("${Money.formatInr(it.spent - it.limit)} over on ${it.category.name}", "${Money.formatInr(it.spent)} spent of ${Money.formatInr(it.limit)} · $daysLeftText", it.category.id, it.category.id))
            }
        }
        return Dashboard(
            period = p, label = periodLabel(p), isCurrent = isCurrent, dayNumber = dayNumber, spent = spent, overall = overall, status = status,
            byCategory = byCategory, recent = txns.take(5), reviewCount = txns.count { it.needsReview }, over = over,
            categoryRows = rows, unbudgeted = unbudgeted,
        )
    }

    fun senderName(group: String?): String = Institutions.nameFor(group)

    companion object {
        const val LOCK_AFTER_MS = 60_000L

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
