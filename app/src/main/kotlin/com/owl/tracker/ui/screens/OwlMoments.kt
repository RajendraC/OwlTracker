package com.owl.tracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owl.tracker.ui.Celebration
import com.owl.tracker.ui.Platform
import com.owl.tracker.ui.SharePicture
import com.owl.tracker.ui.UiMessage
import com.owl.tracker.ui.components.MoodOwl
import com.owl.tracker.ui.components.OwlMood
import kotlinx.coroutines.launch

private val Cocoa = Color(0xFF3A1C0C)
private val Brown = Color(0xFF5A3A22)
private val Rust = Color(0xFFA0521A)
private val MangoTop = Color(0xFFFFE3A0)
private val MangoMid = Color(0xFFFFC15A)
private val MangoEnd = Color(0xFFFFE9C9)

/** What goes with every shared picture: who made it, that it's private, and where to get it. */
const val SHARE_TEXT = "I'm keeping track of my spending with Owl Tracker 🦉 It works without internet and needs no login; " +
    "everything stays on the phone.\nhttps://play.google.com/store/apps/details?id=com.owl.tracker"

/**
 * The owl's reaction after you add a payment, in place of the plain message: the owl (hopping, thinking or slumped)
 * on the left, a title and a line, and Undo.
 */
@Composable
fun OwlReactionBar(m: UiMessage, data: SnackbarData) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).padding(top = 26.dp).testTag("owl_reaction")) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.inverseSurface)
                .padding(start = 82.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.text, color = c.inverseOnSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                m.line?.let { Text(it, color = c.inverseOnSurface.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
            }
            data.visuals.actionLabel?.let { a ->
                Text(
                    a, color = c.inversePrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { data.performAction() }.padding(horizontal = 12.dp, vertical = 10.dp).testTag("owl_reaction_undo"),
                )
            }
        }
        // The owl sits on the strip's left edge, standing a little above it
        MoodOwl(m.owl ?: OwlMood.HAPPY, 78.dp, Modifier.offset(x = 4.dp, y = (-24).dp), dark = false, still = m.still)
    }
}

/** A milestone, full screen and once: the owl cheering under a little confetti, the badge, Share and Yay. */
@Composable
fun CelebrationScreen(c: Celebration, still: Boolean, onShare: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFFE7A8), MangoMid, Color(0xFFF59E2B)), radius = 1600f))
                .testTag("celebration"),
        ) {
            if (!still) Confetti()
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                MoodOwl(OwlMood.CHEERING, 210.dp, dark = false, still = still)
                Spacer(Modifier.height(10.dp))
                Text(c.title, color = Cocoa, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(c.line, color = Cocoa, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFF8EE)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Medal(c.emoji, 54)
                    Column {
                        Text(if (c.share is SharePicture.GoalReached) "Goal" else "New badge", color = Brown, style = MaterialTheme.typography.labelMedium)
                        Text(c.badgeName, color = Cocoa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(36.dp))
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("Share", Color(0xFFFFE3B0), Cocoa, Modifier.weight(1f).testTag("celebration_share"), onShare)
                    Pill("Yay!", Cocoa, Color(0xFFFFF3E2), Modifier.weight(1f).testTag("celebration_close"), onClose)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(vertical = 15.dp),
    )
}

@Composable
private fun Medal(emoji: String, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFFFB21A), Color(0xFFC9700A)))),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size * 0.5).sp) }
}

/** Paper bits falling once, for about two seconds. */
@Composable
private fun Confetti() {
    val fall = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fall.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val bits = remember {
        val r = java.util.Random(7)
        val colours = listOf(Color(0xFFFFB21A), Color(0xFF4CB86B), Color(0xFFE5534B), Color(0xFF5B3FB8), Color(0xFFFFF8EE))
        List(36) { Triple(r.nextFloat(), r.nextFloat() * 0.6f, colours[it % colours.size]) to (r.nextFloat() * 360f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = fall.value
        if (t >= 1f) return@Canvas
        bits.forEach { (b, spin) ->
            val (x, start, col) = b
            val y = (start - 0.6f + t * 1.7f) * size.height
            val at = Offset(x * size.width + 18f * kotlin.math.sin((t * 6 + x * 10).toDouble()).toFloat(), y)
            rotate(spin + t * 540f, at) { drawRect(col.copy(alpha = (1.3f - t).coerceIn(0f, 1f)), at - Offset(9f, 16f), Size(18f, 32f)) }
        }
    }
}

/**
 * A picture to share, shown first so the person sees exactly what goes. It carries no rupee amounts and no
 * percentages. Share hands it, with a line about the app and its Play Store link, to the app they pick.
 */
@Composable
fun SharePictureDialog(p: SharePicture, platform: Platform, onClose: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xF0201008)).statusBarsPadding().navigationBarsPadding().padding(20.dp).testTag("share_picture"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("This is the picture that will be shared", color = Color(0xFFFFE3B0), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(if (p is SharePicture.Month) 0.8f else 1f)
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) },
            ) { PictureCard(p) }
            Spacer(Modifier.height(10.dp))
            Text("No amounts or percentages, just the wins.", color = Color(0xFFFFE3B0).copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Not now", Color.White.copy(alpha = 0.16f), Color.White, Modifier.weight(1f).testTag("share_picture_close"), onClose)
                Pill("Share…", Color(0xFFFFB21A), Cocoa, Modifier.weight(1f).testTag("share_picture_send")) {
                    scope.launch {
                        runCatching {
                            val bmp = layer.toImageBitmap().asAndroidBitmap()
                            val dir = java.io.File(context.cacheDir, "shares").apply { mkdirs() }
                            dir.listFiles()?.forEach { it.delete() }
                            val f = java.io.File(dir, "owl-tracker-${System.currentTimeMillis()}.png")
                            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            platform.shareImage(f, SHARE_TEXT)
                        }
                    }
                }
            }
        }
    }
}

/** The picture itself, drawn the same in light and dark and in every palette: it's the app's calling card. */
@Composable
private fun PictureCard(p: SharePicture) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(MangoTop, MangoMid, MangoEnd), start = Offset.Zero, end = Offset(600f, 1400f)))
            .padding(20.dp)
            .semantics { contentDescription = "The picture to share" },
    ) {
        when (p) {
            is SharePicture.Month -> MonthPicture(p)
            is SharePicture.BadgeEarned -> SquarePicture("NEW BADGE", p.name, p.what, p.emoji)
            is SharePicture.GoalReached -> SquarePicture("GOAL REACHED", p.name, "Saved up, bit by bit", p.emoji)
        }
        Footer(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Footer(modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("🦉 Owl Tracker · works offline, no login", color = Brown, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            "On Play Store", color = Color(0xFFFFF3E2), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Cocoa).padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MonthPicture(p: SharePicture.Month) {
    val tiles = buildList {
        if (p.daysWithinShare > 0) add("${p.daysWithinShare} of ${p.days}" to "days within my daily share")
        if (p.noSpendDays > 0) add("${p.noSpendDays}" to if (p.noSpendDays == 1) "no-spend day" else "no-spend days")
        if (p.lessThanBefore) add("Less" to "spent than in ${p.previousMonth}")
        if (p.badges > 0) add("${p.badges}" to if (p.badges == 1) "owl badge earned" else "owl badges earned")
        if (isEmpty()) add("🦉" to "keeping an eye on every rupee")
    }.take(4)
    Column(Modifier.fillMaxSize().padding(bottom = 30.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("MY MONTH", color = Rust, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text(
                    when (p.under) { true -> "${p.month}, done!"; false -> "${p.month}: a tough one"; null -> "My ${p.month}" },
                    color = Cocoa, style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    when (p.under) { true -> "Stayed under budget"; false -> "A little over, but look at these wins"; null -> "Tracking every rupee, offline" },
                    color = Cocoa, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                )
            }
            MoodOwl(if (p.under == true) OwlMood.CHEERING else OwlMood.HAPPY, 96.dp, dark = false, still = true)
        }
        Spacer(Modifier.weight(1f))
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (big, small) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.7f)).padding(12.dp)) {
                        Text(big, color = Cocoa, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        Text(small, color = Brown, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(0.4f))
    }
}

@Composable
private fun SquarePicture(label: String, name: String, line: String, emoji: String) {
    Column(Modifier.fillMaxSize().padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.BottomEnd) {
            MoodOwl(OwlMood.CHEERING, 130.dp, dark = false, still = true)
            Box(Modifier.offset(x = 10.dp).rotate(-8f)) { Medal(emoji, 50) }
        }
        Text(label, color = Rust, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
        Text(name, color = Cocoa, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(line, color = Cocoa, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
