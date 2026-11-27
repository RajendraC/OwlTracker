package com.owl.tracker.nudge

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.R
import com.owl.tracker.core.Money
import com.owl.tracker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The big widget: the budget ring, what went out this month, what's left, and today. */
class BudgetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = OwlWidgets.refreshAsync(context, goAsync())
}

/** The small widget: today against its share, and a button to add cash. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = OwlWidgets.refreshAsync(context, goAsync())
}

/**
 * Draws both widgets from a [MonthSnapshot]. They are refreshed after each SMS sync, every evening, and when the
 * app goes to the background. With app lock on, they show no amounts: only a prompt to open the app.
 */
object OwlWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refreshAsync(context: Context, pending: android.content.BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        scope.launch {
            try { refresh(app) } catch (_: Exception) { } finally { pending?.finish() }
        }
    }

    private fun ids(context: Context, cls: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls)) }.getOrDefault(IntArray(0))

    /** True when at least one widget is on the home screen (nothing to do otherwise). */
    fun any(context: Context) = ids(context, BudgetWidget::class.java).isNotEmpty() || ids(context, TodayWidget::class.java).isNotEmpty()

    suspend fun refresh(context: Context) {
        if (!any(context)) return
        val app = context.applicationContext as? OwlTrackerApp ?: return
        val settings = app.container.repo.settings()
        val snapshot = if (settings.onboarded) MonthSnapshot.load(app.container.repo, app.container.clock) else null
        val manager = AppWidgetManager.getInstance(context)
        val big = ids(context, BudgetWidget::class.java)
        if (big.isNotEmpty()) manager.updateAppWidget(big, budgetViews(context, snapshot))
        val small = ids(context, TodayWidget::class.java)
        if (small.isNotEmpty()) manager.updateAppWidget(small, todayViews(context, snapshot))
    }

    private fun open(context: Context, what: String, code: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(Nudges.EXTRA_OPEN, what)
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun budgetViews(context: Context, s: MonthSnapshot?): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_budget)
        v.setOnClickPendingIntent(R.id.widget_root, open(context, Nudges.OPEN_HOME, 7101))
        val dp = context.resources.displayMetrics.density
        when {
            s == null -> {
                v.setImageViewBitmap(R.id.widget_ring, ring(0.0, (96 * dp).toInt(), null))
                v.setTextViewText(R.id.widget_label, "Owl Tracker")
                v.setTextViewText(R.id.widget_spent, "Set up")
                v.setTextViewText(R.id.widget_left, "Open the app to start")
                v.setTextViewText(R.id.widget_today, "")
            }
            s.appLock -> {
                v.setImageViewBitmap(R.id.widget_ring, ring(0.0, (96 * dp).toInt(), null))
                v.setTextViewText(R.id.widget_label, "Owl Tracker is locked")
                v.setTextViewText(R.id.widget_spent, "🔒")
                v.setTextViewText(R.id.widget_left, "Tap to open")
                v.setTextViewText(R.id.widget_today, "Amounts stay hidden while app lock is on")
            }
            else -> {
                v.setImageViewBitmap(R.id.widget_ring, ring(if (s.overall > 0) s.used else 0.0, (96 * dp).toInt(), s.period.dayNumber(s.today).toFloat() / s.period.days))
                v.setTextViewText(R.id.widget_label, "Spent this month")
                v.setTextViewText(R.id.widget_spent, Money.formatInr(s.spent))
                val over = s.overall > 0 && s.left < 0
                v.setTextViewText(R.id.widget_left, when {
                    s.overall <= 0 -> "No budget yet"
                    over -> "Over by ${Money.formatInr(-s.left)}"
                    else -> "${Money.formatInr(s.left)} left"
                })
                v.setInt(R.id.widget_left, "setBackgroundResource", if (over) R.drawable.widget_chip_over else R.drawable.widget_chip_ok)
                v.setTextColor(R.id.widget_left, if (over) 0xFFB3261E.toInt() else 0xFF2E7D32.toInt())
                v.setTextViewText(R.id.widget_today, if (s.dayShare > 0) "Today ${Money.formatInr(s.todaySpent)} of ${Money.formatInr(s.dayShare)}" else "Today ${Money.formatInr(s.todaySpent)}")
            }
        }
        return v
    }

    fun todayViews(context: Context, s: MonthSnapshot?): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_today)
        v.setOnClickPendingIntent(R.id.widget_root, open(context, Nudges.OPEN_HOME, 7102))
        v.setOnClickPendingIntent(R.id.widget_add, open(context, Nudges.OPEN_ADD, 7103))
        val dp = context.resources.displayMetrics.density
        val w = (140 * dp).toInt(); val h = (10 * dp).toInt()
        when {
            s == null || s.appLock -> {
                v.setTextViewText(R.id.widget_label, if (s == null) "Owl Tracker" else "Locked")
                v.setTextViewText(R.id.widget_spent, if (s == null) "Set up" else "🔒")
                v.setImageViewBitmap(R.id.widget_bar, bar(0f, w, h))
                v.setTextViewText(R.id.widget_today, "Tap to open")
            }
            else -> {
                v.setTextViewText(R.id.widget_label, "Today")
                v.setTextViewText(R.id.widget_spent, Money.formatInr(s.todaySpent))
                val f = if (s.dayShare > 0) s.todaySpent.toFloat() / s.dayShare else 0f
                v.setImageViewBitmap(R.id.widget_bar, bar(f, w, h))
                v.setTextViewText(R.id.widget_today, if (s.dayShare > 0) "of ${Money.formatInr(s.dayShare)} share" else "no budget yet")
            }
        }
        return v
    }

    private val GREEN = 0xFF4CB86B.toInt()
    private val MANGO = 0xFFFFB21A.toInt()
    private val RED = 0xFFD63B41.toInt()
    private val RED_LIGHT = 0xFFFF6B6B.toInt()
    private val TRACK = 0xFFEADBC8.toInt()
    private val INK = 0xFF3A1C0C.toInt()

    /** The same ring as Home: once round for the budget, an inner red lap past it, a tick for an even pace. */
    fun ring(used: Double, size: Int, pace: Float?): Bitmap {
        val b = Bitmap.createBitmap(size.coerceAtLeast(1), size.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val ow = size * 0.12f
        val r = size / 2f - ow / 2f - size * 0.02f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = ow }
        val box = RectF(size / 2f - r, size / 2f - r, size / 2f + r, size / 2f + r)
        p.color = TRACK; c.drawArc(box, 0f, 360f, false, p)
        if (used > 0) { p.color = if (used < 0.8) GREEN else MANGO; c.drawArc(box, -90f, (used.coerceAtMost(1.0) * 360).toFloat().coerceAtMost(359.5f), false, p) }
        if (used > 1) {
            val iw = size * 0.085f
            val ir = r - ow / 2f - iw / 2f - size * 0.025f
            val ib = RectF(size / 2f - ir, size / 2f - ir, size / 2f + ir, size / 2f + ir)
            p.strokeWidth = iw
            val past = used - 1
            p.color = TRACK; c.drawArc(ib, 0f, 360f, false, p)
            if (past > 1) { p.color = RED; c.drawArc(ib, 0f, 360f, false, p) }
            p.color = RED_LIGHT; c.drawArc(ib, -90f, (((if (past > 1) past - 1 else past).coerceAtMost(1.0)) * 360).toFloat().coerceAtMost(359.5f), false, p)
        }
        if (pace != null && used in 0.0..1.0) {
            val a = Math.toRadians((pace * 360 - 90).toDouble())
            val r0 = r - ow / 2f - 2; val r1 = r + ow / 2f + 2
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; strokeWidth = size * 0.022f; strokeCap = Paint.Cap.ROUND }
            c.drawLine(size / 2f + r0 * Math.cos(a).toFloat(), size / 2f + r0 * Math.sin(a).toFloat(), size / 2f + r1 * Math.cos(a).toFloat(), size / 2f + r1 * Math.sin(a).toFloat(), t)
        }
        // The middle: % used, or how many times the budget
        val text = when { used <= 0 -> "–"; used > 1 -> String.format(java.util.Locale.ROOT, "%.1f×", used); else -> "${(used * 100).toInt()}%" }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = size * 0.22f; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD) }
        c.drawText(text, size / 2f, size / 2f + tp.textSize * 0.35f, tp)
        return b
    }

    /** Today's bar: green up to half the share, mango up to all of it, red past it. */
    fun bar(f: Float, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = TRACK; c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, p)
        if (f > 0) {
            p.color = when { f <= 0.5f -> GREEN; f <= 1f -> MANGO; else -> RED }
            c.drawRoundRect(RectF(0f, 0f, w * f.coerceAtMost(1f), h.toFloat()), h / 2f, h / 2f, p)
        }
        return b
    }
}
