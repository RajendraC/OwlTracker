package com.owl.tracker.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owl.tracker.ui.components.LAUNCH_SECONDS
import com.owl.tracker.ui.components.LaunchAct
import com.owl.tracker.ui.components.LaunchBadge
import kotlinx.coroutines.delay

/**
 * The owl's hello when Owl Tracker opens: the app icon plays [act] for [LAUNCH_SECONDS], then fades away to show
 * the app underneath, which loads meanwhile. It doesn't take touches or show up for TalkBack. With "Remove
 * animations" on, the owl just appears for a moment, still.
 */
@Composable
fun LaunchGreeting(act: LaunchAct, onDone: () -> Unit) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val clock = remember { Animatable(0f) }
    val shown = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (still) delay(400) else clock.animateTo(LAUNCH_SECONDS, tween((LAUNCH_SECONDS * 1000).toInt(), easing = LinearEasing))
        shown.animateTo(0f, tween(250))
        onDone()
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .background(MaterialTheme.colorScheme.background)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LaunchBadge(act, clock.value, 168.dp)
            Text("Owl Tracker", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
