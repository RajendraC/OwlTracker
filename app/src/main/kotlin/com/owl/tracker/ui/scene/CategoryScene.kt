package com.owl.tracker.ui.scene

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/**
 * The picture for an opened category: a short animated scene drawn by assets/scenes (the people, vehicles,
 * bowls and cups of the design), played once when it opens and again when tapped. It shows how much of the
 * budget the category has used. The page is the app's own file: it never reaches the network.
 */
@Composable
fun CategoryScene(spec: SceneSpec, categoryName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember { animationsOff(context) }
    val holder = remember { SceneHolder() }
    val json = spec.toJson(still)
    val tint = lerp(Color(android.graphics.Color.parseColor(spec.color)), Color.White, 0.92f)
    val spoken = describe(spec, categoryName)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(248f / 120f).clip(RoundedCornerShape(16.dp)).background(tint)
                .testTag("category_scene"),
        ) {
            AndroidView(
                factory = { ctx -> holder.create(ctx) },
                modifier = Modifier.fillMaxSize(),
                update = { holder.show(json) },
                onRelease = { holder.release() },
            )
            // Taps go to the picture as a whole (the page itself takes none)
            Box(
                Modifier.matchParentSize()
                    .clickable(onClickLabel = "Play it again") { holder.replay() }
                    .semantics { contentDescription = spoken },
            )
        }
        Text(
            when (spec.basis) {
                SceneBasis.CATEGORY_BUDGET -> "Against its own budget · tap the picture to play it again"
                SceneBasis.MONTH_BUDGET -> "Against the month's budget · tap the picture to play it again"
                SceneBasis.ALL_SPENDING -> "As a share of all spending · tap the picture to play it again"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/** What the picture says, for screen readers. */
internal fun describe(spec: SceneSpec, categoryName: String): String {
    val pct = (spec.fraction * 100).roundToInt()
    val how = when {
        spec.over.isNotEmpty() -> "${spec.over} its budget"
        spec.basis == SceneBasis.CATEGORY_BUDGET -> "$pct% of its budget used"
        spec.basis == SceneBasis.MONTH_BUDGET -> "$pct% of the month's budget"
        else -> "$pct% of all spending"
    }
    val parts = spec.order.joinToString(", ") { it.second }
    return "Picture of $categoryName: $how" + (if (parts.isEmpty()) "" else ". Shows $parts") + "."
}

/** With animations turned off in the phone's settings, the picture shows its last frame without moving. */
private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Keeps the page and draws each new spec into it once it has loaded. */
private class SceneHolder {
    private var web: WebView? = null
    private var ready = false
    private var pending: String? = null
    private var shown: String? = null

    @SuppressLint("SetJavaScriptEnabled") // the page and its scripts are the app's own files; it loads nothing else
    fun create(context: Context): WebView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String?) {
                ready = true
                pending?.let { render(it) }
            }
        }
        web = this
        loadUrl("file:///android_asset/scenes/scene.html")
    }

    fun show(json: String) {
        if (json == shown) return
        pending = json
        if (ready) render(json)
    }

    private fun render(json: String) {
        shown = json
        pending = null
        web?.evaluateJavascript("renderScene($json)", null)
    }

    fun replay() {
        web?.evaluateJavascript("replay()", null)
    }

    fun release() {
        web?.apply {
            stopLoading()
            destroy()
        }
        web = null
        ready = false
        shown = null
    }
}
