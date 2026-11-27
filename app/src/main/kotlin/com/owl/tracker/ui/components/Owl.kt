package com.owl.tracker.ui.components

import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * How the owl feels about some spending, from how much of its budget has gone. [says] is the first line of its
 * speech bubble; [ink] its colour there (dark enough to read on the bubble's cream or white).
 */
enum class OwlMood(val label: String, val says: String, val ink: Color) {
    CHEERING("cheering", "Yay, plenty left!", Color(0xFF1E7A34)),
    HAPPY("happy", "Nicely on track", Color(0xFF3B6E12)),
    WORRIED("worried", "Getting close…", Color(0xFF8A5600)),
    SAD("sad", "A little over", Color(0xFFA84A12)),
    UPSET("upset", "Well over budget", Color(0xFFB42318)),
    CRYING("crying", "Way, way over!", Color(0xFFA8174F)),
    CURIOUS("curious", "No budget yet", Color(0xFF1F5F96));

    companion object {
        /**
         * Half the budget or less used: cheering; up to 80%: happy; up to all of it: worried; up to a quarter over:
         * sad; up to double: upset; more than double: crying. No budget: curious.
         */
        fun of(spent: Long, budget: Long?): OwlMood {
            if (budget == null || budget <= 0) return CURIOUS
            val used = spent.toDouble() / budget
            return when {
                used <= 0.5 -> CHEERING
                used <= 0.8 -> HAPPY
                used <= 1.0 -> WORRIED
                used <= 1.25 -> SAD
                used <= 2.0 -> UPSET
                else -> CRYING
            }
        }
    }
}

/** What the owl says: its mood, a title ("Transport · ₹21,726") and a line ("₹20,726 over its ₹1,000 budget"). */
data class OwlSay(val mood: OwlMood, val title: String, val line: String)

/** How long the owl acts its mood before it settles into the mood's still pose. */
private const val ACT = 7f

/**
 * The app's owl (Round 31: a flat owl in the app's browns, drawn in parts) acting out [mood]: it hops and waves
 * with sparkles, bobs and winks, thinks with a wing at its chin, slumps, shivers with darting eyes and a drop of
 * sweat, cries with its ear tufts down, or tilts its head at a question mark. The act plays for a
 * few seconds whenever the mood or [playKey] changes, then the owl holds the mood's face. With "Remove animations"
 * on, or [still], the act is skipped and only the face shows.
 */
@Composable
fun MoodOwl(mood: OwlMood, size: Dp, modifier: Modifier = Modifier, playKey: Any? = null, dark: Boolean = true, still: Boolean = false) {
    // still: only the mood's face, no act (the owl's "Calm" setting, and pictures made for sharing)
    val clock = remember(mood, playKey, still) { Animatable(if (still) ACT else 0f) }
    LaunchedEffect(mood, playKey, still) { if (!still) clock.animateTo(ACT, tween((ACT * 1000).toInt(), easing = LinearEasing)) }
    Canvas(
        modifier.size(size)
            .semantics { contentDescription = "The owl looks ${mood.label}" }
            .testTag("owl"),
    ) {
                // an 870-unit square around it, feet at the same height in the box as the old owl's (84 of 92)
        val s = this.size.width / 870f
        withTransform({
            scale(s, s, pivot = Offset.Zero)
            translate(-190f, -174f)
        }) { drawOwl(mood, clock.value, dark) }
    }
}

// ---------------------------------------------------------------- the drawing (Round 31), in the owl's own units:
// the same numbers as the mock-up's SVG, a 1040 box with the feet at about (625, 968)

private val DARK = Color(0xFF5A2F14)
private val MID = Color(0xFFA8612D)
private val LIGHT = Color(0xFFE2A86C)
private val PALE = Color(0xFFF5D9B4)
private val BELLY = Color(0xFFFFF4E2)
private val MANGO = Color(0xFFFF9A1F)
private val SPARK = Color(0xFFFFB21A)
private val TEAR = Color(0xFF6FB7E8)

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

/** A value at [phase] (0–1) between keyframes (time, value), eased between each pair. */
private fun kf(phase: Float, vararg frames: Pair<Float, Float>): Float {
    if (phase <= frames.first().first) return frames.first().second
    for (i in 1 until frames.size) {
        val (t1, v1) = frames[i]
        if (phase <= t1) {
            val (t0, v0) = frames[i - 1]
            return v0 + (v1 - v0) * smooth((phase - t0) / (t1 - t0).coerceAtLeast(1e-4f))
        }
    }
    return frames.last().second
}

private fun phase(t: Float, period: Float, delay: Float = 0f): Float { val x = (t - delay) / period; return if (x < 0) 0f else x - kotlin.math.floor(x) }
private fun wave(t: Float, period: Float) = (0.5f - 0.5f * cos(2 * PI.toFloat() * t / period))

/** A quick blink (0 open, 1 shut) at each of [at], seconds into the act. */
private fun blinks(t: Float, vararg at: Float): Float {
    for (b in at) { val d = t - b; if (d in 0f..0.26f) return if (d < 0.1f) d / 0.1f else 1f - (d - 0.1f) / 0.16f }
    return 0f
}

private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

private object OwlArt {
    val tuftL = svg("M330 196 C340 270 400 318 500 296 C470 262 420 226 330 196 Z")
    val tuftR = svg("M920 196 C910 270 850 318 750 296 C780 262 830 226 920 196 Z")
    val head = svg("M480 262 C540 222 710 222 772 258 C840 280 880 330 888 400 C896 470 870 520 836 540 C760 562 690 536 625 548 C560 536 490 562 412 540 C376 520 350 470 360 400 C368 330 410 280 480 262 Z")
    val brow = svg("M440 300 C520 290 590 320 625 420 C660 320 730 290 812 300 C760 270 690 268 625 300 C560 268 492 270 440 300 Z")
    val beak = svg("M625 430 L660 486 L625 545 L592 486 Z")
    val body = svg("M412 532 C340 590 318 700 342 800 C356 880 370 940 382 966 L600 940 C700 930 790 900 840 860 C886 800 900 720 884 650 C870 590 846 556 836 538 C760 560 690 540 625 548 C560 540 490 560 412 532 Z")
    val belly = svg("M520 560 C640 528 762 545 805 612 C842 690 826 800 764 862 C704 912 622 932 560 942 C602 862 640 760 622 662 C612 612 572 578 520 560 Z")
    val bellyShade = svg("M812 650 C832 732 812 828 742 884 C700 912 640 928 590 936 C684 890 766 820 812 650 Z")
    val feet = svg("M528 936 C530 912 560 908 572 922 C584 908 610 912 612 936 L612 968 C604 952 594 950 590 966 C584 950 566 948 560 966 C556 950 540 950 528 968 Z M640 932 C642 908 672 904 684 918 C696 904 722 908 724 932 L724 964 C716 948 706 946 702 962 C696 946 678 944 672 962 C668 946 652 946 640 964 Z")
    val wing = svg("M345 700 C380 600 470 555 560 565 C630 575 650 650 620 730 C590 810 470 910 385 965 C365 880 330 790 345 700 Z")
    val wingIn1 = svg("M378 958 C385 830 440 730 530 650 C480 760 440 860 400 955 Z")
    val wingIn2 = svg("M560 565 C640 590 662 700 615 790 C565 875 470 935 392 966 C480 900 575 822 606 740 C636 660 620 600 560 565 Z")
    val wave = svg("M820 600 C900 560 950 470 930 380 C980 420 990 520 950 600 C920 650 870 680 820 690 Z")
    val waveLine = svg("M930 380 C960 440 960 520 925 580")
    val chin = svg("M850 700 C800 690 730 650 690 580 C680 556 700 540 726 548 C780 566 840 610 880 660 C896 690 876 708 850 700 Z")
    val chinLine = svg("M712 560 C760 574 820 618 862 670")
    val question = svg("M880 210 C880 150 980 150 980 210 C980 250 935 255 930 300")
    val star = svg("M0 -34 L9 -9 L34 0 L9 9 L0 34 L-9 9 L-34 0 L-9 -9 Z")
    val drop = svg("M0 -33 Q26 11 0 33 Q-26 11 0 -33 Z")
}

private const val EL_X = 520f
private const val ER_X = 735f
private const val E_Y = 415f
private const val E_R = 104f

private fun DrawScope.drawOwl(mood: OwlMood, t: Float, dark: Boolean) {
    // The act fades out over its last second and a half, leaving the still pose
    val amp = smooth((ACT - t) / 1.5f)
    val acting = t < ACT
    val tau = 2 * PI.toFloat()

    // whole-owl motion, about the feet
    var tx = 0f; var ty = 0f; var sx = 1f; var sy = 1f; var rot = 0f
    when (mood) {
        OwlMood.CHEERING -> { val p = phase(t, 0.9f)
            ty = -60f * kf(p, 0f to 0f, 0.5f to 1f, 1f to 0f) * amp
            sx = 1f + 0.02f * kf(p, 0f to 1f, 0.5f to -1f, 1f to 1f) * amp; sy = 2f - sx }
        OwlMood.HAPPY -> { sy = 1f - 0.015f * wave(t, 3f); sx = 2f - sy }
        OwlMood.WORRIED -> { sy = 1f - 0.012f * wave(t, 3.2f); sx = 2f - sy }
        OwlMood.SAD -> { ty = 16f + 6f * wave(t, 3f) * amp; sx = 1.03f; sy = 0.96f }
        OwlMood.UPSET -> tx = if (acting) 3f * sin(tau * t / 0.12f) * amp else 0f
        OwlMood.CRYING -> { ty = 18f + 6f * wave(t, 3f) * amp; sx = 1.04f; sy = 0.95f }
        OwlMood.CURIOUS -> Unit
    }
    // the head on top of that
    val headRot = when (mood) {
        OwlMood.WORRIED -> -9f * kf(phase(t, 3.2f), 0f to 0f, 0.3f to 1f, 0.7f to 1f, 1f to 0f) * amp - 6f * (1f - amp)
        OwlMood.CURIOUS -> (-12f + 20f * smooth(wave(t, 4f))) * amp - 8f * (1f - amp)
        OwlMood.CRYING, OwlMood.SAD -> { val p = phase(t, 3f); 5f * kf(p, 0f to 0f, 0.4f to 0f, 0.5f to -1f, 0.6f to 1f, 0.7f to -0.8f, 0.8f to 0f, 1f to 0f) * amp }
        else -> 0f
    }

    val shade = if (dark) Color.Black.copy(alpha = 0.4f) else DARK.copy(alpha = 0.18f)
    drawOval(shade, Offset(470f, 950f), Size(320f, 40f))

    withTransform({
        translate(tx, ty)
        rotate(rot, pivot = Offset(625f, 968f))
        scale(sx, sy, pivot = Offset(625f, 968f))
    }) {
        // body, belly, feet
        drawPath(OwlArt.body, DARK)
        drawPath(OwlArt.belly, BELLY)
        drawPath(OwlArt.bellyShade, PALE)
        drawPath(OwlArt.feet, DARK)
        // the near wing, folded
        drawPath(OwlArt.wing, Brush.linearGradient(listOf(LIGHT, MID), Offset(345f, 565f), Offset(650f, 965f)))
        drawPath(OwlArt.wingIn1, DARK)
        drawPath(OwlArt.wingIn2, DARK)
        // the far wing: waving when cheering, at its chin when thinking
        when (mood) {
            OwlMood.CHEERING, OwlMood.HAPPY -> {
                val a = if (mood == OwlMood.CHEERING) (-14f + 30f * wave(t, 0.9f)) * amp
                        else (if (acting && t < 2.4f) -10f + 22f * wave(t, 0.8f) else 0f) * amp
                if (mood == OwlMood.CHEERING || (acting && t < 2.4f)) rotate(a, pivot = Offset(830f, 610f)) {
                    drawPath(OwlArt.wave, DARK)
                    drawPath(OwlArt.waveLine, MID, style = Stroke(14f, cap = StrokeCap.Round))
                }
            }
            OwlMood.WORRIED -> rotate(-4f * wave(t, 1.6f) * amp, pivot = Offset(830f, 610f)) {
                drawPath(OwlArt.chin, DARK)
                drawPath(OwlArt.chinLine, MID, style = Stroke(14f, cap = StrokeCap.Round))
            }
            else -> Unit
        }
        // the head, tufts drooping when sad or crying
        rotate(headRot, pivot = Offset(625f, 540f)) {
            val droop = if (mood == OwlMood.SAD || mood == OwlMood.CRYING) 22f else 0f
            val flick = if (mood == OwlMood.HAPPY && acting) 6f * kf(phase(t, 3f), 0f to 0f, 0.7f to 0f, 0.8f to 1f, 0.9f to -0.3f, 1f to 0f) else 0f
            rotate(-droop - flick, pivot = Offset(480f, 290f)) { drawPath(OwlArt.tuftL, DARK) }
            rotate(droop + flick, pivot = Offset(770f, 290f)) { drawPath(OwlArt.tuftR, DARK) }
            drawPath(OwlArt.head, DARK)
            drawEyes(mood, t, amp, acting)
            drawPath(OwlArt.brow, DARK)
            drawPath(OwlArt.beak, MANGO)
        }
        // tears and sweat run with the owl
        if (acting && (mood == OwlMood.CRYING || mood == OwlMood.SAD)) {
            val xs = if (mood == OwlMood.CRYING) listOf(470f to 0f, 785f to 0.8f) else listOf(470f to 0.4f)
            for ((x, d) in xs) {
                if (t < d) continue
                val p = phase(t, 1.6f, d)
                translate(x, 525f + 70f * p) { scale(0.6f, 0.6f, pivot = Offset.Zero) { drawPath(OwlArt.drop, TEAR.copy(alpha = (kf(p, 0f to 0f, 0.2f to 1f, 1f to 0f) * amp).coerceIn(0f, 1f))) } }
            }
        }
        if (acting && mood == OwlMood.UPSET) {
            val p = phase(t, 1.2f)
            translate(900f, 333f + 70f * p) { drawPath(OwlArt.drop, TEAR.copy(alpha = (kf(p, 0f to 0f, 0.2f to 1f, 1f to 0f) * amp).coerceIn(0f, 1f))) }
        }
    }

    // around the owl: sparkles, a question mark
    if (acting && mood == OwlMood.CHEERING) {
        for ((c, d) in listOf(Offset(330f, 330f) to 0f, Offset(980f, 250f) to 0.6f)) {
            val p = phase(t, 1.2f, d)
            val k = kf(p, 0f to 0.3f, 0.5f to 1f, 1f to 0.3f)
            val a = kf(p, 0f to 0f, 0.5f to 1f, 1f to 0f) * amp
            translate(c.x, c.y) { scale(k, k, pivot = Offset.Zero) { drawPath(OwlArt.star, SPARK.copy(alpha = a.coerceIn(0f, 1f))) } }
        }
    }
    if (mood == OwlMood.WORRIED || mood == OwlMood.CURIOUS) {
        val a = if (acting) kf(phase(t, 3.2f), 0f to 0f, 0.2f to 0f, 0.35f to 1f, 0.85f to 1f, 1f to 0f) * amp else 0f
        if (a > 0f) {
            drawPath(OwlArt.question, MID.copy(alpha = a), style = Stroke(34f, cap = StrokeCap.Round))
            drawCircle(MID.copy(alpha = a), 20f, Offset(930f, 350f))
        }
    }
}

private fun DrawScope.drawEyes(mood: OwlMood, t: Float, amp: Float, acting: Boolean) {
    val blink = if (acting && (mood == OwlMood.HAPPY || mood == OwlMood.CURIOUS || mood == OwlMood.WORRIED)) blinks(t, 1.4f, 4.4f) else 0f
    val wink = mood == OwlMood.HAPPY && acting && t in 2.6f..3.3f
    // where the pupils look
    var px = 0f; var py = 0f
    when (mood) {
        OwlMood.WORRIED -> { val k = kf(phase(t, 3.2f), 0f to 0f, 0.3f to 1f, 0.7f to 1f, 1f to 0f) * amp; px = -14f * k; py = -22f * k + 6f }
        OwlMood.CURIOUS -> px = 26f
        OwlMood.UPSET -> px = if (acting) (if (phase(t, 1.4f) < 0.5f) -22f else 22f) * amp else 0f
        OwlMood.SAD, OwlMood.CRYING -> py = 18f
        else -> Unit
    }
    scale(1f, 1f - 0.92f * blink, pivot = Offset(625f, E_Y)) {
        for ((cx, left) in listOf(EL_X to true, ER_X to false)) {
            drawCircle(Color.White, E_R, Offset(cx, E_Y))
            val smiling = mood == OwlMood.CHEERING || (wink && !left)
            if (smiling) {
                val d = Path().apply { moveTo(cx - 48f, E_Y + 12f); quadraticBezierTo(cx, E_Y - 38f, cx + 48f, E_Y + 12f) }
                drawPath(d, DARK, style = Stroke(18f, cap = StrokeCap.Round))
                continue
            }
            drawCircle(DARK, 52f, Offset(cx + px, E_Y + py))
            drawCircle(Color.White, 16f, Offset(cx + px + 16f, E_Y + py - 18f))
            // lids: heavy and sloping out when sad, raised in the middle when worried
            val eye = Path().apply { addOval(Rect(Offset(cx, E_Y), E_R)) }
            val lid: Path? = when (mood) {
                OwlMood.SAD, OwlMood.CRYING -> Path().apply {
                    moveTo(cx - 110f, E_Y - 110f); lineTo(cx + 110f, E_Y - 110f)
                    lineTo(cx + 110f, E_Y + if (left) -6f else 30f); lineTo(cx - 110f, E_Y + if (left) 30f else -6f); close() }
                OwlMood.UPSET, OwlMood.WORRIED -> Path().apply {
                    moveTo(cx - 110f, E_Y - 110f); lineTo(cx + 110f, E_Y - 110f)
                    lineTo(cx + 110f, E_Y + if (left) -40f else -20f); lineTo(cx - 110f, E_Y + if (left) -20f else -40f); close() }
                else -> null
            }
            if (lid != null) clipPath(eye) { drawPath(lid, DARK) }
        }
    }
    if (mood == OwlMood.HAPPY || mood == OwlMood.CHEERING) {
        for (x in listOf(440f, 815f)) drawOval(Color(0xFFFF7A6B).copy(alpha = 0.35f), Offset(x - 34f, 500f), Size(68f, 26f))
    }
}

// ---------------------------------------------------------------- the owl that says hello when the app opens

/** The five ways the owl greets you when Owl Tracker opens; one is picked at random each time. */
enum class LaunchAct { BLINK, WAKE_UP, LOOK_AROUND, COIN_WINK, HELLO }

/** How long the greeting plays before the app fades in. */
const val LAUNCH_SECONDS = 1.5f

/**
 * The app icon (the owl on its mango circle with the ₹ coin) acting out [act] at [t] seconds (0..[LAUNCH_SECONDS]).
 * Drawn in the icon's own 300-unit box.
 */
@Composable
fun LaunchBadge(act: LaunchAct, t: Float, size: Dp, modifier: Modifier = Modifier) {
    val text = androidx.compose.ui.text.rememberTextMeasurer()
    val rupee = remember(text) {
        text.measure("₹", androidx.compose.ui.text.TextStyle(color = Color(0xFFB9790A), fontSize = androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
    }
    Canvas(modifier.size(size)) { drawLaunchBadge(act, t, rupee) }
}

/** [LaunchBadge]'s drawing, scaled to fill this DrawScope's width; [rupee] is the coin's ₹ (none in tests). */
internal fun DrawScope.drawLaunchBadge(act: LaunchAct, t: Float, rupee: androidx.compose.ui.text.TextLayoutResult?) {
    val k = this.size.width / 300f
    val p = (t / LAUNCH_SECONDS).coerceIn(0f, 1f)
    scale(k, k, pivot = Offset.Zero) {
        drawCircle(Brush.linearGradient(listOf(Color(0xFFFFF1B3), Color(0xFFFFC85C), Color(0xFFFF9A3D)), Offset.Zero, Offset(300f, 300f)), 150f, Offset(150f, 150f))
        drawOval(DARK.copy(alpha = 0.18f), Offset(92f, 222f), Size(110f, 14f))
        withTransform({ translate(46f, 22f); scale(200f / 1040f, 200f / 1040f, pivot = Offset.Zero); translate(-120f, -20f) }) { drawLaunchOwl(act, p) }
        // the coin: it hops and spins, then a shine runs across it, for the coin wink
        val spin = if (act == LaunchAct.COIN_WINK) smooth(kf(p, 0f to 0f, 0.06f to 0f, 0.45f to 1f, 1f to 1f)) else 0f
        val hop = if (act == LaunchAct.COIN_WINK) -30f * kf(p, 0f to 0f, 0.06f to 0f, 0.3f to 1f, 0.5f to 0f, 1f to 0f) else 0f
        val c = Offset(218f, 198f + hop)
        scale(abs(cos(spin * 4 * PI.toFloat())).coerceAtLeast(0.06f), 1f, pivot = c) {
            drawCircle(Color(0xFFE09A12), 28f, c)
            drawCircle(Color(0xFFFFC93C), 24f, c)
            drawCircle(Color(0xFFE5A417), 17f, c, style = Stroke(1.5f))
            if (rupee != null) drawText(rupee, topLeft = Offset(c.x - rupee.size.width / 2f, c.y - rupee.size.height / 2f))
        }
        if (act == LaunchAct.COIN_WINK) {
            val s = kf(p, 0f to -1f, 0.5f to -1f, 0.75f to 1f, 1f to 1f)
            if (s > -1f && s < 1f) clipPath(Path().apply { addOval(Rect(c, 28f)) }) {
                val x = c.x + 40f * s
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent), Offset(x - 14f, c.y - 10f), Offset(x + 14f, c.y + 10f)), Offset(c.x - 30f, c.y - 30f), Size(60f, 60f))
            }
        }
    }
}

private fun DrawScope.drawLaunchOwl(act: LaunchAct, p: Float) {
    // lids (0 open, 1 shut), where the pupils look, the head tilt, the ear tufts, the whole owl's hop and squash
    var lidL = 0f; var lidR = 0f; var px = 0f; var py = 0f; var head = 0f; var tuft = 0f; var ty = 0f; var sy = 1f
    when (act) {
        LaunchAct.BLINK -> { lidL = kf(p, 0f to 0f, 0.22f to 0f, 0.3f to 1f, 0.4f to 0f, 0.52f to 0f, 0.6f to 1f, 0.7f to 0f, 1f to 0f); lidR = lidL }
        LaunchAct.WAKE_UP -> {
            lidL = kf(p, 0f to 1f, 0.45f to 1f, 0.53f to 0.15f, 0.58f to 0.05f, 0.64f to 0f, 1f to 0f); lidR = lidL
            sy = kf(p, 0f to 0.96f, 0.45f to 0.96f, 0.55f to 1.06f, 0.64f to 0.98f, 0.74f to 1f, 1f to 1f)
            tuft = kf(p, 0f to -18f, 0.45f to -18f, 0.57f to 8f, 0.66f to 0f, 1f to 0f)
        }
        LaunchAct.LOOK_AROUND -> {
            val look = kf(p, 0f to 0f, 0.08f to 0f, 0.2f to -1f, 0.36f to -1f, 0.5f to 1f, 0.64f to 1f, 0.74f to 0f, 1f to 0f)
            px = 34f * look; py = 4f * abs(look); head = 5f * look
            lidL = kf(p, 0f to 0f, 0.78f to 0f, 0.85f to 1f, 0.93f to 0f, 1f to 0f); lidR = lidL
        }
        LaunchAct.COIN_WINK -> {
            val f = kf(p, 0f to 0f, 0.1f to 0f, 0.25f to 1f, 0.45f to 1f, 0.56f to 0f, 1f to 0f)
            px = 26f * f; py = 30f * f
            lidR = kf(p, 0f to 0f, 0.56f to 0f, 0.63f to 1f, 0.8f to 1f, 0.88f to 0f, 1f to 0f)
        }
        LaunchAct.HELLO -> {
            ty = -110f * kf(p, 0f to 0f, 0.1f to 1f, 0.22f to 0f, 1f to 0f)
            lidL = kf(p, 0f to 0f, 0.76f to 0f, 0.83f to 1f, 0.91f to 0f, 1f to 0f); lidR = lidL
        }
    }
    val feet = Offset(625f, 968f)
    withTransform({ translate(0f, ty); scale(2f - sy, sy, pivot = feet) }) {
        drawPath(OwlArt.body, DARK)
        drawPath(OwlArt.belly, BELLY)
        drawPath(OwlArt.bellyShade, PALE)
        drawPath(OwlArt.feet, DARK)
        drawPath(OwlArt.wing, Brush.linearGradient(listOf(LIGHT, MID), Offset(345f, 565f), Offset(650f, 965f)))
        drawPath(OwlArt.wingIn1, DARK)
        drawPath(OwlArt.wingIn2, DARK)
        if (act == LaunchAct.HELLO) {
            val show = kf(p, 0f to 0f, 0.1f to 1f, 0.72f to 1f, 0.84f to 0f, 1f to 0f)
            if (show > 0.02f) {
                val a = kf(p, 0f to -70f, 0.1f to 0f, 0.2f to -16f, 0.3f to 6f, 0.4f to -16f, 0.5f to 6f, 0.62f to 0f, 0.84f to -70f, 1f to -70f)
                rotate(a, pivot = Offset(830f, 640f)) {
                    scale(0.4f + 0.6f * show, pivot = Offset(830f, 640f)) {
                        drawPath(OwlArt.wave, DARK)
                        drawPath(OwlArt.waveLine, MID, style = Stroke(14f, cap = StrokeCap.Round))
                    }
                }
            }
        }
        rotate(head, pivot = Offset(625f, 540f)) {
            rotate(tuft, pivot = Offset(480f, 290f)) { drawPath(OwlArt.tuftL, DARK) }
            rotate(-tuft, pivot = Offset(770f, 290f)) { drawPath(OwlArt.tuftR, DARK) }
            drawPath(OwlArt.head, DARK)
            for ((cx, lid) in listOf(EL_X to lidL, ER_X to lidR)) {
                val c = Offset(cx, E_Y)
                drawCircle(Color.White, E_R, c)
                drawCircle(DARK, 52f, Offset(cx + px, E_Y + py))
                drawCircle(Color.White, 16f, Offset(cx + px + 16f, E_Y + py - 18f))
                if (lid > 0.01f) clipPath(Path().apply { addOval(Rect(c, E_R)) }) {
                    drawRect(DARK, Offset(cx - E_R, E_Y - E_R), Size(2 * E_R, 2 * E_R * lid))
                    if (lid > 0.85f) {
                        val crease = Path().apply { moveTo(cx - 70f, E_Y + 25f); quadraticBezierTo(cx, E_Y + 65f, cx + 70f, E_Y + 25f) }
                        drawPath(crease, LIGHT, style = Stroke(12f, cap = StrokeCap.Round))
                    }
                }
            }
            drawPath(OwlArt.brow, DARK)
            drawPath(OwlArt.beak, MANGO)
        }
    }
    // "z Z" drifting up while it sleeps
    if (act == LaunchAct.WAKE_UP) {
        for ((i, at) in listOf(Offset(900f, 250f) to 0f, Offset(960f, 170f) to 0.12f).withIndex()) {
            val (o, d) = at
            val q = ((p - d) / 0.4f).coerceIn(0f, 1f)
            val a = kf(q, 0f to 0f, 0.3f to 1f, 0.8f to 1f, 1f to 0f)
            if (a <= 0f) continue
            val s = if (i == 0) 60f else 85f
            val z = Path().apply { moveTo(0f, 0f); lineTo(s, 0f); lineTo(0f, s); lineTo(s, s) }
            translate(o.x + 30f * q, o.y - 50f * q) { drawPath(z, DARK.copy(alpha = a * 0.8f), style = Stroke(16f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)) }
        }
    }
}

// ---------------------------------------------------------------- the owl in Home's header, alive

/**
 * The owl beside Home's greeting, alive: now and then it blinks, looks around, or (less often) hops and waves,
 * the same moves as its hello when the app opens. Tap it and it waves at you. Between moves it sits still, and with
 * [wander] off (tests) it only moves when tapped. The month's mood stays with the owl in the ring.
 */
@Composable
fun IdleOwl(size: Dp, modifier: Modifier = Modifier, wander: Boolean = true) {
    val clock = remember { Animatable(LAUNCH_SECONDS) }
    var act by remember { mutableStateOf(LaunchAct.BLINK) }
    val scope = rememberCoroutineScope()
    val play = { a: LaunchAct ->
        act = a
        scope.launch {
            clock.snapTo(0f)
            clock.animateTo(LAUNCH_SECONDS, tween((LAUNCH_SECONDS * 1000).toInt(), easing = LinearEasing))
        }
        Unit
    }
    if (wander && !androidx.compose.ui.platform.LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1_500)
            while (true) {
                val r = kotlin.random.Random.nextInt(10)
                play(if (r < 5) LaunchAct.BLINK else if (r < 8) LaunchAct.LOOK_AROUND else LaunchAct.HELLO)
                kotlinx.coroutines.delay(kotlin.random.Random.nextLong(7_000, 12_000))
            }
        }
    }
    Canvas(
        modifier.size(size)
            .clickable(onClickLabel = "Wave at the owl") { play(LaunchAct.HELLO) }
            .semantics { contentDescription = "The owl" }
            .testTag("header_owl"),
    ) {
        val s = this.size.width / 870f
        withTransform({
            scale(s, s, pivot = Offset.Zero)
            translate(-190f, -174f)
        }) { drawLaunchOwl(act, clock.value / LAUNCH_SECONDS) }
    }
}
