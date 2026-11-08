package com.owl.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Motion for Reports, in the spirit of Apple Health: numbers count up, bars and rings grow in one
 * after another, cards rise into place. Everything is short (under a second) and follows Android's
 * animation speed setting, so "Remove animations" turns it off.
 */

/** Quick start, long gentle settle. */
val SettleEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * A money amount that counts up from zero the first time it's shown, then glides to each new value.
 * It always ends exactly on [target] (the in-between values are only for show).
 */
@Composable
fun animatedPaise(target: Long, durationMs: Int = 900): Long {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.toFloat(), tween(durationMs, easing = SettleEasing)) }
    return if (!anim.isRunning && anim.targetValue == target.toFloat()) target else anim.value.toLong()
}

/** Goes from 0 to 1 when first shown, and again whenever [key] changes: charts that grow in. */
@Composable
fun growIn(key: Any?, durationMs: Int = 850, delayMs: Int = 0): Float {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.animateTo(1f, tween(durationMs, delayMs, SettleEasing)) }
    return p.value
}

/** How far item [i] of [n] has grown when they grow one after another within an overall [progress]. */
fun stagger(progress: Float, i: Int, n: Int, spread: Float = 0.45f): Float {
    if (n <= 1) return progress
    val start = spread * i / (n - 1)
    return ((progress - start) / (1f - spread)).coerceIn(0f, 1f)
}

/**
 * Fades and lifts a card into place the first time it appears on a screen. [seen] remembers which
 * cards have, so scrolling back doesn't replay it; cards on the first screenful follow each other by
 * [order], and ones scrolled to later come in straight away.
 */
@Composable
fun Reveal(key: Any, seen: MutableSet<Any>, order: Int, openedAt: Long, content: @Composable () -> Unit) {
    val first = remember(key) { seen.add(key) }
    val p = remember(key) { Animatable(if (first) 0f else 1f) }
    if (first) LaunchedEffect(key) {
        delay((order * 70L - (System.currentTimeMillis() - openedAt)).coerceIn(0L, 420L))
        p.animateTo(1f, tween(520, easing = SettleEasing))
    }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 28.dp.toPx() }) { content() }
}
