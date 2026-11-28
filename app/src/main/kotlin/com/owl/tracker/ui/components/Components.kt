package com.owl.tracker.ui.components

import com.owl.tracker.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owl.tracker.AppIcons
import com.owl.tracker.data.Category
import com.owl.tracker.R
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.clay
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import com.owl.tracker.ui.theme.LocalStatusColors
import com.owl.tracker.core.BudgetState
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Institutions
import com.owl.tracker.core.ShopKinds

/** A card in the clay look: moulded, lit from the top left, casting a soft warm shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(28.dp), modifier.fillMaxWidth(), elevation = 8.dp) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** The app's owl (the launcher's), on its own: use this wherever the owl appears, never the 🦉 emoji. */
@Composable
fun OwlMark(size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(com.owl.tracker.R.drawable.owl_mascot), contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** A picture given as an emoji (a badge's, an event's): the app's own owl for 🦉, otherwise the emoji itself. */
@Composable
fun Glyph(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    if (emoji == OWL) OwlMark(size * 1.15f, modifier)
    else Text(emoji, fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { size.toSp() }, modifier = modifier)
}

const val OWL = "🦉"

/** The app's badge, as on the launcher: the owl and its coin on ripe mango. */
@Composable
fun OwlBadge(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    // Always mango, in the dark theme too, like the launcher icon
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFF1B3), Color(0xFFFFC85C), Color(0xFFFF9A3D)))),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.owl.tracker.R.drawable.owl_mascot), contentDescription = null,
            modifier = Modifier.size(size * 0.8f),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Ic.back, contentDescription = tr("Back")) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** A small clay tile holding a line icon. [background] may be see-through: it's laid over the card's colour. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp) {
    val clay = com.owl.tracker.ui.theme.LocalBrand.current.clay
    Box(
        Modifier.size(size).clay(RoundedCornerShape(size * 0.32f), clay, background.compositeOver(clay.surface), elevation = if (size >= 32.dp) 3.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * The soft 3D pictures for the category icons: Microsoft's Fluent Emoji (MIT, assets/licenses/fluent-emoji.txt),
 * chosen 22 Nov 2026 ("Option 4") in place of the glossy pictures. Every built-in icon has one.
 */
private val SOFT3D = mapOf(
    "tag" to R.drawable.soft3d_tag, "food" to R.drawable.soft3d_food, "coffee" to R.drawable.soft3d_coffee,
    "delivery" to R.drawable.soft3d_delivery, "groceries" to R.drawable.soft3d_groceries,
    "shopping" to R.drawable.soft3d_shopping, "fashion" to R.drawable.soft3d_fashion,
    "electronics" to R.drawable.soft3d_electronics, "furniture" to R.drawable.soft3d_furniture,
    "beauty" to R.drawable.soft3d_beauty, "jewellery" to R.drawable.soft3d_jewellery,
    "transport" to R.drawable.soft3d_transport, "commute" to R.drawable.soft3d_commute, "cabs" to R.drawable.soft3d_cabs,
    "car" to R.drawable.soft3d_car, "metro" to R.drawable.soft3d_metro, "fuel" to R.drawable.soft3d_fuel,
    "kids" to R.drawable.soft3d_kids, "baby" to R.drawable.soft3d_baby, "daycare" to R.drawable.soft3d_daycare,
    "school" to R.drawable.soft3d_school, "kids_sports" to R.drawable.soft3d_kids_sports,
    "kids_health" to R.drawable.soft3d_kids_health, "toys" to R.drawable.soft3d_toys,
    "kids_clothes" to R.drawable.soft3d_kids_clothes, "bills" to R.drawable.soft3d_bills,
    "utilities" to R.drawable.soft3d_utilities, "recharge" to R.drawable.soft3d_recharge,
    "subscriptions" to R.drawable.soft3d_subscriptions, "home_help" to R.drawable.soft3d_home_help,
    "insurance" to R.drawable.soft3d_insurance, "health" to R.drawable.soft3d_health,
    "medicines" to R.drawable.soft3d_medicines, "doctor" to R.drawable.soft3d_doctor,
    "entertainment" to R.drawable.soft3d_entertainment, "sports" to R.drawable.soft3d_sports,
    "travel" to R.drawable.soft3d_travel, "holiday" to R.drawable.soft3d_holiday,
    "education" to R.drawable.soft3d_education, "fun" to R.drawable.soft3d_fun, "rent" to R.drawable.soft3d_rent,
    "emi" to R.drawable.soft3d_emi, "investments" to R.drawable.soft3d_investments, "cash" to R.drawable.soft3d_cash,
    "income" to R.drawable.soft3d_income, "transfer" to R.drawable.soft3d_transfer,
    "wallet" to R.drawable.soft3d_wallet, "coins" to R.drawable.soft3d_coins, "gift" to R.drawable.soft3d_gift,
    "temple" to R.drawable.soft3d_temple, "pet" to R.drawable.soft3d_pet, "others" to R.drawable.soft3d_others,
)

/** The 3D picture for a category icon, if there is one. */
fun categoryArt(icon: String?): Int? = SOFT3D[icon ?: "others"]

/**
 * A category's tile: its 3D picture (a bowl of noodles, an auto rickshaw, a metro train…) on a soft tile that fades
 * from the card colour into the category's, gently floating. Small tiles, and icons with no picture, keep the line
 * icon. The float is off with "Remove animations", and staggered by icon so a list doesn't bob in step.
 */
@Composable
fun CategoryTile(category: Category?, size: Dp = 40.dp) {
    val color = Color(category?.color ?: 0xFF7F8682)
    val icon = category?.icon ?: "others"
    val art = SOFT3D[icon]
    if (art == null || size < 28.dp) {
        IconTile(Ic.forCategory(icon), color, color.copy(alpha = 0.13f), size)
        return
    }
    val surface = MaterialTheme.colorScheme.surface
    val float = if (androidx.compose.ui.platform.LocalInspectionMode.current) 0f else {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "tile")
        t.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable<Float>(
                androidx.compose.animation.core.tween<Float>(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
                androidx.compose.animation.core.StartOffset((icon.hashCode() and 0x7fffffff) % 1500),
            ),
            label = "float",
        ).value
    }
    Box(
        Modifier.size(size)
            .shadow(3.dp, RoundedCornerShape(size * 0.3f), clip = false, ambientColor = color.copy(alpha = 0.3f), spotColor = color.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(size * 0.3f))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(surface, color.copy(alpha = 0.16f).compositeOver(surface)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(art), contentDescription = null,
            modifier = Modifier.size(size * 0.72f).graphicsLayer { translationY = -float * size.toPx() * 0.05f },
        )
    }
}

/**
 * The merchant's real app icon when that app is installed; otherwise, for a shop with no app, an icon for the
 * kind of shop its name says (jeweller, opticals, bakery…); otherwise the category icon; otherwise the
 * merchant's initials. Money received never gets a shop icon: a salary from a pharma company is not a pharmacy.
 */
@Composable
fun MerchantAvatar(appIcons: AppIcons, packageName: String?, merchantName: String, category: Category?, size: Dp = 42.dp) {
    val bitmap = remember(packageName) { appIcons.get(packageName)?.asImageBitmap() }
    val shop = remember(merchantName, category?.id) {
        if (category?.id == Cat.INCOME) null else ShopKinds.of(merchantName)
    }
    when {
        bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)))
        shop != null -> {
            val color = Color(category?.color ?: 0xFF7F8682)
            Box(Modifier.testTag("shop_icon_${shop.name.lowercase()}")) {
                IconTile(Ic.forShop(shop), color, color.copy(alpha = 0.13f), size)
            }
        }
        category != null && category.id != Cat.OTHERS && category.id != Cat.INCOME -> CategoryTile(category, size)
        else -> {
            // A badge of the merchant's initials, in a colour that's always the same for that name.
            val tint = MONOGRAM_COLORS[(merchantName.lowercase().hashCode() and 0x7fffffff) % MONOGRAM_COLORS.size]
            Box(
                Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials(merchantName),
                    style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    color = tint,
                )
            }
        }
    }
}

/** A bank, card or wallet: its own app icon when one of its apps is installed, otherwise a plain bank tile. */
@Composable
fun BankIcon(appIcons: AppIcons, institutionId: String?, size: Dp = 40.dp) {
    val bitmap = remember(institutionId) { appIcons.get(appIcons.bankApp(institutionId))?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)))
    } else {
        IconTile(
            if (institutionId == Institutions.UNRECOGNISED) Ic.info else Ic.bank,
            MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant, size,
        )
    }
}

private val MONOGRAM_COLORS = listOf(
    Color(0xFF2A6FB5), Color(0xFF7552C4), Color(0xFFC23F73), Color(0xFF1F8F86),
    Color(0xFFD9622B), Color(0xFF3F8A3A), Color(0xFF9E740C), Color(0xFF5866B8),
)

/** "Ramesh Kumar" → RK, "zomato" → Z, "7-Eleven" → 7E. */
fun initials(name: String): String {
    val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String? = null, iconTint: Color? = null, iconBg: Color? = null, titleColor: Color = Color.Unspecified, leading: @Composable (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading() else IconTile(icon, iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant, iconBg ?: MaterialTheme.colorScheme.surfaceVariant, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A locked switch looks locked: greyed, and a tap does nothing
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

@Composable
fun StatusPill(state: BudgetState, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    val (label, icon) = when (state) {
        BudgetState.ON_TRACK -> tr("On track") to Ic.check
        BudgetState.HEADS_UP -> tr("Heads-up") to Ic.alert
        BudgetState.OVER -> tr("Over budget") to Ic.alert
    }
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(sc.bg(state)).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = sc.fg(state), modifier = Modifier.size(14.dp))
        Text(label, color = sc.fg(state), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/**
 * Budget ring. Past 100% a second, red inner lap shows how far over you are,
 * so overspend is visible at a glance without relying on colour alone (the centre text says it too).
 */
@Composable
fun BudgetRing(fraction: Double, state: BudgetState, centerTop: String, centerBottom: String, size: Dp = 136.dp) {
    val sc = LocalStatusColors.current
    // On track is green in every palette (never the palette's own colour, which could read as a warning)
    val main = sc.ring(state, sc.ring)
    val track = sc.track
    val over = sc.overFg
    Box(Modifier.size(size).clearAndSetSemantics { contentDescription = "$centerTop $centerBottom" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.095f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val sweep = (fraction.coerceIn(0.0, 1.0) * 360).toFloat()
            if (sweep > 0f) drawArc(main, -90f, sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 1.0) {
                val s2 = stroke * 0.55f
                val inner = stroke + s2 * 1.6f
                val innerSize = Size(this.size.width - inner * 2 + s2, this.size.height - inner * 2 + s2)
                val innerSweep = ((fraction - 1.0).coerceIn(0.0, 1.0) * 360).toFloat()
                drawArc(over, -90f, innerSweep, false, topLeft = Offset(inner - s2 / 2, inner - s2 / 2), size = innerSize, style = Stroke(s2, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.headlineSmall)
            Text(centerBottom, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class DonutSlice(val value: Float, val color: Color)

@Composable
fun Donut(slices: List<DonutSlice>, center: String, size: Dp = 116.dp) {
    val track = LocalStatusColors.current.track
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.14f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total > 0f) {
                var start = -90f
                slices.forEach { s ->
                    val sweep = s.value / total * 360f
                    drawArc(s.color, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        Text(center, style = MaterialTheme.typography.titleSmall)
    }
}

/** A progress bar with a thin marker showing where an even pace would be today. */
@Composable
fun PaceBar(fraction: Double, pace: Double, color: Color, height: Dp = 10.dp) {
    val track = LocalStatusColors.current.track
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(height + 8.dp)) {
        val h = height.toPx()
        val top = (this.size.height - h) / 2
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(this.size.width, h), cornerRadius = r)
        val w = (fraction.coerceIn(0.0, 1.0) * this.size.width).toFloat()
        if (w > 0f) drawRoundRect(color, topLeft = Offset(0f, top), size = Size(maxOf(w, h), h), cornerRadius = r)
        val x = (pace.coerceIn(0.0, 1.0) * this.size.width).toFloat()
        drawLine(marker, Offset(x, 0f), Offset(x, this.size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun Checkmark(checked: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
            .background(if (checked) c else Color.Transparent)
            .border(2.dp, if (checked) c else MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Ic.check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun InfoNote(text: String, icon: ImageVector = Ic.info, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(container).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun OneLine(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium, color: Color = Color.Unspecified) =
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
