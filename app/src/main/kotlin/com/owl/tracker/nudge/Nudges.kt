package com.owl.tracker.nudge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.R
import com.owl.tracker.core.Money
import com.owl.tracker.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The owl's nudges: worked out on the phone and shown as notifications; nothing is sent anywhere. Four kinds, each
 * with its own switch (More → Owl nudges) and Android channel: the evening line about today, budget alerts (80% and
 * 100% of the month, a category going over), Sunday's week, and the month's recap on its first days. Each is shown at
 * most once (per day, week or month); amounts are hidden on the lock screen.
 */
object Nudges {
    const val PREFS = "nudges"
    const val DAILY = "daily"
    const val ALERTS = "alerts"
    const val WEEKLY = "weekly"
    const val RECAP = "recap"
    const val CHECK = "check"
    val KINDS = listOf(DAILY, ALERTS, WEEKLY, RECAP, CHECK)

    /** Opened from a notification: what to show (EXTRA_OPEN values). */
    const val EXTRA_OPEN = "owl_open"
    const val OPEN_HOME = "home"
    const val OPEN_BUDGETS = "budgets"
    const val OPEN_RECAP = "recap"
    const val OPEN_ADD = "add"
    const val OPEN_ACTIVITY = "activity"
    const val ACTION_CHECK_YES = "com.owl.tracker.CHECK_YES"
    const val EXTRA_TX = "tx"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isOn(context: Context, kind: String) = prefs(context).getBoolean("on_$kind", true)
    fun setOn(context: Context, kind: String, on: Boolean) = prefs(context).edit().putBoolean("on_$kind", on).apply()

    /** Android 13+ asks before an app may notify. */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(DAILY, "Evening: today's spending", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(ALERTS, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(WEEKLY, "Sunday: your week", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(RECAP, "Month recap", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHECK, "New payments: was the category right?", NotificationManager.IMPORTANCE_LOW),
        ).forEach { nm.createNotificationChannel(it) }
    }

    /** One nudge: its kind, a key so it's only shown once, and the words. */
    data class Nudge(val kind: String, val key: String, val title: String, val text: String, val open: String)

    /**
     * What the owl would say now, given the month: at most one evening line (after 7 pm), one week line on Sunday
     * evenings, budget alerts at any time, and the recap in the first three days of a new month.
     */
    fun due(s: MonthSnapshot, now: LocalDateTime, previous: MonthSnapshot?): List<Nudge> = buildList {
        val ps = s.period.start
        if (s.overall > 0) {
            val over = s.spent - s.overall
            when {
                over > 0 -> add(Nudge(ALERTS, "100_$ps", "You've gone past ${month(s)}'s budget",
                    "${Money.formatInr(over)} over, with ${days(s.daysLeft)} left." + (s.biggestCategory?.let { " $it is the biggest." } ?: ""), OPEN_HOME))
                s.used >= 0.8 -> add(Nudge(ALERTS, "80_$ps", "${(s.used * 100).toInt()}% of ${month(s)}'s budget used",
                    "${Money.formatInr(s.left)} left for ${days(s.daysLeft + 1)}, about ${Money.formatInr(s.perDayLeft)} a day." + (s.biggestCategory?.let { " $it is the biggest." } ?: ""), OPEN_HOME))
            }
        }
        s.overCategories.forEach { (name, sp, lim) ->
            add(Nudge(ALERTS, "cat_${name}_$ps", "$name went over its budget", "${Money.formatInr(sp)} of ${Money.formatInr(lim)}. Tap to see where it went.", OPEN_BUDGETS))
        }
        if (now.hour >= 19) {
            if (now.dayOfWeek == DayOfWeek.SUNDAY) {
                val diff = s.weekSpent - s.lastWeekSpent
                val compare = when {
                    s.lastWeekSpent <= 0 -> ""
                    diff < 0 -> "${Money.formatInr(-diff)} less than last week."
                    diff > 0 -> "${Money.formatInr(diff)} more than last week."
                    else -> "The same as last week."
                }
                add(Nudge(WEEKLY, "week_${now.toLocalDate()}", "Your week: ${Money.formatInr(s.weekSpent)}", "$compare Tap for the details.".trim(), OPEN_HOME))
            } else {
                val text = when {
                    s.dayShare <= 0 -> "Set a monthly budget and the owl will say how each day went."
                    s.todaySpent == 0L -> "Nothing went out today. A no-spend day!"
                    s.todaySpent <= s.dayShare -> "Within today's ${Money.formatInr(s.dayShare)} share." + if (s.overall > 0 && s.left > 0) " ${Money.formatInr(s.left)} left for ${days(s.daysLeft)}." else ""
                    else -> "${Money.formatInr(s.todaySpent - s.dayShare)} over today's ${Money.formatInr(s.dayShare)} share."
                }
                val mark = when { s.dayShare <= 0 -> ""; s.todaySpent <= s.dayShare -> " 👍"; else -> "" }
                add(Nudge(DAILY, "day_${now.toLocalDate()}", "Today: ${Money.formatInr(s.todaySpent)}$mark", text, OPEN_HOME))
            }
        }
        if (previous != null && s.dayNumber <= 3 && previous.spent > 0) {
            val p = previous
            val line = when {
                p.overall > 0 && p.spent <= p.overall -> "You came in ${Money.formatInr(p.overall - p.spent)} under budget. See the highlights."
                p.overall > 0 -> "Spent ${Money.formatInr(p.spent)}. See the highlights and what to watch."
                else -> "Spent ${Money.formatInr(p.spent)}. See your biggest day, top merchant and more."
            }
            add(Nudge(RECAP, "recap_${p.period.start}", "Your ${month(p)} recap is ready", line, OPEN_RECAP))
        }
    }

    private fun month(s: MonthSnapshot): String = monthName(s.period)

    /** A month that starts on a later day (salary day) is named after the month most of it falls in. */
    fun monthName(p: com.owl.tracker.core.BudgetPeriod): String =
        p.start.plusDays((p.days / 2).toLong()).month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

    /** Works out what's due and shows what hasn't been shown yet, if allowed and switched on. */
    suspend fun run(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val app = context.applicationContext as? OwlTrackerApp ?: return
        val settings = app.container.repo.settings()
        if (!settings.onboarded) return
        val s = MonthSnapshot.load(app.container.repo, app.container.clock, now.toLocalDate())
        val previous = if (s.dayNumber <= 3) MonthSnapshot.load(app.container.repo, app.container.clock, s.period.start.minusDays(1)) else null
        val p = prefs(context)
        val todo = due(s, now, previous).filter { isOn(context, it.kind) && !p.getBoolean("shown_${it.key}", false) }
        if (todo.isEmpty() || !allowed(context)) return
        channels(context)
        val nm = NotificationManagerCompat.from(context)
        todo.forEach { n ->
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_OPEN, n.open)
            val pi = PendingIntent.getActivity(context, n.key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // On the lock screen: only that there's a nudge, never the amounts
            val public = NotificationCompat.Builder(context, n.kind).setSmallIcon(R.drawable.ic_notify_owl)
                .setContentTitle("Owl Tracker").setContentText(publicLine(n.kind)).build()
            val notification = NotificationCompat.Builder(context, n.kind)
                .setSmallIcon(R.drawable.ic_notify_owl)
                .setColor(0xFFFFB21A.toInt())
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .build()
            // allowed() was checked above; the permission can still be taken away in between
            try { nm.notify(n.kind.hashCode() + if (n.kind == ALERTS) n.key.hashCode() else 0, notification) } catch (_: SecurityException) { return }
            p.edit().putBoolean("shown_${n.key}", true).apply()
        }
        prune(p, now.toLocalDate())
    }

    private fun publicLine(kind: String) = when (kind) {
        DAILY -> "Today's summary"
        ALERTS -> "A budget alert"
        WEEKLY -> "Your week"
        else -> "Your month's recap is ready"
    }

    /** Forget "shown" marks older than three months, so the file stays small. */
    private fun prune(p: SharedPreferences, today: LocalDate) {
        val cutoff = today.minusMonths(3)
        val old = p.all.keys.filter { k ->
            k.startsWith("shown_") && Regex("""\d{4}-\d{2}-\d{2}""").find(k)?.value?.let { runCatching { LocalDate.parse(it).isBefore(cutoff) }.getOrNull() } == true
        }
        if (old.isNotEmpty()) p.edit().apply { old.forEach { remove(it) } }.apply()
    }
}

/**
 * "Was this right?": after an SMS sync, each new payment (up to three; more become one summary) as a nudge with
 * ✓ Yes (keeps the category, marks it checked) and Change (opens a quick picker). The first run after updating
 * only notes where it's up to, so nothing old is asked about.
 */
object PaymentChecks {
    private const val LAST = "check_last_id"

    suspend fun run(context: android.content.Context) {
        val app = context.applicationContext as? OwlTrackerApp ?: return
        val repo = app.container.repo
        val p = Nudges.prefs(context)
        val maxId = repo.maxTransactionId()
        if (!p.contains(LAST)) { p.edit().putLong(LAST, maxId).apply(); return }
        val last = p.getLong(LAST, maxId)
        if (maxId <= last) return
        p.edit().putLong(LAST, maxId).apply()
        if (!Nudges.isOn(context, Nudges.CHECK) || !Nudges.allowed(context)) return
        val cats = repo.categories().associateBy { it.id }
        val fresh = repo.transactionsAfter(last).filter { it.source == com.owl.tracker.data.Source.SMS && it.isSpending(cats[it.categoryId]) }
        if (fresh.isEmpty()) return
        Nudges.channels(context)
        val nm = NotificationManagerCompat.from(context)
        fun open(what: String, code: Int) = PendingIntent.getActivity(
            context, code, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(Nudges.EXTRA_OPEN, what),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, Nudges.CHECK).setSmallIcon(R.drawable.ic_notify_owl).setContentTitle("Owl Tracker").setContentText("A new payment to check").build()
        val posts = if (fresh.size <= 3) fresh.map { t ->
            val cat = cats[t.categoryId]?.name ?: "Not sorted yet"
            val time = java.time.Instant.ofEpochMilli(t.occurredAt).atZone(app.container.clock.zone).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            val yes = PendingIntent.getBroadcast(context, t.id.toInt(), Intent(context, CheckReceiver::class.java).setAction(Nudges.ACTION_CHECK_YES).putExtra(Nudges.EXTRA_TX, t.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            (CHECK_BASE + (t.id % 100_000).toInt()) to NotificationCompat.Builder(context, Nudges.CHECK)
                .setSmallIcon(R.drawable.ic_notify_owl).setColor(0xFFFFB21A.toInt())
                .setContentTitle("${t.merchantName} · ${Money.formatInr(t.amountPaise)} → $cat?")
                .setContentText((t.accountLabel?.let { "$it · " } ?: "") + "$time. Was that right?")
                .setContentIntent(open("check:${t.id}", t.id.toInt()))
                .addAction(0, "✓ Yes, $cat", yes)
                .addAction(0, "Change", open("check:${t.id}", t.id.toInt() + 1))
                .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).setGroup("checks")
                .build()
        } else listOf(
            CHECK_BASE to NotificationCompat.Builder(context, Nudges.CHECK)
                .setSmallIcon(R.drawable.ic_notify_owl).setColor(0xFFFFB21A.toInt())
                .setContentTitle("${fresh.size} new payments")
                .setContentText("${Money.formatInr(fresh.sumOf { it.amountPaise })} in all. Tap to check their categories.")
                .setContentIntent(open(Nudges.OPEN_ACTIVITY, 7201))
                .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
                .build()
        )
        posts.forEach { (id, n) -> try { nm.notify(id, n) } catch (_: SecurityException) { return } }
    }

    /** Everything stored so far has been seen (the app was open): only later payments get a nudge. */
    suspend fun markSeen(context: android.content.Context) {
        val app = context.applicationContext as? OwlTrackerApp ?: return
        Nudges.prefs(context).edit().putLong(LAST, app.container.repo.maxTransactionId()).apply()
    }

    const val CHECK_BASE = 40_000
}

/** ✓ Yes on a new payment's nudge: keeps its category and marks it checked, without opening the app. */
class CheckReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        if (intent.action != Nudges.ACTION_CHECK_YES) return
        val id = intent.getLongExtra(Nudges.EXTRA_TX, -1).takeIf { it > 0 } ?: return
        val pending = goAsync()
        val app = context.applicationContext as? OwlTrackerApp ?: run { pending.finish(); return }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                if (app.container.licence.current().canEdit) app.container.repo.confirmReview(id)
                NotificationManagerCompat.from(context).cancel(PaymentChecks.CHECK_BASE + (id % 100_000).toInt())
            } finally { pending.finish() }
        }
    }
}

/** Every evening around 9 pm: the owl's nudges, and the home-screen widgets brought up to date. */
class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Nudges.run(applicationContext) }
        runCatching { OwlWidgets.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "owl-nudges"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(21, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(24, TimeUnit.HOURS, 2, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
