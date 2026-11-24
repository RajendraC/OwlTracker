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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.owl.tracker.AppIcons
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.data.Txn
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalBrand
import com.owl.tracker.ui.theme.LocalStatusColors
import com.owl.tracker.ui.theme.LocalDarkTheme
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A category on the ring. [note] replaces the share under its name in the list; [warn] colours it. [inside]: for a
 * group, what it's made of ("Phones & Electronics ₹19k · Not sorted ₹5.2k"), shown under the note.
 */
data class RingCategory(
    val id: String, val name: String, val color: Color, val paise: Long, val note: String? = null, val warn: Boolean = false,
    val inside: String? = null,
)

/** A merchant inside the category opened. */
data class RingMerchant(val key: String, val name: String, val paise: Long)

/**
 * Home's top level: the ring lies on the card in 3D with the owl in the middle, saying how the [month] is going,
 * or a category once its slice is tapped ([byCategory], by category id).
 */
/** [spent] and [budget]: the period's (month, week or day), for the budget track the bubbles ride on. */
data class RingMoods(
    val month: OwlSay, val byCategory: Map<String, OwlSay>, val use: Map<String, Float> = emptyMap(),
    val spent: Long = 0, val budget: Long = 0,
)

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
    /** With these, the top level is the 3D ring with the owl: a tap turns a slice to the front, a second tap opens it. */
    moods: RingMoods? = null,
    /** Show each category's glossy picture in the list instead of a dot. */
    tiles: Boolean = false,
    /** Home, this month: the months behind it, as small bars on every row and a 6 months tab. */
    trends: RingTrends? = null,
    /**
     * Inside each group in [categories]: its categories, biggest first, and the group itself (same id) for payments not
     * sorted yet. With [pickedGroup] set the ring shows that group's categories; [pickedCategory] is then one of them.
     */
    groups: Map<String, List<RingCategory>> = emptyMap(),
    pickedGroup: String? = null,
    /** The arrow at the top: up one level (the screen keeps track of where it is). Without it, [onCategory] toggles. */
    onUp: (() -> Unit)? = null,
    /** "Sort" on a group's payments not sorted yet. */
    onSort: ((groupId: String) -> Unit)? = null,
    /** Home, the whole month: a Paid by tab, the spending by card and account. */
    paidBy: PaidByTab? = null,
    /**
     * Round 28 B6: the owl on the left and a card beside it, saying how the month is doing or, after a tap on a
     * category, showing that category's picture, amount and how it's doing. Since 1.9.17 the categories circle the
     * owl as bubbles (OrbitOwlStage) instead of the 3D ring.
     */
    sideBySide: Boolean = false,
) {
    val sc = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceVariant
    val haptics = LocalHapticFeedback.current
    val total = categories.sumOf { it.paise }.coerceAtLeast(1)
    // A group open: its categories are the ring; one of them may be open in turn
    val groupKids = pickedGroup?.let { groups[it] }?.takeIf { it.isNotEmpty() }
    val groupName = pickedGroup?.let { id -> categories.firstOrNull { it.id == id }?.name }
    val inGroup = groupKids != null && pickedCategory == null
    // The ring the opened slice came from: the month's, or the open group's
    val parentList = if (groupKids != null && pickedCategory != null) groupKids else categories
    val parentTotal = parentList.sumOf { it.paise }.coerceAtLeast(1)
    val pick = when {
        pickedCategory != null -> parentList.firstOrNull { it.id == pickedCategory }
        groupKids != null -> categories.firstOrNull { it.id == pickedGroup }
        else -> null
    }
    val merchantKey = pickedMerchant?.takeIf { pick != null && !inGroup }
    val pickColor = pick?.color ?: MaterialTheme.colorScheme.primary
    val pickShare = (pick?.paise ?: 0L).toFloat() / (if (inGroup) total else parentTotal)

    // Level 0: categories. Levels 1 and 2: the opened category's merchants (2 has one of them picked).
    // A group open is level 1 too, with its categories in their own colours.
    val slices: List<Slice> = remember(pick, categories, merchants, merchantKey, inGroup, groupKids) {
        if (pick == null) {
            categories.map { Slice(it.id, it.name, it.paise, it.paise.toFloat() / total, it.color) }
        } else if (inGroup && groupKids != null) {
            val whole = groupKids.sumOf { it.paise }.coerceAtLeast(1)
            groupKids.map { Slice(it.id, it.name, it.paise, it.paise.toFloat() / whole, it.color) }
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
    // The row whose months are open, and the category lit up in 6 months
    var openTrend by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var sixPick by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val paidShowing = paidBy != null && paidBy.showing && level == 0
    val six = trends != null && trends.sixMonths && level == 0 && !paidShowing
    // The 3D ring's chosen slice (turned to the front, the owl talking about it), and a count to replay the owl's act
    var chosen by remember(sweepKey) { mutableStateOf<String?>(null) }
    var act by remember { mutableIntStateOf(0) }
    val chosenHere = chosen?.takeIf { id -> categories.any { it.id == id } }
    var hover by remember(pick?.id) { mutableStateOf<Int?>(null) }
    val active = hover ?: slices.indexOfFirst { it.key.isNotEmpty() && it.key == merchantKey }.takeIf { it >= 0 }
    val ring = growIn(sweepKey to (pick == null), 1000)
    val open = growIn(pick?.id, 1000)
    val pickStart = remember(pick, parentList) {
        var acc = 0f
        for (c in parentList) { if (c.id == pick?.id) break; acc += c.paise.toFloat() / parentTotal * 360f }
        -90f + acc
    }
    // Each slice eases out from the ring when touched or picked, and back when let go
    val pops = key(pick?.id, slices.size) {
        slices.indices.map { i -> animateFloatAsState(if (active == i) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "pop").value }
    }
    fun tapSlice(i: Int) {
        val s = slices.getOrNull(i) ?: return
        when {
            pick == null || inGroup -> onCategory(s.key)
            s.key.isNotEmpty() -> onMerchant(s.key)
        }
    }

    Panel(modifier = modifier.testTag("report_categories")) {
        AnimatedContent(
            targetState = (level + if (pickedGroup != null && !inGroup && level > 0) 1 else 0) to (pick?.name to slices.firstOrNull { it.key.isNotEmpty() && it.key == merchantKey }?.name),
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
                        Text(
                            if (paidShowing) "By card and account · tap one to see what it was used for"
                            else if (moods != null && sideBySide) "Tap a bubble to see how it's doing · tap again to open it · swipe to spin"
                            else if (moods != null) "Tap a slice to see how it's doing · tap again to open it" else "Slide round the ring · tap a slice to open it",
                            style = MaterialTheme.typography.bodySmall, color = muted,
                        )
                    }
                    headerAction?.invoke()
                }
            } else {
                val upTo = if (merchantName != null) catName else if (inGroup || pickedGroup == null) null else groupName
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable {
                            when {
                                merchantName != null -> pickedMerchant?.let(onMerchant)
                                onUp != null -> onUp()
                                else -> pickedCategory?.let(onCategory)
                            }
                        }
                        .padding(end = 8.dp).testTag("ring_up"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Ic.back, contentDescription = if (upTo != null) "Back to $upTo" else "Back to all categories",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text((merchantName ?: catName) ?: "", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                merchantName != null -> "$catName › payments · back to $catName"
                                inGroup -> "By category · tap one for its merchants"
                                else -> "By merchant · tap one for its payments" + (groupName?.let { " · back to $it" } ?: "")
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Gap(12.dp)
        if ((trends != null || paidBy != null) && level == 0 && !(paidShowing && paidBy?.opened == true)) {
            WhereSwitch(if (paidShowing) 2 else if (six) 1 else 0, hasSix = trends != null, hasPaid = paidBy != null) { v ->
                when (v) {
                    2 -> paidBy?.onShow(true)
                    1 -> trends?.onSixMonths?.invoke(true)
                    else -> if (trends != null) trends.onSixMonths(false) else paidBy?.onShow(false)
                }
            }
            Gap(12.dp)
        }
        if (paidShowing && paidBy != null) {
            paidBy.content()
            return@Panel
        }
        if (six && trends != null) {
            // This month's categories first, then any others the months behind it had
            val order = remember(categories, catMap, trends.history) {
                val now = categories.mapNotNull { catMap[it.id] }
                now + catMap.values.filter { c -> c.countsAsSpend && !c.archived && c.parent == null && now.none { it.id == c.id } }
                    .sortedByDescending { trends.history.usual(it.id) }
            }
            SixMonthsView(trends.history, order, trends.monthBudget, sixPick) { sixPick = it }
            return@Panel
        }
        if (pick != null && scene != null) {
            scene(pick.id)
            Gap(12.dp)
        }
        if (level == 0 && moods != null && sideBySide) {
            // Option B (24 Nov 2026): one piece. How the period is going across the top, then the orbit, whose track
            // is the period's budget filling up; a tap on a bubble swaps the top line to that category.
            val say = chosenHere?.let { moods.byCategory[it] } ?: moods.month
            TopLine(
                month = moods.month, spent = moods.spent, budget = moods.budget,
                category = chosenHere?.let { id -> categories.firstOrNull { it.id == id } },
                share = chosenHere?.let { id -> categories.firstOrNull { it.id == id }?.paise?.times(100)?.div(total) },
                onOpen = chosenHere?.let { id -> { onCategory(id) } },
            )
            OrbitOwlStage(
                items = categories.map { OrbitItem(it.id, it.name, it.color, it.paise, catMap[it.id]?.icon, moods.use[it.id]) },
                say = say,
                picked = chosenHere,
                actKey = act,
                sweepKey = sweepKey,
                onTap = { hit ->
                    when (hit) {
                        is RingHit.Slice -> if (chosenHere == hit.id) onCategory(hit.id) else chosen = hit.id
                        RingHit.Middle -> if (chosenHere != null) chosen = null else act++
                        RingHit.Outside -> Unit
                    }
                },
                budgetUsed = moods.budget.takeIf { it > 0 }?.let { moods.spent.toFloat() / it },
                wide = true,
            )
        } else if (level == 0 && moods != null) {
            RingOwlStage(
                slices = categories.map { Ring3DSlice(it.id, it.color, it.paise.toFloat() / total) },
                description = "Spending by category: " + categories.joinToString { "${it.name} ${it.paise * 100 / total}%" },
                say = chosenHere?.let { moods.byCategory[it] } ?: moods.month,
                picked = chosenHere,
                actKey = act,
                sweepKey = sweepKey,
                onTap = { hit ->
                    when (hit) {
                        is RingHit.Slice -> if (chosenHere == hit.id) onCategory(hit.id) else chosen = hit.id
                        RingHit.Middle -> if (chosenHere != null) chosen = null else act++
                        RingHit.Outside -> Unit
                    }
                },
                onOpen = chosenHere?.let { id -> { onCategory(id) } },
            )
        } else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                        contentDescription = (if (pick == null) "Spending by category: " else if (inGroup) "${pick.name} by category: " else "${pick.name} by merchant: ") +
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
                        val from = if (inGroup) categories else parentList
                        val fromTotal = if (inGroup) total else parentTotal
                        from.forEach { c ->
                            val sweep = c.paise.toFloat() / fromTotal * 360f
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
            // The hole is a fixed size: with big text its words grow a little, then get smaller to fit
            CapFontScale(1.25f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(118.dp).testTag("ring_centre")) {
                    FitText(focus?.name ?: pick?.name ?: "Total", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minScale = 0.75f)
                    FitText(Money.formatInr(animatedPaise(focus?.paise ?: pick?.paise ?: categories.sumOf { it.paise }, 450)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, minScale = 0.6f)
                    FitText(
                        when {
                            focus != null -> "${(focus.share * 100).toInt()}%" + if (pick != null) " of ${pick.name}" else " of spending"
                            pick != null -> "${(pickShare * 100).toInt()}% of " + (if (!inGroup && groupName != null) groupName else "spending")
                            else -> "${slices.size} ${if (slices.size == 1) "category" else "categories"}"
                        },
                        style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center, maxLines = 2, minScale = 0.75f,
                    )
                }
            }
        }
        Gap(8.dp)
        when (level) {
            0 -> categories.forEachIndexed { i, c ->
                if (i > 0) Divider()
                val on = moods != null && chosenHere == c.id
                val h = trends?.history
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(if (tiles) 18.dp else 10.dp))
                        .then(if (on) Modifier.background(LocalBrand.current.clay.pressed) else Modifier)
                        .clickable { onCategory(c.id) }
                        .padding(vertical = if (tiles) 8.dp else 10.dp, horizontal = 6.dp)
                        .testTag("cat_${c.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tiles) 12.dp else 10.dp),
                ) {
                    if (tiles) CategoryTile(catMap[c.id], 40.dp) else Box(Modifier.size(12.dp).clip(CircleShape).background(c.color))
                    // The amount beside the name when there's room, else under it
                    SideOrStacked(
                        Modifier.weight(1f),
                        start = {
                            Column {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    c.note ?: "${(c.paise.toFloat() / total * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall, color = if (c.warn) sc.overFg else muted,
                                )
                            }
                        },
                        end = { Text(Money.formatInr(c.paise), style = MaterialTheme.typography.titleSmall) },
                    )
                    Icon(Ic.chevR, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                }
                // A group: what it's made of, under the whole row so big text has the width
                if (c.inside != null) Text(
                    c.inside, style = MaterialTheme.typography.bodySmall, color = muted,
                    modifier = Modifier.fillMaxWidth().padding(start = if (tiles) 58.dp else 28.dp, end = 6.dp, bottom = 4.dp).testTag("ring_inside_${c.id}"),
                )
                // Its months, small, and which way it's going, under the whole row (with big text, beside the amount
                // squeezed the words one to a line): tap for the amounts
                if (h != null) RowTrendStrip(h, c.id, c.name, c.color, openTrend == c.id) { openTrend = if (openTrend == c.id) null else c.id }
                if (h != null && openTrend == c.id) RowTrendOpen(h, c.id, c.color, trends?.budgets?.get(c.id))
            }
            1 -> if (inGroup && groupKids != null) groupKids.forEachIndexed { i, c ->
                if (i > 0) Divider()
                val unsorted = c.id == pickedGroup
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .clickable { onCategory(c.id) }
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .testTag(if (unsorted) "group_unsorted_${c.id}" else "group_cat_${c.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (unsorted) NotSortedTile(36.dp) else CategoryTile(catMap[c.id], 36.dp)
                    SideOrStacked(
                        Modifier.weight(1f),
                        start = {
                            Column {
                                Text(if (unsorted) "Not sorted yet" else c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    c.note ?: "${(c.paise.toFloat() / (pick?.paise ?: 1L).coerceAtLeast(1L) * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        end = { Text(Money.formatInr(c.paise), style = MaterialTheme.typography.titleSmall) },
                    )
                    Icon(Ic.chevR, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                }
                // Sort: under the row, so the name and amount keep their width (big text)
                if (unsorted && onSort != null) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onSort(c.id) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(start = 54.dp, bottom = 8.dp).testTag("sort_${c.id}"),
                    ) { Text("Sort: pick what it was") }
                }
            } else slices.forEachIndexed { i, s ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .then(if (s.key.isNotEmpty()) Modifier.clickable { onMerchant(s.key) } else Modifier)
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                        .testTag("ring_merchant_${s.key.ifEmpty { "others" }}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(s.color))
                    // The share and amount beside the name when there's room, else under it (big text, a lakh)
                    SideOrStacked(
                        Modifier.weight(1f),
                        start = { Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        end = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${(s.share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = muted)
                                Text(Money.formatInr(s.paise), style = MaterialTheme.typography.titleSmall)
                            }
                        },
                    )
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

/** The tile for payments in a group that aren't sorted into one of its categories yet. */
@Composable
fun NotSortedTile(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Above the orbit: the period's mood, what went and against what budget, and what's left; after a tap on a bubble,
 * that category's amount, share and how it's doing against its own budget, with Open.
 */
@Composable
private fun TopLine(month: OwlSay, spent: Long, budget: Long, category: RingCategory?, share: Long?, onOpen: (() -> Unit)?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sc = LocalStatusColors.current
    val xb = androidx.compose.ui.text.font.FontWeight.ExtraBold
    AnimatedContent(category?.id, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "top_line") { id ->
        val c = category?.takeIf { it.id == id }
        // The two halves wrap onto two lines when the text is big, never breaking inside an amount
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().padding(top = 10.dp).testTag(if (c == null) "top_month" else "top_category"),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.padding(end = 12.dp)) {
                if (c == null) {
                    Text(month.mood.says, color = month.mood.ink, style = MaterialTheme.typography.labelLarge, fontWeight = xb)
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(MaterialTheme.typography.headlineSmall.toSpanStyle().copy(fontWeight = xb)) { append(Money.formatInr(animatedPaise(spent, 450))) }
                            if (budget > 0) withStyle(MaterialTheme.typography.bodyMedium.toSpanStyle().copy(color = muted)) { append(" of\u00A0" + Money.formatInr(budget)) }
                        },
                        modifier = Modifier.testTag("side_amount"),
                    )
                    if (budget <= 0) Text(month.line, style = MaterialTheme.typography.bodySmall, color = muted)
                } else {
                    // The category's note already starts with its share; say it once
                    Text(c.name + (share?.takeIf { c.note?.startsWith("$it%") != true }?.let { " · $it%" } ?: ""), style = MaterialTheme.typography.labelLarge, fontWeight = xb, color = muted)
                    Text(Money.formatInr(c.paise), style = MaterialTheme.typography.headlineSmall, fontWeight = xb, modifier = Modifier.testTag("side_amount"))
                    c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (c.warn) sc.overFg else muted) }
                }
            }
            if (c != null && onOpen != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Open ${c.name}", onClick = onOpen)
                        .background(MaterialTheme.colorScheme.primary).heightIn(min = 40.dp).padding(start = 16.dp, end = 10.dp).testTag("owl_open"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Open", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = xb)
                    Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            } else if (c == null && budget > 0) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 3.dp)) {
                    val left = budget - spent
                    Text(
                        if (left >= 0) "${Money.formatInr(left)} left" else "${Money.formatInr(-left)} over",
                        color = if (left >= 0) month.mood.ink else sc.overFg, style = MaterialTheme.typography.labelLarge, fontWeight = xb,
                    )
                    Text(month.title.substringBefore(" · ").lowercase(), style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
    }
}
