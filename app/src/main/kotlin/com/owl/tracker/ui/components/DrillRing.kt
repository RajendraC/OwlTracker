package com.owl.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owl.tracker.AppIcons
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.data.Txn
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors
import kotlin.math.atan2
import kotlin.math.hypot

/** A category on the ring. [note] replaces the share under its name in the list; [warn] colours it. */
data class RingCategory(val id: String, val name: String, val color: Color, val paise: Long, val note: String? = null, val warn: Boolean = false)

/** A merchant inside the category opened. */
data class RingMerchant(val key: String, val name: String, val paise: Long)

private data class Slice(val key: String, val name: String, val paise: Long, val share: Float, val color: Color)

/**
 * "Where it went": a ring you can touch, shared by Home and Reports. Slide a finger round it to see
 * each slice (it eases out, with a haptic tick, and the centre shows it); tap a category to open it
 * into its merchants, and a merchant to see its payments. The arrow goes up one level; the screen
 * using it hooks the phone's Back to do the same.
 *
 * [merchants] are the opened category's merchants, biggest first, not narrowed to [pickedMerchant];
 * [payments] are the picked merchant's, newest first.
 */
@Composable
fun DrillRingCard(
    categories: List<RingCategory>,
    pickedCategory: String?,
    pickedMerchant: String?,
    merchants: List<RingMerchant>,
    payments: List<Txn>,
    paymentCount: Int,
    sweepKey: Any?,
    appIcons: AppIcons,
    catMap: Map<String, Category>,
    dayText: (Txn) -> String,
    onCategory: (String) -> Unit,
    onMerchant: (String) -> Unit,
    onPayment: (Txn) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Where it went",
    moreBelow: Boolean = false,
    /** Shown beside the title at the top level, like Home's "See all". */
    headerAction: (@Composable () -> Unit)? = null,
    /** The picture of the category opened, shown above its merchants (Home has one). */
    scene: (@Composable (categoryId: String) -> Unit)? = null,
) {
    val sc = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceVariant
    val haptics = LocalHapticFeedback.current
    val total = categories.sumOf { it.paise }.coerceAtLeast(1)
    val pick = pickedCategory?.let { id -> categories.firstOrNull { it.id == id } }
    val merchantKey = pickedMerchant?.takeIf { pick != null }
    val pickColor = pick?.color ?: MaterialTheme.colorScheme.primary
    val pickShare = (pick?.paise ?: 0L).toFloat() / total

    // Level 0: categories. Levels 1 and 2: the opened category's merchants (2 has one of them picked).
    val slices: List<Slice> = remember(pick, categories, merchants, merchantKey) {
        if (pick == null) {
            categories.map { Slice(it.id, it.name, it.paise, it.paise.toFloat() / total, it.color) }
        } else {
            // With a merchant picked, the category's own total may be narrowed to that merchant, so the
            // whole is rebuilt from its merchants: the ring stays the whole category
            val whole = (if (merchantKey == null) pick.paise else merchants.sumOf { it.paise }).coerceAtLeast(1)
            val top = merchants.take(6)
            val rest = (whole - top.sumOf { it.paise }).coerceAtLeast(0)
            val list = top.map { it.key to (it.name to it.paise) } + if (rest > 0) listOf("" to ("Others" to rest)) else emptyList()
            list.mapIndexed { i, (k, v) ->
                Slice(k, v.first, v.second, v.second.toFloat() / whole, lerp(pickColor, Color.White, 0.55f * i / list.size.coerceAtLeast(1)))
            }
        }
    }
    val level = if (pick == null) 0 else if (merchantKey == null) 1 else 2
    var hover by remember(pick?.id) { mutableStateOf<Int?>(null) }
    val active = hover ?: slices.indexOfFirst { it.key.isNotEmpty() && it.key == merchantKey }.takeIf { it >= 0 }
    val ring = growIn(sweepKey to (pick == null), 1000)
    val open = growIn(pick?.id, 1000)
    val pickStart = remember(pick, categories) {
        var acc = 0f
        for (c in categories) { if (c.id == pick?.id) break; acc += c.paise.toFloat() / total * 360f }
        -90f + acc
    }
    // Each slice eases out from the ring when touched or picked, and back when let go
    val pops = key(pick?.id, slices.size) {
        slices.indices.map { i -> animateFloatAsState(if (active == i) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "pop").value }
    }
    fun tapSlice(i: Int) {
        val s = slices.getOrNull(i) ?: return
        when {
            pick == null -> onCategory(s.key)
            s.key.isNotEmpty() -> onMerchant(s.key)
        }
    }

    Panel(modifier = modifier.testTag("report_categories")) {
        AnimatedContent(
            targetState = level to (pick?.name to slices.firstOrNull { it.key.isNotEmpty() && it.key == merchantKey }?.name),
            transitionSpec = {
                val deeper = targetState.first > initialState.first
                (fadeIn(tween(300)) + slideInHorizontally(tween(380, easing = SettleEasing)) { if (deeper) it / 3 else -it / 3 }) togetherWith fadeOut(tween(150))
            },
            label = "ring_title",
        ) { (lv, names) ->
            val (catName, merchantName) = names
            if (lv == 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text("Slide round the ring · tap a slice to open it", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    headerAction?.invoke()
                }
            } else {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable { if (lv == 2) pickedMerchant?.let(onMerchant) else pickedCategory?.let(onCategory) }
                        .padding(end = 8.dp).testTag("ring_up"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Ic.back, contentDescription = if (lv == 2) "Back to ${catName ?: "the category"}" else "Back to all categories",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text((if (lv == 2) merchantName else catName) ?: "", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (lv == 2) "$catName › payments · back to $catName" else "By merchant · tap one for its payments",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Gap(12.dp)
        if (pick != null && scene != null) {
            scene(pick.id)
            Gap(12.dp)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(220.dp)
                    .pointerInput(slices) {
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val outer = size.width / 2f
                        fun sliceAt(p: Offset): Int? {
                            if (slices.isEmpty()) return null
                            val angle = ((Math.toDegrees(atan2((p.y - centre.y).toDouble(), (p.x - centre.x).toDouble())) + 90 + 360) % 360).toFloat()
                            var acc = 0f
                            slices.forEachIndexed { i, s -> acc += s.share * 360f; if (angle <= acc) return i }
                            return slices.lastIndex
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d = hypot(down.position.x - centre.x, down.position.y - centre.y)
                            if (d > outer || d < outer * 0.42f) return@awaitEachGesture   // only the ring itself
                            hover = sliceAt(down.position)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (moved) {
                                    ch.consume()   // sliding round the ring doesn't scroll the page
                                    val i = sliceAt(ch.position)
                                    if (i != hover) { hover = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                }
                            }
                            val last = hover
                            hover = null
                            if (!moved && last != null) tapSlice(last)
                        }
                    }
                    .semantics {
                        contentDescription = (if (pick == null) "Spending by category: " else "${pick.name} by merchant: ") +
                            slices.joinToString { "${it.name} ${(it.share * 100).toInt()}%" }
                    }
                    .testTag("category_donut"),
            ) {
                val stroke = 24.dp.toPx()
                val popOut = 9.dp.toPx()
                val inset = stroke / 2 + popOut + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val tl = Offset(inset, inset)
                val dim = active != null
                drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
                fun slice(color: Color, start: Float, sweep: Float, pop: Float) {
                    // A popped slice moves outward along its middle and grows a little thicker
                    val mid = Math.toRadians((start + sweep / 2).toDouble())
                    val shift = Offset((kotlin.math.cos(mid) * popOut * pop).toFloat(), (kotlin.math.sin(mid) * popOut * pop).toFloat())
                    drawArc(color, start, sweep, false, tl + shift, arcSize, style = Stroke(stroke * (1f + 0.3f * pop)))
                }
                if (pick == null) {
                    // The ring sweeps round clockwise, slice after slice, like a fitness ring closing
                    val shown = 360f * ring
                    var start = -90f
                    slices.forEachIndexed { i, s ->
                        val sweep = s.share * 360f
                        val visible = (shown - (start + 90f)).coerceIn(0f, sweep)
                        val gap = if (slices.size > 1) 1.2f else 0f
                        if (visible > gap) slice(s.color.copy(alpha = if (dim && active != i) 0.35f else 1f), start + gap / 2, (visible - gap).coerceAtLeast(0.5f), pops.getOrElse(i) { 0f })
                        start += sweep
                    }
                } else {
                    // 1: the opened slice swings round and fills the ring while the others fade
                    val e = SettleEasing.transform((open / 0.45f).coerceIn(0f, 1f))
                    if (e < 1f) {
                        var start = -90f
                        categories.forEach { c ->
                            val sweep = c.paise.toFloat() / total * 360f
                            if (c.id != pick.id) drawArc(c.color.copy(alpha = 0.9f * (1f - e)), start + 0.6f, (sweep - 1.2f).coerceAtLeast(0.5f), false, tl, arcSize, style = Stroke(stroke))
                            start += sweep
                        }
                    }
                    val pickSweep = pickShare * 360f
                    if (open < 1f) drawArc(
                        pickColor.copy(alpha = if (open < 0.45f) 1f else 1f - (open - 0.45f) / 0.55f),
                        pickStart + (-90f - pickStart) * e, pickSweep + (360f - pickSweep) * e, false, tl, arcSize, style = Stroke(stroke),
                    )
                    // 2: then its merchants sweep in, each a lighter shade of the category's colour
                    if (open > 0.45f) {
                        val shown = 360f * SettleEasing.transform(((open - 0.45f) / 0.55f).coerceIn(0f, 1f))
                        var at = -90f
                        slices.forEachIndexed { i, s ->
                            val sweep = s.share * 360f
                            val visible = (shown - (at + 90f)).coerceIn(0f, sweep)
                            if (visible > 1.2f) slice(s.color.copy(alpha = if (dim && active != i) 0.4f else 1f), at + 0.6f, visible - 1.2f, pops.getOrElse(i) { 0f })
                            at += sweep
                        }
                    }
                }
            }
            // The centre answers the finger: the slice being touched, else the level's total
            val focus = active?.let { slices.getOrNull(it) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(118.dp).testTag("ring_centre")) {
                Text(focus?.name ?: pick?.name ?: "Total", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Money.formatInr(animatedPaise(focus?.paise ?: pick?.paise ?: categories.sumOf { it.paise }, 450)), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        focus != null -> "${(focus.share * 100).toInt()}%" + if (pick != null) " of ${pick.name}" else " of spending"
                        pick != null -> "${(pickShare * 100).toInt()}% of spending"
                        else -> "${slices.size} ${if (slices.size == 1) "category" else "categories"}"
                    },
                    style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center,
                )
            }
        }
        Gap(8.dp)
        when (level) {
            0 -> categories.forEachIndexed { i, c ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { onCategory(c.id) }
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                        .testTag("cat_${c.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(c.color))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            c.note ?: "${(c.paise.toFloat() / total * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall, color = if (c.warn) sc.overFg else muted,
                        )
                    }
                    Text(Money.formatInr(c.paise), style = MaterialTheme.typography.titleSmall)
                    Icon(Ic.chevR, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                }
            }
            1 -> slices.forEachIndexed { i, s ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .then(if (s.key.isNotEmpty()) Modifier.clickable { onMerchant(s.key) } else Modifier)
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                        .testTag("ring_merchant_${s.key.ifEmpty { "others" }}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(s.color))
                    Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${(s.share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = muted)
                    Text(Money.formatInr(s.paise), style = MaterialTheme.typography.titleSmall)
                    if (s.key.isNotEmpty()) Icon(Ic.chevR, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                }
            }
            else -> {
                // The merchant's payments, newest first; tap one for its details
                val shown = payments.take(20)
                Column(Modifier.fillMaxWidth().testTag("ring_payments")) {
                    Text(
                        "$paymentCount ${if (paymentCount == 1) "payment" else "payments"}" +
                            (if (paymentCount > shown.size) " · newest ${shown.size} here" + (if (moreBelow) ", all of them below" else "") else ""),
                        style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                    shown.forEachIndexed { i, t ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onPayment(t) }
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                                .testTag("ring_payment_${t.id}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MerchantAvatar(appIcons, t.merchantPackage, t.merchantName, catMap[t.categoryId], 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(dayText(t), style = MaterialTheme.typography.titleSmall)
                                Text(t.accountLabel ?: t.note ?: "", style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text((if (t.isMoneyIn) "+" else "−") + Money.formatInr(t.amountPaise), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}
