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
import com.owl.tracker.core.Money
import kotlin.math.roundToInt

/**
 * A category's picture: a short animated scene drawn by assets/scenes (glossy groceries in a basket, coffee and
 * cake on a board, cars on a road...), filling up with how much of the budget the category has used. It plays once when it
 * appears and again when tapped (or, with [onClick], tapping does that instead). The page is the app's own file:
 * it never reaches the network.
 *
 * [caption] adds the line underneath that says what the picture is measured against.
 */
@Composable
fun CategoryScene(
    spec: SceneSpec,
    categoryName: String,
    modifier: Modifier = Modifier,
    caption: Boolean = true,
    tag: String = "category_scene",
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val still = remember { animationsOff(context) }
    val holder = remember { SceneHolder() }
    val json = spec.toJson(still)
    val tint = lerp(Color(android.graphics.Color.parseColor(spec.color)), Color.White, 0.86f)
    val spoken = describe(spec, categoryName)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(320f / 180f).clip(RoundedCornerShape(22.dp)).background(tint)
                .testTag(tag),
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
                    .clickable(onClickLabel = if (onClick == null) "Play it again" else "Open $categoryName") { if (onClick != null) onClick() else holder.replay() }
                    .semantics { contentDescription = spoken },
            )
        }
        if (caption) Text(
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
    return "Picture of $categoryName, ${Money.formatInr(spec.spentRupees * 100)}: $how" + (if (parts.isEmpty()) "" else ". Shows $parts") + "."
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
        // Fills the box it's given: with WRAP_CONTENT, a WebView gives its page no height to lay out in
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
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
        val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) webChromeClient = object : android.webkit.WebChromeClient() {
            // Test builds only: the page's own errors, to find a picture that didn't draw
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.w("OwlScene", "${m.messageLevel()}: ${m.message()} (${m.sourceId()?.substringAfterLast('/')}:${m.lineNumber()})")
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            // Either may come first; the page picks up a picture handed over before its script ran
            override fun onPageCommitVisible(view: WebView, url: String?) = pageReady()

            override fun onPageFinished(view: WebView, url: String?) = pageReady()
        }
        web = this
        loadUrl("file:///android_asset/scenes/scene.html")
    }

    private fun pageReady() {
        ready = true
        pending?.let { render(it) }
    }

    fun show(json: String) {
        if (json == shown) return
        pending = json
        if (ready) render(json)
    }

    private fun render(json: String) {
        shown = json
        pending = null
        web?.evaluateJavascript("window.__scene=$json;if(window.renderScene)window.renderScene(window.__scene);", null)
    }

    fun replay() {
        web?.evaluateJavascript("if(window.replay)window.replay();", null)
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
