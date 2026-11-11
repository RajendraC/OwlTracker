package com.owl.tracker.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
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
 * The app's owl (the one on the icon) acting out [mood]: it hops and cheers, sways, fidgets with a drop of sweat,
 * sighs, frowns under a rain cloud, cries into a puddle, or tilts its head at a question mark. The act plays for a
 * few seconds whenever the mood or [playKey] changes, then the owl holds the mood's face. With "Remove animations"
 * on, the act is skipped and only the face shows.
 */
@Composable
fun MoodOwl(mood: OwlMood, size: Dp, modifier: Modifier = Modifier, playKey: Any? = null, dark: Boolean = true) {
    val clock = remember(mood, playKey) { Animatable(0f) }
    LaunchedEffect(mood, playKey) { clock.animateTo(ACT, tween((ACT * 1000).toInt(), easing = LinearEasing)) }
    Canvas(
        modifier.size(size)
            .semantics { contentDescription = "The owl looks ${mood.label}" }
            .testTag("owl"),
    ) {
        val s = this.size.width / 92f
        withTransform({
            translate(46f * s, 84f * s)
            scale(s, s, pivot = Offset.Zero)
        }) { drawOwl(mood, clock.value, dark) }
    }
}

// ---------------------------------------------------------------- the drawing, in the owl's own units
// (the same as the mock-ups: feet at 0,0, the body 46 tall above them)

private val BROW = Color(0xFF4A220C)
private val LID = Color(0xFF8E4E24)
private val EAR = Color(0xFF7A3B18)
private val MANGO = Color(0xFFFF9A1F)
private val RING = Color(0xFFFFC85C)
private val PUPIL = Color(0xFF2A1708)
private val MOUTH = Color(0xFF5E2F12)
private val TEAR = Color(0xFF8FD3FF)
private val SPARK = Color(0xFFFFD84A)

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

private fun Path.drop() { moveTo(0f, -3.6f); cubicTo(1.8f, -0.9f, 2.7f, 0.9f, 2.7f, 2f); cubicTo(2.7f, 5.6f, -2.7f, 5.6f, -2.7f, 2f); cubicTo(-2.7f, 0.9f, -1.8f, -0.9f, 0f, -3.6f); close() }

private fun DrawScope.drawOwl(mood: OwlMood, t: Float, dark: Boolean) {
    // The act fades out over its last second and a half, leaving the still pose
    val amp = smooth((ACT - t) / 1.5f)
    val acting = t < ACT

    // body motion, about the feet
    var tx = 0f; var ty = 0f; var sx = 1f; var sy = 1f; var rot = 0f
    when (mood) {
        OwlMood.CHEERING -> { val p = phase(t, 0.95f)
            ty = kf(p, 0f to 0f, 0.18f to 0f, 0.48f to -10f, 0.78f to 0f, 1f to 0f) * amp
            sx = 1f + kf(p, 0f to 0.07f, 0.18f to 0f, 0.48f to -0.04f, 0.78f to 0.05f, 1f to 0.07f) * amp
            sy = 1f + kf(p, 0f to -0.07f, 0.18f to 0f, 0.48f to 0.05f, 0.78f to -0.05f, 1f to -0.07f) * amp }
        OwlMood.HAPPY -> ty = -2.2f * wave(t, 2.4f) * amp
        OwlMood.WORRIED -> rot = 2.5f * sin(2 * PI.toFloat() * t / 2.4f) * amp
        OwlMood.SAD -> { val p = phase(t, 4.2f)
            sx = 1f + kf(p, 0f to 0f, 0.35f to 0.02f, 0.55f to 0.02f, 0.75f to 0f, 1f to 0f) * amp
            sy = 1f + kf(p, 0f to 0f, 0.35f to 0.04f, 0.55f to -0.06f, 0.75f to -0.04f, 1f to 0f) * amp
            ty = kf(p, 0f to 0f, 0.55f to 1f, 0.75f to 0f) * amp }
        OwlMood.UPSET -> { val p = phase(t, 2.6f)
            if (p < 0.45f) { val w = sin(2 * PI.toFloat() * p / 0.1f); tx = 1.6f * w * amp; rot = w * amp } }
        OwlMood.CRYING -> { val p = phase(t, 1.3f)
            val b = kf(p, 0f to 0f, 0.1f to 1f, 0.2f to 0f, 0.3f to 1f, 0.4f to 0f, 1f to 0f) * amp
            ty = b; sx = 1f + 0.03f * b; sy = 1f - 0.05f * b }
        OwlMood.CURIOUS -> rot = (-8f + 13f * smooth(wave(t, 6.4f))) * amp - 3f * (1f - amp)
    }

    // shadow, and the crying owl's puddle under it
    drawOval(if (dark) Color.Black.copy(alpha = 0.45f) else Color(0xFF7A3B18).copy(alpha = 0.25f), Offset(-17f, -2.7f), Size(34f, 6.4f))
    if (mood == OwlMood.CRYING) {
        val g = smooth(t / 3f)
        val w = 48f * (0.25f + 0.75f * g)
        drawOval(TEAR.copy(alpha = 0.55f * g.coerceAtLeast(0.2f)), Offset(-w / 2, -2.6f), Size(w, 7.2f))
    }

    withTransform({
        translate(tx, ty)
        rotate(rot, pivot = Offset.Zero)
        scale(sx, sy, pivot = Offset.Zero)
    }) {
        // feet
        for ((x0, x1) in listOf(-6f to -8f, -3f to -3f, 6f to 8f, 3f to 3f)) {
            drawLine(MANGO, Offset(x0, -1f), Offset(x1, -4f), 1.8f, StrokeCap.Round)
        }
        // body
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFD9A06A), Color(0xFF9A5528), Color(0xFF5E2F12)), Offset(-6f, -35.5f), 36.5f),
            Offset(-20f, -47f), Size(40f, 46f),
        )
        // ears: pricked, or drooping when sad or crying
        val ears = Path().apply {
            if (mood == OwlMood.SAD || mood == OwlMood.CRYING) {
                moveTo(-17f, -41f); lineTo(-22f, -50f); lineTo(-8f, -44f); close(); moveTo(17f, -41f); lineTo(22f, -50f); lineTo(8f, -44f); close()
            } else {
                moveTo(-17f, -40f); lineTo(-12f, -52f); lineTo(-6f, -44f); close(); moveTo(17f, -40f); lineTo(12f, -52f); lineTo(6f, -44f); close()
            }
        }
        drawPath(ears, EAR)
        // belly and its feather marks
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8EC), Color(0xFFFFE7C2), Color(0xFFF4C98E)), Offset(-3.8f, -20.8f), 20.4f), 12f, Offset(0f, -16f))
        val marks = Path().apply {
            for ((x, y) in listOf(-6f to -19f, 2f to -19f, -4f to -13f, 0f to -13f)) { moveTo(x, y); quadraticBezierTo(x + 2f, y + 2f, x + 4f, y) }
        }
        drawPath(marks, Color(0xFFE2B27A), style = Stroke(1.1f, cap = StrokeCap.Round))
        // wings
        drawWings(mood, t, amp)
        // cheeks
        when (mood) {
            OwlMood.CHEERING, OwlMood.HAPPY -> for (x in listOf(-14f, 14f)) drawOval(Color(0xFFFF7A6B).copy(alpha = 0.5f), Offset(x - 3.2f, -26.4f), Size(6.4f, 3.8f))
            OwlMood.UPSET -> { val a = if (acting) 0.55f * (0.85f + 0.15f * sin(2 * PI.toFloat() * t / 2.6f)) else 0.5f
                for (x in listOf(-13.5f, 13.5f)) drawOval(Color(0xFFFF3B2E).copy(alpha = a), Offset(x - 4f, -26.9f), Size(8f, 4.8f)) }
            else -> Unit
        }
        drawEyes(mood, t, amp, acting)
        drawBrowsAndBeak(mood, t, acting)
        // the shine on its head
        rotate(-30f, pivot = Offset(-10f, -40f)) { drawOval(Color.White.copy(alpha = 0.45f), Offset(-15f, -42.4f), Size(10f, 4.8f)) }
        // tears down its face
        if (mood == OwlMood.CRYING) {
            val streak = Path().apply { moveTo(-13f, -29f); quadraticBezierTo(-16.5f, -18f, -15.5f, -6f); moveTo(13f, -29f); quadraticBezierTo(16.5f, -18f, 15.5f, -6f) }
            drawPath(streak, TEAR.copy(alpha = 0.75f), style = Stroke(2.6f, cap = StrokeCap.Round))
            if (acting) for ((x, dx, d) in listOf(Triple(-13f, -4f, 0f), Triple(13f, 4f, 0.35f), Triple(-13f, -5f, 0.6f), Triple(13f, 5f, 0.9f))) {
                if (t < d) continue
                val p = phase(t, 1.2f, d)
                val a = (if (p < 0.12f) p / 0.12f else 1f - (p - 0.12f) / 0.88f) * amp
                translate(x + dx * p, -29f + 26f * p) { drawPath(Path().apply { drop() }, Color(0xFFBFE6FF).copy(alpha = a.coerceIn(0f, 1f))) }
            }
        }
        // a drop of sweat sliding down its temple
        if (mood == OwlMood.WORRIED && acting) {
            val p = phase(t, 2.2f)
            val a = kf(p, 0f to 0f, 0.2f to 1f, 0.8f to 1f, 1f to 0f) * amp
            translate(18f, -45f + kf(p, 0f to -2f, 0.8f to 9f, 1f to 11f)) { drawPath(Path().apply { drop() }, Color(0xFF9FDCFF).copy(alpha = a)) }
        }
    }

    drawAround(mood, t, amp, acting)
}

private fun DrawScope.drawWings(mood: OwlMood, t: Float, amp: Float) {
    val fill = Brush.radialGradient(listOf(Color(0xFFC07A42), Color(0xFF7A3B18), Color(0xFF4A220C)), Offset(-21.8f, -27.6f), 17f)
    val fillR = Brush.radialGradient(listOf(Color(0xFFC07A42), Color(0xFF7A3B18), Color(0xFF4A220C)), Offset(20.2f, -27.6f), 17f)
    val left = Path().apply { moveTo(-15f, -36f); cubicTo(-25f, -33f, -27f, -18f, -21f, -8f); cubicTo(-16f, -13f, -14f, -26f, -15f, -36f); close() }
    val right = Path().apply { moveTo(15f, -36f); cubicTo(25f, -33f, 27f, -18f, 21f, -8f); cubicTo(16f, -13f, 14f, -26f, 15f, -36f); close() }
    val (l, r) = when (mood) {
        OwlMood.CHEERING -> { val a = (15f + 55f * wave(t, 0.84f)) * amp + 15f * (1f - amp); a to -a }
        OwlMood.HAPPY -> 0f to kf(phase(t, 3f), 0f to 0f, 0.55f to 0f, 0.65f to -75f, 0.73f to -50f, 0.81f to -75f, 0.89f to -50f, 1f to 0f) * amp
        OwlMood.WORRIED -> -14f to 14f
        OwlMood.CURIOUS -> 0f to kf(phase(t, 3.2f), 0f to 0f, 0.4f to 0f, 0.55f to -120f, 0.62f to -108f, 0.69f to -120f, 0.76f to -108f, 0.88f to 0f, 1f to 0f) * amp
        else -> 0f to 0f
    }
    rotate(l, pivot = Offset(-15.9f, -33.8f)) { drawPath(left, fill) }
    rotate(r, pivot = Offset(15.9f, -33.8f)) { drawPath(right, fillR) }
}

private fun DrawScope.drawEyes(mood: OwlMood, t: Float, amp: Float, acting: Boolean) {
    fun ring(x: Float) = drawCircle(RING, 8f, Offset(x, -33f), style = Stroke(1.4f))
    fun shut(x: Float, path: Path) {
        drawCircle(LID, 8f, Offset(x, -33f)); ring(x)
        drawPath(path, PUPIL, style = Stroke(2f, cap = StrokeCap.Round))
    }
    fun open(x: Float, px: Float, py: Float, pr: Float, lid: Float) {
        drawCircle(Color.White, 8f, Offset(x, -33f))
        drawCircle(PUPIL, pr, Offset(x + px, py))
        drawCircle(Color.White, 1.3f, Offset(x + px + 1.4f, py - 1.5f))
        if (lid > 0.01f) {
            val eye = Path().apply { addOval(Rect(Offset(x, -33f), 8f)) }
            clipPath(eye) { drawRect(LID, Offset(x - 9f, -42f), Size(18f, 18f * lid)) }
        }
        ring(x)
    }
    when (mood) {
        OwlMood.CHEERING -> {
            shut(-8f, Path().apply { moveTo(-12.5f, -31.5f); quadraticBezierTo(-8f, -37.5f, -3.5f, -31.5f) })
            shut(8f, Path().apply { moveTo(3.5f, -31.5f); quadraticBezierTo(8f, -37.5f, 12.5f, -31.5f) })
        }
        OwlMood.CRYING -> {
            shut(-8f, Path().apply { moveTo(-12.5f, -34f); quadraticBezierTo(-8f, -30f, -3.5f, -34f); moveTo(-12f, -31f); lineTo(-9f, -32.5f) })
            shut(8f, Path().apply { moveTo(3.5f, -34f); quadraticBezierTo(8f, -30f, 12.5f, -34f); moveTo(12f, -31f); lineTo(9f, -32.5f) })
        }
        OwlMood.SAD -> {
            val lid = 0.48f + 0.52f * max(blinks(t, 2.0f), blinks(t, 5.2f))
            open(-8f, 0f, -30.5f, 4f, lid); open(8f, 0f, -30.5f, 4f, lid)
        }
        OwlMood.UPSET -> { open(-8f, 1f, -32f, 3.2f, 0.3f); open(8f, -1f, -32f, 3.2f, 0.3f) }
        OwlMood.CURIOUS -> { val lid = blinks(t, 1.8f, 4.6f); open(-8f, 2.4f, -35f, 4f, lid); open(8f, 2.4f, -35f, 4f, lid) }
        OwlMood.WORRIED -> {
            val dart = if (acting) kf(phase(t, 2.4f), 0f to 0f, 0.18f to -2.4f, 0.42f to -2.4f, 0.58f to 2.4f, 0.82f to 2.4f, 1f to 0f) * amp else 0f
            val lid = blinks(t, 3.1f, 6.2f)
            open(-8f, dart, -33f, 3.6f, lid); open(8f, dart, -33f, 3.6f, lid)
        }
        OwlMood.HAPPY -> { val lid = blinks(t, 1.6f, 4.1f, 6.3f); open(-8f, 1f, -32.5f, 4f, lid); open(8f, 1f, -32.5f, 4f, lid) }
    }
}

private fun DrawScope.drawBrowsAndBeak(mood: OwlMood, t: Float, acting: Boolean) {
    val brows = Path()
    when (mood) {
        OwlMood.WORRIED -> { brows.moveTo(-13f, -42.5f); brows.lineTo(-4.5f, -45.2f); brows.moveTo(4.5f, -45.2f); brows.lineTo(13f, -42.5f) }
        OwlMood.SAD -> { brows.moveTo(-13f, -42.8f); brows.quadraticBezierTo(-9f, -44f, -5f, -45.6f); brows.moveTo(5f, -45.6f); brows.quadraticBezierTo(9f, -44f, 13f, -42.8f) }
        OwlMood.UPSET -> { brows.moveTo(-13f, -46f); brows.lineTo(-4f, -42f); brows.moveTo(4f, -42f); brows.lineTo(13f, -46f) }
        OwlMood.CRYING -> { brows.moveTo(-13f, -41.5f); brows.lineTo(-4.5f, -45.8f); brows.moveTo(4.5f, -45.8f); brows.lineTo(13f, -41.5f) }
        OwlMood.CURIOUS -> { brows.moveTo(-12.5f, -43.6f); brows.lineTo(-4f, -43.6f); brows.moveTo(4f, -46.2f); brows.quadraticBezierTo(8.5f, -49.5f, 13f, -46.2f) }
        else -> Unit
    }
    if (!brows.isEmpty) drawPath(brows, BROW, style = Stroke(2.6f, cap = StrokeCap.Round))
    drawPath(Path().apply { moveTo(-2.8f, -26f); lineTo(0f, -21.2f); lineTo(2.8f, -26f); close() }, MANGO)
    val line = Stroke(1.5f, cap = StrokeCap.Round)
    when (mood) {
        OwlMood.CHEERING -> {
            drawPath(Path().apply { moveTo(-3.6f, -20.6f); quadraticBezierTo(0f, -15.6f, 3.6f, -20.6f); close() }, MOUTH)
            drawPath(Path().apply { moveTo(-1.8f, -18.2f); quadraticBezierTo(0f, -16.6f, 1.8f, -18.2f) }, Color(0xFFFF7A6B), style = Stroke(1.2f))
        }
        OwlMood.HAPPY -> drawPath(Path().apply { moveTo(-3.6f, -20f); quadraticBezierTo(0f, -16.8f, 3.6f, -20f) }, MOUTH, style = line)
        OwlMood.WORRIED -> drawPath(Path().apply {
            moveTo(-4f, -19.4f); quadraticBezierTo(-2.67f, -20.7f, -1.33f, -19.4f); quadraticBezierTo(0f, -18.1f, 1.33f, -19.4f); quadraticBezierTo(2.67f, -20.7f, 4f, -19.4f)
        }, MOUTH, style = Stroke(1.4f, cap = StrokeCap.Round))
        OwlMood.SAD -> drawPath(Path().apply { moveTo(-3.6f, -18.4f); quadraticBezierTo(0f, -21f, 3.6f, -18.4f) }, MOUTH, style = line)
        OwlMood.UPSET -> drawPath(Path().apply { moveTo(-4.2f, -18f); quadraticBezierTo(0f, -21.8f, 4.2f, -18f) }, MOUTH, style = Stroke(1.9f, cap = StrokeCap.Round))
        OwlMood.CRYING -> {
            val o = if (acting) 0.7f + 0.45f * wave(t, 0.64f) else 0.8f
            scale(1f, o, pivot = Offset(0f, -20.2f)) {
                drawOval(Color(0xFF4A1E0A), Offset(-3.4f, -20.2f), Size(6.8f, 5.2f))
                drawOval(Color(0xFFFF7A6B), Offset(-1.8f, -17.6f), Size(3.6f, 2f))
            }
        }
        OwlMood.CURIOUS -> drawCircle(MOUTH, 1.4f, Offset(0.8f, -18.8f))
    }
}

private fun DrawScope.drawAround(mood: OwlMood, t: Float, amp: Float, acting: Boolean) {
    when (mood) {
        OwlMood.CHEERING -> if (acting) {
            for ((x, y, k, d) in listOf(Quad(-31f, -50f, 1.1f, 0f), Quad(29f, -60f, 1.4f, 0.35f), Quad(34f, -30f, 0.9f, 0.7f), Quad(-35f, -24f, 0.8f, 1.0f), Quad(-14f, -68f, 0.7f, 0.5f))) {
                if (t < d) continue
                val p = phase(t, 1.4f, d)
                val s = (0.2f + 0.8f * sin(PI.toFloat() * p)) * k * amp
                val star = Path().apply {
                    for (i in 0 until 8) {
                        val r = if (i % 2 == 0) 5f else 1.3f
                        val a = (i * 45f - 90f + 45f * p) * PI.toFloat() / 180f
                        if (i == 0) moveTo(r * cos(a), r * sin(a)) else lineTo(r * cos(a), r * sin(a))
                    }
                    close()
                }
                translate(x, y) { scale(s, s, pivot = Offset.Zero) { drawPath(star, SPARK.copy(alpha = sin(PI.toFloat() * p).coerceIn(0f, 1f))) } }
            }
        }
        OwlMood.SAD -> if (acting) {
            val p = phase(t, 4.2f)
            val a = kf(p, 0f to 0f, 0.5f to 0f, 0.58f to 0.9f, 1f to 0f) * amp
            val k = 0.6f + 1.1f * ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val dx = -14f * ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val dy = 7f * ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val puff = Color(0xFFC9C2BA).copy(alpha = a)
            translate(-8f + dx, -19f + dy) { scale(k, k, pivot = Offset.Zero) {
                drawCircle(puff, 2.4f, Offset.Zero); drawCircle(puff, 1.8f, Offset(-3f, 1.5f)); drawCircle(puff, 1.6f, Offset(2.2f, 1.8f))
            } }
        }
        OwlMood.UPSET -> {
            val bob = if (acting) -2.5f * wave(t, 4f) * amp else 0f
            translate(0f, bob) {
                if (acting) for ((x, d) in listOf(-8f to 0f, -2f to 0.25f, 4f to 0.1f, 10f to 0.4f)) {
                    val p = phase(t, 0.7f, d)
                    drawLine(TEAR.copy(alpha = (1f - p) * amp), Offset(x, -60f + 9f * p), Offset(x - 1.2f, -56f + 9f * p), 1.4f, StrokeCap.Round)
                }
                val cloud = Brush.radialGradient(listOf(Color(0xFF9AA0AA), Color(0xFF6B717C), Color(0xFF454A54)), Offset(-3f, -74f), 22f)
                drawCircle(cloud, 6f, Offset(-7f, -68f)); drawCircle(cloud, 8f, Offset(1f, -71.5f)); drawCircle(cloud, 6f, Offset(9f, -67.5f))
                drawRoundRect(cloud, Offset(-12f, -68f), Size(26f, 7f), androidx.compose.ui.geometry.CornerRadius(3.5f))
                if (acting) {
                    val p = phase(t, 2.6f)
                    val flash = if (p in 0.6f..0.68f) (if (p in 0.615f..0.63f) 0.2f else 1f) else 0f
                    if (flash > 0f) drawPath(Path().apply {
                        moveTo(2f, -62f); lineTo(-2f, -55.5f); lineTo(1.5f, -55.5f); lineTo(-1.5f, -48.5f); lineTo(5.5f, -57.5f); lineTo(1.8f, -57.5f); lineTo(4.5f, -62f); close()
                    }, SPARK.copy(alpha = flash * amp))
                }
            }
        }
        OwlMood.CURIOUS -> {
            val p = if (acting) wave(t, 3.2f) * amp else 0f
            translate(25f, -60f - 4f * p) { rotate(-8f + 16f * p, pivot = Offset.Zero) {
                drawPath(Path().apply { moveTo(-3.2f, -3f); cubicTo(-3.2f, -7.5f, 3.4f, -7.8f, 3.4f, -3.6f); cubicTo(3.4f, -1f, 0f, -0.6f, 0f, 2.2f) },
                    SPARK, style = Stroke(2.3f, cap = StrokeCap.Round))
                drawCircle(SPARK, 1.35f, Offset(0f, 5.6f))
            } }
        }
        else -> Unit
    }
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
