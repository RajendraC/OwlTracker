package com.owl.tracker.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement

/*
 * Helpers for the Robolectric journeys.
 *
 * Lists are lazy, so a row below the fold doesn't exist until it is scrolled into view: every wait
 * also scrolls any list on screen towards what it is looking for, the way a person would.
 * Taps check the target is really on screen, then trigger its click action directly, so a snackbar
 * that happens to be passing over a button doesn't swallow the tap.
 */

private fun ComposeTestRule.exists(m: SemanticsMatcher): Boolean =
    onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** Scrolls each scrollable list until one shows a node matching [m]. */
private fun ComposeTestRule.scrollListsTo(m: SemanticsMatcher): Boolean {
    val lists = onAllNodes(hasScrollToNodeAction())
    val count = runCatching { lists.fetchSemanticsNodes().size }.getOrDefault(0)
    for (i in 0 until count) {
        try {
            lists[i].performScrollToNode(m)
            waitForIdle()
            if (exists(m)) return true
        } catch (e: Throwable) {
            // Not in this list
        }
    }
    return false
}

/** Waits for background database work, scrolling lists as needed, until a node matching [m] exists. */
fun ComposeTestRule.waitForNode(m: SemanticsMatcher, timeoutMs: Long = 10_000) {
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        try {
            waitUntil(250) { exists(m) }
            return
        } catch (e: Throwable) {
            // keep looking
        }
        if (scrollListsTo(m)) return
        if (System.currentTimeMillis() > end) throw AssertionError("Nothing on screen matches ${m.description} after ${timeoutMs} ms")
    }
}

fun ComposeTestRule.waitForText(text: String, substring: Boolean = true, timeoutMs: Long = 10_000) =
    waitForNode(hasText(text, substring = substring), timeoutMs)

fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 10_000) =
    waitForNode(hasTestTag(tag), timeoutMs)

fun ComposeTestRule.tapText(text: String, substring: Boolean = false) {
    waitForText(text, substring)
    tap(onAllNodes(hasText(text, substring = substring))[0])
}

fun ComposeTestRule.tapTag(tag: String) {
    waitForTag(tag)
    tap(onNode(hasTestTag(tag)))
}

fun ComposeTestRule.typeInto(tag: String, value: String) {
    waitForTag(tag)
    onNode(hasTestTag(tag)).performScrollToSafe().performTextReplacement(value)
    waitForIdle()
}

/**
 * The first-time budget as one monthly total. With a month or more of payments the setup opens on a slider for each
 * category, so this switches to the single amount first.
 */
fun ComposeTestRule.setMonthlyTotal(rupees: String) {
    waitForNode(hasTestTag("one_total") or hasTestTag("budget_input"), 20_000)
    if (exists(hasTestTag("one_total"))) tapTag("one_total")
    typeInto("budget_input", rupees)
}

private fun ComposeTestRule.tap(node: SemanticsNodeInteraction) {
    // Just after typing, the keyboard (and a screen's ime padding with it) can still be moving: give the scroll a few tries
    val end = System.currentTimeMillis() + 3_000
    while (true) {
        node.performScrollToSafe()
        waitForIdle()
        if (runCatching { node.assertIsDisplayed() }.isSuccess || System.currentTimeMillis() > end) break
        Thread.sleep(200)
    }
    node.assertIsDisplayed()
    node.performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

private fun SemanticsNodeInteraction.performScrollToSafe(): SemanticsNodeInteraction = try {
    performScrollTo()
} catch (e: Throwable) {
    this
}

/**
 * Text on screen that doesn't fit, as a list of findings: a word or an amount broken across lines ("Groce / ries",
 * "₹6,00,7 / 99"), or an amount or a short label cut off with "…" ("₹1,14…", "Novem…"). Long tokens (SMS text, UPI
 * IDs) may still break and long names may still end in "…": those are expected.
 */
fun ComposeTestRule.textProblems(): List<String> {
    val found = mutableListOf<String>()
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
    for (node in nodes) {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        runCatching { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
        val r = results.firstOrNull() ?: continue
        val text = r.layoutInput.text.text
        for (i in 0 until r.lineCount - 1) {
            val end = r.getLineEnd(i)
            if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) {
                val from = text.lastIndexOfAny(charArrayOf(' ', '\n'), end - 1) + 1
                val to = text.indexOfAny(charArrayOf(' ', '\n'), end).let { if (it < 0) text.length else it }
                val word = text.substring(from, to)
                if (word.length <= 16) found += "\"${text.substring(from, end)} / ${text.substring(end, to)}\" in \"${text.take(60)}\""
            }
        }
        val last = r.lineCount - 1
        if (last >= 0 && r.isLineEllipsized(last) && ('₹' in text || text.length <= 12)) found += "cut off: \"${text.take(60)}\""
        if (!r.layoutInput.softWrap && r.didOverflowWidth && r.layoutInput.overflow == androidx.compose.ui.text.style.TextOverflow.Clip &&
            ('₹' in text || text.length <= 12)) found += "clipped: \"${text.take(60)}\""
    }
    return found.distinct()
}
