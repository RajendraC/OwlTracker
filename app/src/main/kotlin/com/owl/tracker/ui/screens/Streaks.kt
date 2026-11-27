package com.owl.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.DayMark
import com.owl.tracker.core.Money
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Badge
import com.owl.tracker.ui.badgeEmoji
import com.owl.tracker.ui.Streak
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.theme.ClaySurface
import com.owl.tracker.ui.theme.Ic
import java.time.format.TextStyle
import java.util.Locale

private val Green = Color(0xFF4CB86B)
private val Red = Color(0xFFD63B41)
private val Purple = Color(0xFF7B5CD6)
private val Gold = Color(0xFFFFB21A)


/** The last seven days as dots: green within the share, red over, a star for no-spend days, today still open. */
@Composable
private fun WeekDots(st: Streak) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        st.info.lastSeven.forEach { (d, mark) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val (bg, text) = when (mark) {
                    DayMark.WITHIN -> Green to "✓"
                    DayMark.OVER -> Red to "✕"
                    DayMark.NO_SPEND -> Purple to "★"
                    DayMark.TODAY -> (if (st.info.todayWithin) Gold.copy(alpha = 0.45f) else Red.copy(alpha = 0.35f)) to "·"
                    DayMark.BEFORE -> MaterialTheme.colorScheme.surfaceVariant to ""
                }
                Box(Modifier.size(32.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dayWord(n: Int) = if (n == 1) "day" else "days"

/** Home: days in a row within the share, the last seven days, and how many badges. Opens the Streaks page. */
@Composable
fun StreakCard(st: Streak, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ClaySurface(RoundedCornerShape(30.dp), modifier.fillMaxWidth(), elevation = 12.dp) {
        Column(Modifier.clickable(onClickLabel = "See your streaks and badges", onClick = onOpen).testTag("streak_card").padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (st.info.days > 0) "🔥" else "🌱", fontSize = 34.sp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (st.info.days > 0) "${st.info.days} ${dayWord(st.info.days)} in a row" else "Start a streak today",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("streak_days"),
                    )
                    Text("within your ${Money.formatInr(st.info.share)} daily share", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val earned = st.badges.count { it.earned }
                Text("🏅 $earned of ${st.badges.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 5.dp))
                Icon(Ic.chevR, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            WeekDots(st)
        }
    }
}

/** Home, the day after a no-spend day: a small celebration, put away with ×. */
@Composable
fun NoSpendCard(countThisMonth: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFF1EAFB), Color(0xFFFFF4DA))))
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp).testTag("no_spend_card"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🌟", fontSize = 30.sp)
        Column(Modifier.weight(1f)) {
            Text("Yesterday was a no-spend day!", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4A2F9A))
            Text(
                "Nothing went out. That's ${yourNth(countThisMonth)} this month" + if (countThisMonth < 5) "; 5 earns the Zero day badge." else ".",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B4A7A),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.testTag("no_spend_close")) { Icon(Ic.close, contentDescription = "Put away") }
    }
}

private fun yourNth(n: Int) = when {
    n % 100 in 11..13 -> "your ${n}th"
    n % 10 == 1 -> if (n == 1) "your first" else "your ${n}st"
    n % 10 == 2 -> "your ${n}nd"
    n % 10 == 3 -> "your ${n}rd"
    else -> "your ${n}th"
}

/** The Streaks page: the streak, weeks, no-spend days, and every badge (earned or how to earn it). */
@Composable
fun StreaksScreen(vm: AppViewModel) {
    val st by vm.streaks.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("streaks_screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("Your streaks", onBack = { vm.back() })
        val s = st
        if (s == null) {
            Text("Set a monthly budget and the owl will count the days you stay within your daily share.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        ClaySurface(RoundedCornerShape(30.dp), Modifier.fillMaxWidth(), elevation = 12.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (s.info.days > 0) "🔥" else "🌱", fontSize = 52.sp)
                    Column {
                        Text("${s.info.days} ${dayWord(s.info.days)}", style = MaterialTheme.typography.displaySmall)
                        Text("in a row within your ${Money.formatInr(s.info.share)} daily share", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                WeekDots(s)
                Text(
                    if (s.info.todayWithin) "Keep today under ${Money.formatInr(s.info.share)} to make it ${s.info.days + 1}." else "Today's gone past the share; a new streak starts tomorrow.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Weeks within budget", "${s.info.weeks} in a row", "best ${s.info.bestWeeks}", Modifier.weight(1f))
            Stat("Longest streak", "${s.info.bestDays} ${dayWord(s.info.bestDays)}", null, Modifier.weight(1f))
        }
        Stat("No-spend days this month", "🌟 ${s.info.noSpendThisMonth}", "Days when nothing went out", Modifier.fillMaxWidth())
        Text("Owl badges · ${s.badges.count { it.earned }} of ${s.badges.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        if (s.badges.any { it.earned }) Text("Tap a badge you've earned to share it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.badges.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b -> BadgeTile(b, Modifier.weight(1f), onShare = if (b.earned) ({ vm.shareBadge(b) }) else null) }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    ClaySurface(RoundedCornerShape(24.dp), modifier, elevation = 8.dp) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BadgeTile(b: Badge, modifier: Modifier = Modifier, onShare: (() -> Unit)? = null) {
    val tap = if (onShare != null) Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = "Share this badge", onClick = onShare) else Modifier
    Column(modifier.then(tap).testTag("badge_${b.id}").alpha(if (b.earned) 1f else 0.5f).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(if (b.earned) Brush.radialGradient(listOf(Color(0xFFFFE08A), Gold, Color(0xFFC9700A))) else Brush.radialGradient(listOf(Color(0xFFEFE3D3), Color(0xFFE0CDB6)))),
            contentAlignment = Alignment.Center,
        ) { Text(badgeEmoji(b.id), fontSize = 34.sp) }
        Text(b.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text(if (b.earned) b.earnedText else b.howTo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
