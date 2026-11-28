package com.owl.tracker.ui.screens

import com.owl.tracker.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owl.tracker.core.Money
import com.owl.tracker.ui.Platform
import com.owl.tracker.ui.Recap
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.components.MoodOwl
import com.owl.tracker.ui.components.OwlMood
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Cream = Color(0xFFFFE3B0)

/** One card of the story: its tag (for tests), colours and what it says. */
private class RecapCard(val tag: String, val top: Color, val bottom: Color, val content: @Composable () -> Unit)

/**
 * The month's recap: a story of cards (tap the right side for the next, the left for the one before). Everything in
 * it was worked out on the phone. "Share my month" makes a separate picture of the wins, without amounts.
 */
@Composable
fun RecapStory(r: Recap, platform: Platform, onClose: () -> Unit, onShare: () -> Unit = {}) {
    val cards = remember(r) { recapCards(r) }
    var at by remember(r) { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val card = cards[at]
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(card.top, card.bottom))).testTag("recap")) {
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(card.top, card.bottom)))
                    .statusBarsPadding().padding(horizontal = 26.dp).padding(top = 34.dp, bottom = 130.dp)
                    .pointerInput(cards.size) {
                        detectTapGestures { o -> at = if (o.x < size.width / 3f) (at - 1).coerceAtLeast(0) else if (at < cards.size - 1) at + 1 else at }
                    }
                    .testTag("recap_${card.tag}"),
            ) {
                Text(tr("Your {0} · {1} of {2}", r.month, at + 1, cards.size), color = Cream, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.weight(1f)) { card.content() }
            }
            // The progress dashes and close button, above the card
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                cards.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= at) Color.White else Color.White.copy(alpha = 0.3f)))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp).testTag("recap_close")) {
                    Icon(Ic.close, contentDescription = tr("Close the recap"), tint = Color.White)
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Shares the month's wins as one picture, without amounts (the cards themselves stay on the phone)
                Text(
                    tr("Share my month"), color = Color(0xFF3A1C0C), style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color.White)
                        .clickable(onClick = onShare)
                        .padding(vertical = 14.dp).testTag("recap_share"),
                )
                Text(
                    if (at < cards.size - 1) tr("Next ›") else tr("Done"), color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                        .clickable { if (at < cards.size - 1) at++ else onClose() }.padding(horizontal = 22.dp, vertical = 14.dp).testTag("recap_next"),
                )
            }
        }
    }
}

@Composable
private fun Big(text: String, size: Int = 46) {
    Text(text, color = Color.White, style = MaterialTheme.typography.displaySmall.copy(fontSize = size.sp, lineHeight = (size * 1.08).sp))
}

@Composable
private fun Line(text: String) {
    Text(text, color = Cream, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp))
}

@Composable
private fun Tile(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.14f)).padding(14.dp)) {
        Text(label, color = Cream, style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
        sub?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun RecapRing(used: Double, centre: String, sub: String) {
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 22.dp.toPx(); val r = size.minDimension / 2 - w
            val tl = Offset(center.x - r, center.y - r); val s = Size(2 * r, 2 * r)
            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, tl, s, style = Stroke(w))
            drawArc(if (used <= 1) Color(0xFF7BD88F) else Color(0xFFFFB21A), -90f, (used.coerceAtMost(1.0) * 360).toFloat().coerceAtMost(359.5f), false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
            if (used > 1) {
                val iw = 15.dp.toPx(); val ir = r - w / 2 - iw / 2 - 5.dp.toPx()
                drawArc(Color(0xFFFF6B6B), -90f, ((used - 1).coerceAtMost(1.0) * 360).toFloat().coerceAtMost(359.5f), false, Offset(center.x - ir, center.y - ir), Size(2 * ir, 2 * ir), style = Stroke(iw, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centre, color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(sub, color = Cream, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", com.owl.tracker.i18n.I18n.locale)

private fun recapCards(r: Recap): List<RecapCard> = buildList {
    val cocoa = Color(0xFF3A1C0C); val rust = Color(0xFF8A3E12); val mango = Color(0xFFE08A12)
    add(RecapCard("intro", cocoa, rust) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Big(tr("Your {0}, in a few cards", r.month))
            Line(tr("{0} payments, {1} in all. Here's what stood out.", r.payments, Money.formatInr(r.spent)))
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) { MoodOwl(OwlMood.HAPPY, 170.dp) }
        }
    })
    if (r.budget > 0) {
        val used = r.spent.toDouble() / r.budget
        val under = r.spent <= r.budget
        add(RecapCard("budget", if (under) Color(0xFF1F4A2E) else cocoa, if (under) Color(0xFF3E8A55) else Color(0xFF9A2A22)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Big(if (under) tr("You saved\n{0}", Money.formatInr(r.budget - r.spent)) else tr("You went over by\n{0}", Money.formatInr(r.spent - r.budget)))
                Line(tr("The budget was {0} and you spent {1}.", Money.formatInr(r.budget), Money.formatInr(r.spent)))
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    RecapRing(used, if (used > 1) String.format(Locale.ROOT, "%.1f×", used) else "${(used * 100).toInt()}%", if (used > 1) tr("the budget") else tr("of budget"))
                }
            }
        })
    }
    if (r.biggestDay != null && r.biggestDayPaise > 0) add(RecapCard("biggest_day", rust, mango) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("Biggest day"), color = Cream, style = MaterialTheme.typography.titleMedium)
            Big(r.biggestDay.format(dayFmt))
            Big(Money.formatInr(r.biggestDayPaise), 38)
            r.biggestDayMerchant?.let { Line(tr("Mostly {0}.", it)) }
        }
    })
    if (r.topMerchant != null) add(RecapCard("top_merchant", Color(0xFF5A2A6E), Color(0xFFB0507A)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("Your top merchant"), color = Cream, style = MaterialTheme.typography.titleMedium)
            Big(r.topMerchant)
            Line("${r.topMerchantCount} ${if (r.topMerchantCount == 1) "payment" else "payments"}, ${Money.formatInr(r.topMerchantPaise)} in all.")
        }
    })
    r.topCategory?.let { c ->
        add(RecapCard("top_category", Color(0xFF1F3F5A), Color(0xFF3A7AA0)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("Where most of it went"), color = Cream, style = MaterialTheme.typography.titleMedium)
                com.owl.tracker.ui.components.categoryArt(c.icon)?.let { Image(painterResource(it), null, Modifier.size(120.dp)) }
                Big(c.name)
                val pct = if (r.spent > 0) (r.topCategoryPaise * 100 / r.spent).toInt() else 0
                Line("${Money.formatInr(r.topCategoryPaise)}, $pct% of the month.")
            }
        })
    }
    if (r.days > 0 && (r.noSpendDays > 0 || r.daysWithinShare > 0)) add(RecapCard("good_days", Color(0xFF1F4A2E), Color(0xFF6BA84F)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("The good days"), color = Cream, style = MaterialTheme.typography.titleMedium)
            if (r.daysWithinShare > 0) Big(tr("{0} of {1} days\nwithin your share", r.daysWithinShare, r.days))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(tr("No-spend days"), "${r.noSpendDays}", if (r.noSpendDays > 0) tr("nothing went out") else tr("try one this month"), Modifier.weight(1f))
                if (r.invested > 0) Tile(tr("Invested"), Money.formatInr(r.invested), tr("still yours"), Modifier.weight(1f))
            }
        }
    })
    add(RecapCard("outro", cocoa, mango) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val diff = r.spent - r.previousSpent
            Big(when {
                r.previousSpent <= 0 -> tr("That's {0}", r.month)
                diff < 0 -> tr("{0} less than {1}", Money.formatInr(-diff), r.previousMonth)
                diff > 0 -> tr("{0} more than {1}", Money.formatInr(diff), r.previousMonth)
                else -> tr("Just like {0}", r.previousMonth)
            }, 40)
            Line(if (diff <= 0 || r.previousSpent <= 0) tr("Nicely done. The owl will keep an eye on the new month.") else tr("A fresh month starts now. The owl will help you keep to it."))
            Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) { MoodOwl(if (diff <= 0) OwlMood.CHEERING else OwlMood.HAPPY, 160.dp) }
        }
    })
}
