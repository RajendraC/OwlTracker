package com.owl.tracker.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.compose.ui.zIndex
import com.owl.tracker.core.Money
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A category circling the owl: [use] is how much of its own budget is spent (1 = all of it), null without one. */
data class OrbitItem(val id: String, val name: String, val color: Color, val paise: Long, val icon: String?, val use: Float?)

/** The orbit's slow drift, in radians a second (a lap in about 18 s). */
private const val DRIFT = 0.35f
private const val MORE = "+more"

/**
 * Home's "Where it went", chosen 23 Nov 2026 ("Option C · Orbit") in place of the 3D ring: the six biggest categories
 * are bubbles with their 3D pictures circling the owl on its stand. A bubble's size is what went there; a thin ring
 * round it is how much of its own budget is used, red and gently glowing when it's over. Bubbles passing behind the
 * owl are smaller and paler. A swipe spins them; a tap brings one round to the front ([picked], the card beside it
 * explains it) and a second tap opens it; the owl goes back to the month. More than six: a "+N" bubble.
 * They fly out from the owl when the card first comes into view; with "Remove animations" they sit still.
 */
@Composable
fun OrbitOwlStage(
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
    // Still with "Remove animations", and under tests (their taps and pictures need the bubbles in place)
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f || !com.owl.tracker.ui.AppViewModel.greetingsOn
    }
    val shown = items.take(6)
    val more = items.size - shown.size
    val bubbles = shown + if (more > 0) listOf(OrbitItem(MORE, "+$more", Color(0xFF8C8F94), 0, null, null)) else emptyList()
    val total = shown.sumOf { it.paise }.coerceAtLeast(1)
    val n = bubbles.size.coerceAtLeast(1)

    var seen by remember(sweepKey) { mutableStateOf(false) }
    val appear = remember(sweepKey) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(seen, sweepKey) { if (seen) appear.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }

    // The drift runs as an infinite animation (still under tests and with Remove animations); swipes and picks turn [spin]
    var drift by remember { mutableFloatStateOf(0f) }
    val spin = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val pickedNow by rememberUpdatedState(picked)
    if (!still) LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (last != 0L && pickedNow == null && !dragging) drift += (now - last) / 1e9f * DRIFT
                last = now
            }
        }
    }
    // A picked bubble comes round to the front (the bottom of the ellipse, nearest you)
    LaunchedEffect(picked, bubbles.size) {
        val i = bubbles.indexOfFirst { it.id == picked }
        if (i >= 0) {
            val base = i * 2f * PI.toFloat() / n
            val now = base + drift + spin.value
            val target = PI.toFloat() / 2f
            var d = (target - now) % (2f * PI.toFloat())
            if (d > PI) d -= 2f * PI.toFloat()
            if (d < -PI) d += 2f * PI.toFloat()
            spin.animateTo(spin.value + d, tween(800, easing = FastOutSlowInEasing))
        }
    }
    val pulse = if (still) 0.5f else rememberInfiniteTransition(label = "glow")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow").value
    val scope = rememberCoroutineScope()
    val owlLift by animateFloatAsState(if (picked != null) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "lift")
    val overCount = shown.count { (it.use ?: 0f) > 1f }
    val budgeted = shown.count { it.use != null }

    BoxWithConstraints(
        modifier.fillMaxWidth().onGloballyPositioned {
            if (!seen) { val b = it.boundsInWindow(); if (b.height >= it.size.height * 0.35f) seen = true }
        },
    ) {
        val density = LocalDensity.current
        val k = min(1f, maxWidth.value / 300f)
        val stageH = 230.dp * k
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { stageH.toPx() }
        val cx = w / 2f
        val cy = h * 0.48f
        val rx = w * 0.36f   // the biggest bubbles stay inside the stage, clear of the card beside it
        val ry = h * 0.21f
        val pedY = cy + ry * 0.55f
        val owlSize = 92.dp * k
        val owlPx = with(density) { owlSize.toPx() }
        val trackColor = if (dark) Color(0xFF4A3D33) else Color(0xFFE6D4BF)
        val e = appear.value

        Column {
            Box(
                Modifier.fillMaxWidth().height(stageH)
                    .pointerInput(n) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true; tracker.resetTracking() },
                            onDragEnd = {
                                dragging = false
                                val v = tracker.calculateVelocity().x / rx
                                scope.launch { spin.animateDecay(v, exponentialDecay(frictionMultiplier = 1.6f)) }
                            },
                            onDragCancel = { dragging = false },
                        ) { change, dx ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { spin.snapTo(spin.value + dx / rx) }
                        }
                    }
                    .semantics {
                        contentDescription = "Spending by category, circling the owl: " +
                            shown.joinToString { "${it.name} ${it.paise * 100 / total}%" }
                    }
                    .testTag("orbit"),
            ) {
                // The path the bubbles follow, and the owl's stand
                Canvas(Modifier.fillMaxWidth().height(stageH)) {
                    drawOval(
                        trackColor, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7.dp.toPx()))),
                        alpha = e,
                    )
                    val pw = owlPx * 1.05f
                    drawOval(if (dark) Color(0xFF2E251F) else Color(0xFFE9D7C2), Offset(cx - pw / 2, pedY - pw * 0.13f + 4.dp.toPx()), Size(pw, pw * 0.26f))
                    drawOval(if (dark) Color(0xFF3A3029) else Color(0xFFF4E6D4), Offset(cx - pw / 2, pedY - pw * 0.13f), Size(pw, pw * 0.26f))
                }
                // The owl on its stand: tap it to go back to the month (or to replay its act)
                // Only the owl's body takes the tap, so a bubble peeping out from behind it can still be tapped
                Box(
                    Modifier
                        .offset { IntOffset((cx - owlPx / 2f).roundToInt(), (pedY - owlPx * 0.9f - owlLift * 8.dp.toPx()).roundToInt()) }
                        .zIndex(50f),
                ) {
                    MoodOwl(say.mood, owlSize, dark = dark, playKey = actKey to say)
                    Box(
                        Modifier.align(Alignment.Center).size(owlSize * 0.5f)
                            .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Back to the month") { onTap(RingHit.Middle) }
                            .testTag("orbit_owl"),
                    )
                }

                bubbles.forEachIndexed { i, b ->
                    val a = i * 2f * PI.toFloat() / n + drift + spin.value
                    val depth = (sin(a) + 1f) / 2f
                    val x = cx + rx * cos(a) * e
                    val y = cy + ry * sin(a) * e
                    val share = if (b.id == MORE) 0.02f else b.paise.toFloat() / total
                    val isPicked = b.id == picked
                    val grow by animateFloatAsState(if (isPicked) 1.3f else 1f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "grow")
                    val sizeDp = (30f + sqrt(share) * 62f) * k * (0.72f + 0.38f * depth) * (0.3f + 0.7f * e) * grow
                    val sPx = with(density) { sizeDp.dp.toPx() }
                    val over = (b.use ?: 0f) > 1f
                    val dim = if (picked != null && !isPicked) 0.45f else 1f
                    Box(
                        Modifier
                            .offset { IntOffset((x - sPx / 2f).roundToInt(), (y - sPx / 2f).roundToInt()) }
                            .zIndex(if (isPicked) 200f else if (depth > 0.5f) 100f + depth else depth * 40f)
                            .size(sizeDp.dp)
                            .graphicsLayer { alpha = ((0.55f + 0.45f * depth) * dim * e.coerceIn(0f, 1f)).coerceIn(0f, 1f) }
                            .then(if (b.id == MORE) Modifier else Modifier.clickable(onClickLabel = if (isPicked) "Open ${b.name}" else "See how ${b.name} is doing", role = Role.Button) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTap(RingHit.Slice(b.id))
                            })
                            .semantics {
                                contentDescription = if (b.id == MORE) "$more more categories" else
                                    b.name + ", " + Money.formatInr(b.paise) + when {
                                        b.use == null -> ""
                                        over -> ", over its budget"
                                        else -> ", ${(b.use * 100).roundToInt()}% of its budget"
                                    }
                            }
                            .testTag("orbit_${b.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(sizeDp.dp)) {
                            val r = size.minDimension / 2f
                            if (over) drawCircle(Color(0xFFE23B3B).copy(alpha = 0.18f + 0.22f * pulse), r + (3f + 4f * pulse) * density.density)
                            drawCircle(Color.Black.copy(alpha = if (dark) 0.35f else 0.10f), r, Offset(size.width / 2f, size.height / 2f + 2.dp.toPx()))
                            drawCircle(if (dark) Color(0xFF2C241E) else Color.White, r)
                            val sw = (2.5f * k).dp.toPx().coerceAtLeast(2f)
                            val inset = sw / 2f + 1f
                            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(sw))
                            b.use?.let { u ->
                                drawArc(
                                    if (over) Color(0xFFE23B3B) else b.color, -90f, 360f * u.coerceAtMost(1f) * e, false,
                                    Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(sw, cap = StrokeCap.Round),
                                )
                            }
                        }
                        if (b.id == MORE) {
                            Text(b.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val art = categoryArt(b.icon)
                            if (art != null) Image(painterResource(art), null, Modifier.size((sizeDp * 0.6f).dp))
                            else Icon(Ic.forCategory(b.icon ?: "others"), null, tint = b.color, modifier = Modifier.size((sizeDp * 0.5f).dp))
                        }
                    }
                }
            }
            Text(
                when {
                    budgeted == 0 -> "Bigger bubble, bigger spend"
                    overCount > 0 -> "$overCount of ${shown.size} glowing red · over budget"
                    else -> "All ${shown.size} within budget"
                },
                Modifier.fillMaxWidth().testTag("orbit_note"),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                color = if (overCount > 0) Color(0xFFD32F2F) else if (budgeted == 0) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF2E7D32),
            )
        }
    }
}
