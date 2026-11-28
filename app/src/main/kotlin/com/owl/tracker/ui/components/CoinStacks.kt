package com.owl.tracker.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.owl.tracker.core.Money
import com.owl.tracker.i18n.tr
import com.owl.tracker.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The owl's reactions setting ("lively", "calm" or "off"), provided at the root. */
val LocalOwlReacts = compositionLocalOf { "lively" }

/** At most this many coins in the tallest stack (or budget line). */
private const val MAX_COINS = 22
private const val MORE = "+more"
private val UNITS = longArrayOf(100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 1_00_000, 2_00_000, 5_00_000, 10_00_000).map { it * 100 }
private val OVER = Color(0xFFD9412C)

/** The value of one coin: a round amount that keeps the tallest stack within [MAX_COINS]. */
fun coinUnit(maxPaise: Long): Long = UNITS.firstOrNull { (maxPaise + it / 2) / it <= MAX_COINS } ?: UNITS.last()

/** How many coins [paise] makes (at least one for any spending). */
fun coinsFor(paise: Long, unit: Long): Int = if (paise <= 0) 0 else max(1, ((paise + unit / 2) / unit).toInt())

private class CoinStack(val id: String, val name: String, val color: Color, val paise: Long, val icon: String?, val budget: Long?) {
    val over get() = budget != null && paise > budget
}

/** What the owl says on top of a stack: null without a budget. */
private fun stackWords(s: CoinStack): String? {
    val b = s.budget ?: return null
    return when {
        s.paise > b -> tr("Whoa, over!")
        s.paise >= b * 0.9 -> tr("Just enough")
        else -> tr("Nice!")
    }
}

/**
 * Home's "Where it went" from 1.9.38 (the user's pick on 28 Nov 2026, "L3 · Coin stacks"), in place of the orbit.
 * Each of the six biggest categories is a stack of coins, one coin a round amount; the rest share a grey "+N" stack.
 * A dashed line marks each category's budget; coins past it are red, a tag above says by how much, and the stack
 * wobbles. When the card comes into view the coins drop in and the owl visits each stack once, biggest first, then
 * settles on the one most over (or the biggest). A tap sends the owl to a stack ([picked]); a second tap opens it.
 * Same arguments as the orbit it replaced. Still with "Remove animations", under tests, and when the owl's reactions
 * are Calm (face only) or Off.
 */
@Composable
fun CoinStacksStage(
    items: List<OrbitItem>,
    say: OwlSay,
    picked: String?,
    actKey: Any?,
    sweepKey: Any?,
    onTap: (RingHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val reacts = LocalOwlReacts.current
    val noMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f || !com.owl.tracker.ui.AppViewModel.greetingsOn
    }
    val still = noMotion || reacts != "lively"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val shown = items.take(6)
    val rest = items.drop(6)
    val stacks = shown.map { CoinStack(it.id, it.name, it.color, it.paise, it.icon, it.use?.takeIf { u -> u > 0f }?.let { u -> (it.paise / u).toLong() }) } +
        if (rest.isNotEmpty()) listOf(CoinStack(MORE, "+${rest.size}", Color(0xFFB9AFA4), rest.sumOf { it.paise }, null, null)) else emptyList()
    val unit = coinUnit(stacks.maxOfOrNull { max(it.paise, it.budget ?: 0) } ?: 1)
    val counts = stacks.map { coinsFor(it.paise, unit) }
    val lines = stacks.map { s -> s.budget?.let { b -> coinsFor(b, unit) } }
    val overIdx = stacks.indices.filter { stacks[it].over }.maxByOrNull { stacks[it].paise - (stacks[it].budget ?: 0) }
    val overCount = shown.indices.count { stacks[it].over }
    val budgeted = shown.indices.count { stacks[it].budget != null }

    // The coins drop in when the card first comes into view
    var seen by remember(sweepKey) { mutableStateOf(false) }
    val drop = remember(sweepKey) { Animatable(if (still) 1f else 0f) }
    val totalCoins = counts.sum()
    LaunchedEffect(seen, sweepKey) { if (seen && !still) drop.animateTo(1f, tween(900 + totalCoins * 14, easing = LinearEasing)) }
    // Then the owl visits each stack once, biggest first
    var tourAt by remember(sweepKey) { mutableStateOf<Int?>(null) }
    LaunchedEffect(seen, sweepKey, stacks.size) {
        if (!seen || still) return@LaunchedEffect
        delay(900L + totalCoins * 14)
        for (i in shown.indices.sortedByDescending { stacks[it].paise }) { tourAt = i; delay(1300) }
        tourAt = null
    }
    val pickedIdx = stacks.indexOfFirst { it.id == picked }.takeIf { it >= 0 }
    val restIdx = overIdx ?: 0
    val owlAt = (pickedIdx ?: tourAt ?: restIdx).coerceAtMost(stacks.lastIndex.coerceAtLeast(0))
    val owlStack = stacks.getOrNull(owlAt)
    val mood = when {
        pickedIdx != null -> say.mood
        owlStack != null && (tourAt != null || overIdx != null) -> OwlMood.of(owlStack.paise, owlStack.budget)
        else -> say.mood
    }
    val words = owlStack?.takeIf { it.id != MORE }?.let(::stackWords)

    val wob = if (still) 0f else rememberInfiniteTransition(label = "wobble")
        .animateFloat(-1f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wobble").value

    BoxWithConstraints(
        modifier.fillMaxWidth().onGloballyPositioned {
            if (!seen) { val b = it.boundsInWindow(); if (b.height >= it.size.height * 0.35f) seen = true }
        },
    ) {
        val density = LocalDensity.current
        val maxW = maxWidth
        val k = min(1f, maxW.value / 300f)
        val n = stacks.size.coerceAtLeast(1)
        // Tall enough for the tallest stack (or budget line), the owl on it and a tag above the owl; no more
        val tallest = stacks.indices.maxOfOrNull { max(counts[it], lines[it] ?: 0) } ?: 0
        val stageH = ((50f + tallest * 5f + 8f + 56f + 34f) * k).coerceIn(170f * k, 250f * k).dp
        val slot = maxW / n
        val stackW = min(38f, slot.value * 0.74f).dp
        val pitch = 5.dp * k
        val coinH = 8.dp * k
        val belowH = 50.dp * k
        val owlSize = 56.dp * k
        val px = { d: androidx.compose.ui.unit.Dp -> with(density) { d.toPx() } }
        val baseY = px(stageH) - px(belowH)
        fun cx(i: Int) = px(slot) * (i + 0.5f)
        fun topY(i: Int) = baseY - counts[i] * px(pitch) - px(coinH)

        // The owl hops from stack to stack along an arc
        val target = if (stacks.isEmpty()) Offset.Zero else Offset(cx(owlAt) - px(owlSize) / 2f, topY(owlAt) - px(owlSize) + px(6.dp))
        var from by remember { mutableStateOf<Offset?>(null) }
        val hop = remember { Animatable(1f) }
        var shownAt by remember { mutableStateOf(target) }
        LaunchedEffect(target) {
            if (still || from == null) { from = target; shownAt = target; return@LaunchedEffect }
            val start = shownAt
            hop.snapTo(0f)
            hop.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) {
                val t = value
                shownAt = Offset(start.x + (target.x - start.x) * t, start.y + (target.y - start.y) * t - sin(PI * t).toFloat() * px(34.dp))
            }
            from = target
        }
        val owlPos = if (still) target else shownAt

        Column {
            Box(
                Modifier.fillMaxWidth().height(stageH)
                    .semantics {
                        contentDescription = tr("Spending by category, in coin stacks: ") +
                            shown.joinToString { "${it.name} ${Money.formatInr(it.paise)}" }
                    }
                    .testTag("orbit"),
            ) {
                Canvas(Modifier.fillMaxWidth().height(stageH).testTag("budget_track")) {
                    val baseColor = if (dark) Color(0xFF4A3D33) else Color(0xFFE8D8C3)
                    drawRoundRect(baseColor, Offset(0f, baseY), Size(size.width, 6.dp.toPx() * k), CornerRadius(3.dp.toPx()))
                    stacks.forEachIndexed { i, s ->
                        val w = stackW.toPx()
                        val x0 = cx(i) - w / 2f
                        // Each stack starts a little after the one before it
                        val p = (drop.value * (1f + 0.1f * n) - 0.1f * i).coerceIn(0f, 1f)
                        val showing = counts[i] * p
                        val whole = showing.toInt()
                        val falling = showing - whole
                        val tilt = if (s.over) wob * 3f else 0f
                        rotate(tilt, Offset(cx(i), baseY)) {
                            val last = min(counts[i] - 1, if (falling > 0f) whole else whole - 1)
                            for (j in 0..last) {
                                val fallOff = if (j == whole) (1f - falling) * 40.dp.toPx() else 0f
                                val y = baseY - j * pitch.toPx() - coinH.toPx() - fallOff
                                val col = if (lines[i] != null && j >= lines[i]!!) OVER else s.color
                                drawOval(lerp(col, Color.Black, 0.28f), Offset(x0, y + 1.6.dp.toPx() * k), Size(w, coinH.toPx()))
                                drawOval(col, Offset(x0, y), Size(w, coinH.toPx()))
                                drawOval(Color.White.copy(alpha = 0.22f), Offset(x0 + w * 0.18f, y + coinH.toPx() * 0.18f), Size(w * 0.5f, coinH.toPx() * 0.3f))
                            }
                        }
                        lines[i]?.let { b ->
                            val y = baseY - b * pitch.toPx() - 1.dp.toPx()
                            drawLine(
                                if (dark) Color(0xFFBFAE9C) else Color(0xFF9B8571), Offset(x0 - 5.dp.toPx(), y), Offset(x0 + w + 5.dp.toPx(), y),
                                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                    }
                }

                // Under each stack: its picture and amount; the whole column takes the tap
                stacks.forEachIndexed { i, s ->
                    val isPicked = i == pickedIdx
                    Box(
                        Modifier.offset { IntOffset((px(slot) * i).roundToInt(), 0) }.width(slot).fillMaxHeight()
                            .then(if (s.id == MORE) Modifier else Modifier.clickable(onClickLabel = if (isPicked) tr("Open {0}", s.name) else tr("See how {0} is doing", s.name), role = Role.Button) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTap(RingHit.Slice(s.id))
                            })
                            .semantics {
                                contentDescription = if (s.id == MORE) tr("{0} more categories", rest.size) else
                                    s.name + ", " + Money.formatInr(s.paise) + when {
                                        s.budget == null -> ""
                                        s.over -> ", " + tr("{0} over its budget", Money.formatInr(s.paise - s.budget))
                                        else -> ", " + tr("{0}% of its budget", (s.paise * 100 / s.budget.coerceAtLeast(1)).toInt())
                                    }
                            }
                            .testTag("orbit_${s.id}"),
                    ) {
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            val ring = if (isPicked) Modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) else Modifier
                            Box(ring.padding(2.dp), contentAlignment = Alignment.Center) {
                                if (s.id == MORE) Text(s.name, fontSize = 13.sp * k, fontWeight = FontWeight.ExtraBold, color = muted)
                                else {
                                    val art = categoryArt(s.icon)
                                    if (art != null) Image(painterResource(art), null, Modifier.size(24.dp * k))
                                    else androidx.compose.material3.Icon(com.owl.tracker.ui.theme.Ic.forCategory(s.icon ?: "others"), null, tint = s.color, modifier = Modifier.size(22.dp * k))
                                }
                            }
                            Text(
                                Money.formatShort(s.paise), fontSize = 10.sp * k.coerceAtLeast(0.9f), fontWeight = FontWeight.Bold, maxLines = 1,
                                color = if (s.over) OVER else muted,
                            )
                        }
                    }
                }

                // Tags over the stacks that went over: on top of the owl when it's sitting there
                stacks.forEachIndexed { i, s ->
                    if (!s.over || drop.value < 0.98f) return@forEachIndexed
                    val lift = if (i == owlAt) px(owlSize) - px(4.dp) else 0f
                    val y = topY(i) - px(24.dp) - lift
                    Box(
                        Modifier.offset { IntOffset((px(slot) * i).roundToInt(), y.roundToInt()) }.width(slot).wrapContentWidth(unbounded = true).zIndex(60f),
                    ) {
                        Text(
                            tr("{0} over", Money.formatShort(s.paise - (s.budget ?: 0))),
                            Modifier.clip(RoundedCornerShape(50)).background(if (dark) Color(0xFF5A2620) else Color(0xFFFDE3DE)).padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("stack_over_${s.id}"),
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (dark) Color(0xFFFFB4A8) else Color(0xFFB23A23), maxLines = 1,
                        )
                    }
                }

                if (stacks.isNotEmpty()) {
                    // The owl, and what it says about the stack it's on
                    Box(Modifier.offset { IntOffset(owlPos.x.roundToInt(), owlPos.y.roundToInt()) }.zIndex(80f)) {
                        MoodOwl(mood, owlSize, dark = dark, playKey = Triple(actKey, owlAt, tourAt), still = reacts != "lively" || noMotion)
                        Box(
                            Modifier.align(Alignment.Center).size(owlSize * 0.6f)
                                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = tr("Back to the month")) { onTap(RingHit.Middle) }
                                .testTag("orbit_owl"),
                        )
                    }
                    if (words != null && drop.value >= 0.98f && reacts != "off") {
                        val s = owlStack!!
                        val (fg, bg) = when {
                            s.over -> (if (dark) Color(0xFFFFB4A8) else Color(0xFFB23A23)) to (if (dark) Color(0xFF5A2620) else Color(0xFFFDE3DE))
                            s.paise >= (s.budget ?: 0) * 0.9 -> (if (dark) Color(0xFFFFD98A) else Color(0xFF7A5300)) to (if (dark) Color(0xFF4A3A14) else Color(0xFFFFF0C9))
                            else -> (if (dark) Color(0xFF9FE0B4) else Color(0xFF1E7A43)) to (if (dark) Color(0xFF1E3A28) else Color(0xFFDDF3E3))
                        }
                        val bx = (owlPos.x + px(owlSize) * 0.86f).coerceAtMost(px(maxW) - px(84.dp)).coerceAtLeast(0f)
                        Text(
                            words,
                            Modifier.offset { IntOffset(bx.roundToInt(), (owlPos.y + px(2.dp)).roundToInt()) }.zIndex(90f)
                                .clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp).testTag("owl_words"),
                            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
                        )
                    }
                }
            }
            Text(
                when {
                    budgeted == 0 -> tr("Taller stack, bigger spend")
                    overCount > 0 -> tr("{0} of {1} over budget", overCount, shown.size)
                    else -> tr("All {0} within budget", shown.size)
                },
                Modifier.fillMaxWidth().testTag("orbit_note"),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                color = if (overCount > 0) Color(0xFFD32F2F) else if (budgeted == 0) muted else Color(0xFF2E7D32),
            )
            Text(
                tr("Each coin is {0} · dashed line is the budget", Money.formatInr(unit)),
                Modifier.fillMaxWidth().padding(top = 2.dp).testTag("coin_value"),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = muted,
            )
        }
    }
}
