package com.owl.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalBrand
import com.owl.tracker.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** One category on the 3D ring: its [share] of the whole, 0–1, clockwise from the top. */
data class Ring3DSlice(val id: String, val color: Color, val share: Float)

/** Where a finger landed on the 3D ring. */
sealed interface RingHit {
    data class Slice(val id: String) : RingHit
    /** The middle, where the owl stands. */
    data object Middle : RingHit
    data object Outside : RingHit
}

/** Each slice's start and end, in degrees clockwise from the top. */
fun ringSpans(slices: List<Ring3DSlice>): List<Pair<Float, Float>> {
    var at = 0f
    return slices.map { s -> val a = at; at += s.share * 360f; a to at }
}

/** The ring's tilt once it has settled, in degrees from lying flat facing you. */
const val RING_TILT = 57f

/**
 * The 3D ring's geometry in px: a clay ring lying on the card, tilted back and seen in perspective (as CSS
 * does it: rotateX, then a viewer [dist] away). The drawing and the finding of the slice under a finger both
 * use it, so they always agree. Plane coordinates: u to the right, v towards you, w up off the card.
 */
class Ring3DGeometry(val width: Float, val height: Float, density: Float) {
    /** Narrow phones get a smaller ring. */
    val unit = density * min(1f, width / (300f * density))
    val r0 = 76f * unit
    val r1 = 124f * unit
    val depth = 15f * unit
    val cx = width / 2f
    val cy = 100f * unit
    private val ox = cx
    private val oy = cy - 201f * unit
    private val dist = 900f * unit

    /** Where a point of the ring's plane lands on the screen, with the ring turned [spin] degrees clockwise. */
    fun project(u: Float, v: Float, w: Float, spin: Float, tilt: Float): Offset {
        val sp = spin * PI.toFloat() / 180f
        val u1 = u * cos(sp) - v * sin(sp)
        val v1 = u * sin(sp) + v * cos(sp)
        val th = tilt * PI.toFloat() / 180f
        val y = v1 * cos(th) - w * sin(th)
        val z = v1 * sin(th) + w * cos(th)
        val s = dist / (dist - z)
        return Offset(ox + (cx + u1 - ox) * s, oy + (cy + y - oy) * s)
    }

    /** The point of the plane at height [h] that lands on screen point ([x], [y]): [project] backwards. */
    fun unproject(x: Float, y: Float, h: Float, spin: Float, tilt: Float): Offset {
        val th = tilt * PI.toFloat() / 180f
        val c = cos(th)
        val sn = sin(th)
        val yy = y - oy
        val v1 = (yy * (dist - h * c) - dist * (cy - oy - h * sn)) / (dist * c + yy * sn)
        val s = dist / (dist - v1 * sn - h * c)
        val u1 = (x - ox) / s + ox - cx
        val sp = spin * PI.toFloat() / 180f
        return Offset(u1 * cos(sp) + v1 * sin(sp), -u1 * sin(sp) + v1 * cos(sp))
    }

    /** Where the owl's feet go: the middle of the hole, on the card. */
    fun feet(tilt: Float) = project(0f, 0f, -depth, 0f, tilt)

    /** How far a picked slice moves out from the middle, and up off the card. */
    val liftOut = 10f * unit
    val liftUp = 24f * unit

    /** Each slice's start and end, in degrees clockwise from the top. */
    fun spans(slices: List<Ring3DSlice>) = ringSpans(slices)

    /** The slice, the middle or nothing under screen point ([x], [y]); [picked] is lifted [lift] (0–1). */
    fun hit(x: Float, y: Float, slices: List<Ring3DSlice>, spin: Float, tilt: Float, picked: String? = null, lift: Float = 0f): RingHit {
        val spans = spans(slices)
        fun angleOf(p: Offset) = ((Math.toDegrees(atan2(p.x.toDouble(), (-p.y).toDouble())) + 360.0) % 360.0).toFloat()
        val pi = slices.indexOfFirst { it.id == picked }
        if (pi >= 0 && lift > 0.05f) {
            // The lifted slice first: it stands up off the card and out from the middle
            val (a0, a1) = spans[pi]
            val mid = (a0 + a1) / 2f * PI.toFloat() / 180f
            val p = unproject(x, y, liftUp * lift, spin, tilt) - Offset(sin(mid), -cos(mid)) * (liftOut * lift)
            val r = hypot(p.x, p.y)
            val a = angleOf(p)
            if (r in (r0 - 6f * unit)..(r1 + 8f * unit) && a in a0..a1) return RingHit.Slice(slices[pi].id)
        }
        val p = unproject(x, y, 0f, spin, tilt)
        val r = hypot(p.x, p.y)
        if (r < r0 - 4f * unit) return RingHit.Middle
        if (r > r1 + 10f * unit) return RingHit.Outside
        val a = angleOf(p)
        spans.forEachIndexed { i, (a0, a1) -> if (a <= a1 || i == slices.lastIndex) return RingHit.Slice(slices[i].id) }
        return RingHit.Outside
    }

    /** The middle of a slice's top, on screen: for tests and screenshots to tap. */
    fun pointOf(slices: List<Ring3DSlice>, id: String, spin: Float = 0f, tilt: Float = RING_TILT): Offset? {
        val i = slices.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        val (a0, a1) = spans(slices)[i]
        val mid = (a0 + a1) / 2f * PI.toFloat() / 180f
        val r = (r0 + r1) / 2f
        return project(r * sin(mid), -r * cos(mid), 0f, spin, tilt)
    }
}

// ---------------------------------------------------------------- drawing

private fun Color.shaded(k: Float) = Color(red * k, green * k, blue * k, alpha)

/** The clay tube's shading across its width (0 inner edge, 1 outer): dark edges, a highlight just past the middle. */
private val TUBE = floatArrayOf(0f, -0.45f, 0.225f, -0.06f, 0.38f, 0f, 0.56f, 0.34f, 0.716f, 0.08f, 0.87f, -0.08f, 1f, -0.42f)

private fun tube(c: Color, t: Float): Color {
    var i = 0
    while (i < TUBE.size - 4 && t > TUBE[i + 2]) i += 2
    val f = ((t - TUBE[i]) / (TUBE[i + 2] - TUBE[i])).coerceIn(0f, 1f)
    val s = TUBE[i + 1] + (TUBE[i + 3] - TUBE[i + 1]) * f
    return if (s >= 0f) lerp(c, Color.White, s) else lerp(c, Color.Black, -s)
}

private const val BANDS = 24
private const val STEP = 3f   // degrees between the points along an arc

/** How each slice stands: dropped in (1 when settled), and lifted (0–1). */
private class Pose(val drop: Float, val lift: Float)

private fun DrawScope.drawRing3D(
    g: Ring3DGeometry, slices: List<Ring3DSlice>, spin: Float, tilt: Float, grow: Float, poses: List<Pose?>, shadow: Color, path: Path,
) {
    val spans = g.spans(slices)
    val d2r = PI.toFloat() / 180f

    // The soft shadow the ring casts on the card, a little towards you
    if (tilt > 1f) for ((ki, ko, a) in listOf(Triple(0.97f, 1.02f, 0.22f), Triple(0.9f, 1.08f, 0.14f), Triple(0.82f, 1.15f, 0.08f))) {
        path.reset()
        val n = 72
        for (j in 0..n) {
            val a = j * 360f / n * d2r
            val p = g.project(g.r1 * ko * grow * sin(a), -g.r1 * ko * grow * cos(a) + 14f * g.unit, -g.depth * grow, 0f, tilt)
            if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        for (j in n downTo 0) {
            val a = j * 360f / n * d2r
            val p = g.project(g.r0 * ki * grow * sin(a), -g.r0 * ki * grow * cos(a) + 14f * g.unit, -g.depth * grow, 0f, tilt)
            path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, shadow.copy(alpha = shadow.alpha * a * (tilt / RING_TILT)))
    }

    class Shape(val i: Int, val pose: Pose, val w0: Float)
    val shapes = slices.indices.mapNotNull { i -> poses[i]?.let { p -> Shape(i, p, 70f * g.unit * (1f - p.drop) + g.liftUp * p.lift) } }

    fun point(s: Shape, r: Float, aDeg: Float, w: Float): Offset {
        val (a0, a1) = spans[s.i]
        val mid = (a0 + a1) / 2f * d2r
        val a = aDeg * d2r
        val rm = (g.r0 + g.r1) / 2f
        val mu = rm * sin(mid)
        val mv = -rm * cos(mid)
        val ds = 0.15f + 0.85f * s.pose.drop
        var u = mu + (r * sin(a) - mu) * ds
        var v = mv + (-r * cos(a) - mv) * ds
        u += sin(mid) * g.liftOut * s.pose.lift
        v += -cos(mid) * g.liftOut * s.pose.lift
        return g.project(u * grow, v * grow, (w + s.w0) * grow, spin, tilt)
    }

    fun arcAngles(s: Shape): List<Float> {
        val (b0, b1) = spans[s.i]
        val gap = min(0.7f, (b1 - b0) * 0.18f)
        val a0 = b0 + gap
        val a1 = b1 - gap
        val n = ceil((a1 - a0) / STEP).toInt().coerceAtLeast(1)
        return List(n + 1) { k -> a0 + (a1 - a0) * k / n }
    }

    // Light comes from the front left: a wall facing it is brighter
    val lx = -0.33f
    val lv = 0.94f
    fun walls(s: Shape) {
        val col = slices[s.i].color
        val angles = arcAngles(s)
        for (k in 0 until angles.size - 1) {
            val m = ((angles[k] + angles[k + 1]) / 2f + spin) * d2r
            val nu = sin(m)
            val nv = -cos(m)
            for (outer in listOf(true, false)) {
                val (fu, fv) = if (outer) nu to nv else -nu to -nv
                if (fv <= 0.02f) continue   // facing away
                val r = if (outer) g.r1 else g.r0
                val lit = (fu * lx + fv * lv).coerceAtLeast(0f)
                val kk = if (outer) 0.40f + 0.40f * lit else 0.30f + 0.32f * lit
                val next = if (k < angles.size - 2) angles[k + 1] + 0.5f else angles[k + 1]
                val p0 = point(s, r, angles[k], 0f)
                val p1 = point(s, r, next, 0f)
                val p2 = point(s, r, next, -g.depth)
                val p3 = point(s, r, angles[k], -g.depth)
                path.reset(); path.moveTo(p0.x, p0.y); path.lineTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y); path.close()
                drawPath(path, col.shaded(kk))
            }
        }
        // The ends, where a slice is cut
        for ((a, sign) in listOf(angles.first() to -1f, angles.last() to 1f)) {
            val m = (a + spin) * d2r
            val fu = sign * cos(m)
            val fv = sign * sin(m)
            if (fv <= 0.02f) continue
            val kk = 0.34f + 0.36f * (fu * lx + fv * lv).coerceAtLeast(0f)
            val p0 = point(s, g.r0, a, 0f); val p1 = point(s, g.r1, a, 0f); val p2 = point(s, g.r1, a, -g.depth); val p3 = point(s, g.r0, a, -g.depth)
            path.reset(); path.moveTo(p0.x, p0.y); path.lineTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y); path.close()
            drawPath(path, col.shaded(kk))
        }
    }

    fun top(s: Shape) {
        val col = slices[s.i].color
        val angles = arcAngles(s)
        val overlap = 0.8f / grow.coerceAtLeast(0.2f)
        for (b in 0 until BANDS) {
            val ra = g.r0 + (g.r1 - g.r0) * b / BANDS
            val rb = g.r0 + (g.r1 - g.r0) * (b + 1) / BANDS + if (b < BANDS - 1) overlap else 0f
            path.reset()
            angles.forEachIndexed { k, a -> val p = point(s, rb, a, 0f); if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            for (k in angles.indices.reversed()) { val p = point(s, ra, angles[k], 0f); path.lineTo(p.x, p.y) }
            path.close()
            drawPath(path, tube(col, (b + 0.5f) / BANDS))
        }
        // a thin catch of light along the outer edge
        path.reset()
        angles.forEachIndexed { k, a -> val p = point(s, g.r1, a, 0f); if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, Color.White.copy(alpha = 0.16f), style = Stroke(1f * g.unit))
    }

    // Settled slices: all their walls, then all their tops. Then any still dropping in or lifted, lowest first.
    val (settled, raised) = shapes.partition { it.w0 < 0.5f && it.pose.drop >= 0.999f }
    settled.forEach { walls(it) }
    settled.forEach { top(it) }
    raised.sortedBy { it.w0 }.forEach { walls(it); top(it) }
}

// ---------------------------------------------------------------- the stage: ring, owl and bubble

private val SettleIn = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)
private val Bounce = CubicBezierEasing(0.34f, 1.5f, 0.64f, 1f)
private val Turn = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)
private const val ENTRY = 2.3f

private fun ease(e: CubicBezierEasing, x: Float) = if (x <= 0f) 0f else if (x >= 1f) 1f else e.transform(x)

/**
 * Home's "Where it went" ring in 3D: the slices are clay tubes lying on the card, the owl stands in the middle and
 * says how things are going. When it first comes into view the ring tilts back from flat and the slices drop in
 * clockwise; then the owl pops up. Tapping a slice turns it to the front and lifts it ([picked]); the owl then talks
 * about that category ([say]).
 */
@Composable
fun RingOwlStage(
    slices: List<Ring3DSlice>,
    description: String,
    say: OwlSay,
    picked: String?,
    actKey: Any?,
    sweepKey: Any?,
    onTap: (RingHit) -> Unit,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val brand = LocalBrand.current
    val haptics = LocalHapticFeedback.current
    var seen by remember(sweepKey) { mutableStateOf(false) }
    val clock = remember(sweepKey) { Animatable(0f) }
    LaunchedEffect(seen, sweepKey) { if (seen) clock.animateTo(ENTRY, tween((ENTRY * 1000).toInt(), easing = LinearEasing)) }
    val t = clock.value
    val settle = ease(SettleIn, (t - 0.05f) / 1.3f)
    val tilt = RING_TILT * settle
    val grow = 0.86f + 0.14f * settle

    val spans = remember(slices) { ringSpans(slices) }
    val spin = remember(sweepKey) { Animatable(0f) }
    LaunchedEffect(picked, spans) {
        val i = slices.indexOfFirst { it.id == picked }
        if (i >= 0 && i < spans.size) {
            val mid = (spans[i].first + spans[i].second) / 2f
            val now = spin.value
            val d = ((180f - mid - now) % 360f + 540f) % 360f - 180f
            spin.animateTo(now + d, tween(900, easing = Turn))
        }
    }
    val lifts = slices.map { s -> key(s.id) { animateFloatAsState(if (s.id == picked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "lift").value } }
    val path = remember { Path() }
    val owlIn = ease(Bounce, (t - 1.35f) / 0.7f)
    val bubbleIn = ease(Bounce, (t - 1.6f) / 0.5f)

    BoxWithConstraints(modifier.fillMaxWidth().onGloballyPositioned {
        if (!seen) { val b = it.boundsInWindow(); if (b.height >= it.size.height * 0.35f) seen = true }
    }) {
        val density = LocalDensity.current
        val k = min(1f, maxWidth.value / 300f)
        val stageH = 230.dp * k
        val widthPx = constraints.maxWidth.toFloat()
        val g = remember(widthPx, density.density) { Ring3DGeometry(widthPx, with(density) { stageH.toPx() }, density.density) }
        val owlSize = 124.dp * k
        val feet = g.feet(tilt)
        val owlPx = with(density) { owlSize.toPx() }
        val tailX = with(density) { (g.cx - 12.dp.toPx()).toDp() }
        val bubbleW = (tailX + 48.dp).coerceIn(200.dp, maxWidth)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OwlBubble(say, onOpen, tailX, bubbleW, Modifier.graphicsLayer {
                scaleX = 0.5f + 0.5f * bubbleIn; scaleY = scaleX; alpha = bubbleIn.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.7f, 1f)
            })
            Box(Modifier.fillMaxWidth().height(stageH)) {
                val latest by rememberUpdatedState(TapState(spin.value, tilt, lifts, picked, onTap))
                Canvas(
                    Modifier.fillMaxWidth().height(stageH)
                        .pointerInput(slices, g) {
                            detectTapGestures { pos ->
                                val now = latest
                                val pi = slices.indexOfFirst { it.id == now.picked }
                                val hit = g.hit(pos.x, pos.y, slices, now.spin, now.tilt, now.picked, if (pi >= 0) now.lifts.getOrElse(pi) { 0f } else 0f)
                                if (hit is RingHit.Slice) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                now.onTap(hit)
                            }
                        }
                        .semantics { contentDescription = description }
                        .testTag("ring3d"),
                ) {
                    val spanList = g.spans(slices)
                    val poses = slices.indices.map { i ->
                        val start = 0.35f + 0.95f * (spanList[i].first / 360f).pow(0.85f)
                        if (t < start) null else Pose(ease(Bounce, (t - start) / 0.7f), lifts[i])
                    }
                    drawRing3D(g, slices, spin.value - 75f * (1f - settle), tilt, grow, poses, if (dark) Color.Black.copy(alpha = 0.9f) else brand.clay.shadow.copy(alpha = 0.55f), path)
                }
                // A warm light on the card where the owl stands
                if (owlIn > 0f) {
                    val glow = if (dark) Color(0x33FFBE5A) else Color(0x22FF9A1F)
                    Box(
                        Modifier.offset { IntOffset((feet.x - 75.dp.toPx()).roundToInt(), (feet.y - 40.dp.toPx()).roundToInt()) }
                            .size(150.dp, 70.dp)
                            .graphicsLayer { alpha = owlIn.coerceIn(0f, 1f) }
                            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(glow, Color.Transparent)), RoundedCornerShape(50)),
                    )
                    MoodOwl(
                        say.mood, owlSize, dark = dark, playKey = actKey to say,
                        modifier = Modifier
                            .offset { IntOffset((feet.x - owlPx / 2f).roundToInt(), (feet.y - owlPx * 84f / 92f).roundToInt()) }
                            .graphicsLayer {
                                scaleX = 0.3f + 0.7f * owlIn; scaleY = scaleX; alpha = (owlIn * 2f).coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(0.5f, 0.91f)
                            },
                    )
                }
            }
        }
    }
}

private class TapState(val spin: Float, val tilt: Float, val lifts: List<Float>, val picked: String?, val onTap: (RingHit) -> Unit)

/** The owl's speech bubble: its exclamation in the mood's colour, a title and a line; "Open" when a category is picked. */
@Composable
private fun OwlBubble(say: OwlSay, onOpen: (() -> Unit)?, tailX: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val brand = LocalBrand.current
    val fill = if (dark) Color(0xFFFFF4E0) else Color.White
    val shape = RoundedCornerShape(18.dp)
    val pop = remember(say) { Animatable(0.85f) }
    LaunchedEffect(say) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
    Box(modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = TransformOrigin(0.7f, 1f) }) {
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = tailX.coerceIn(20.dp, width - 30.dp), y = 7.dp)
                .size(14.dp).rotate(45f).background(fill, RoundedCornerShape(2.dp)),
        )
        Column(
            Modifier.width(width)
                .shadow(8.dp, shape, ambientColor = brand.clay.shadow, spotColor = brand.clay.shadow)
                .clip(shape).background(fill)
                .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 10.dp)
                .testTag("owl_bubble"),
        ) {
            // "Open" beside the mood when there's room, else under it
            SideOrStacked(
                Modifier.fillMaxWidth(), gap = 8.dp, stackedGap = 4.dp,
                start = { Text(say.mood.says, color = say.mood.ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold) },
                end = {
                    if (onOpen != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)
                                .background(Color(0xFF4B2412).copy(alpha = 0.08f)).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                .testTag("owl_open"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Open", color = Color(0xFF4B2412), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                            Icon(Ic.chevR, contentDescription = null, tint = Color(0xFF4B2412), modifier = Modifier.size(16.dp))
                        }
                    }
                },
            )
            // With big text the title may take two lines, shrinking a little first
            FitText(say.title, color = Color(0xFF2A1708), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, minScale = 0.8f)
            Text(say.line, color = Color(0xFF5A4030), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
