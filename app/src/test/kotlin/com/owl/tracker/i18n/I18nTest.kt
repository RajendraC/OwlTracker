package com.owl.tracker.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The translation lookup, the language files, and that on-screen text goes through tr(). */
@RunWith(RobolectricTestRunner::class)
class I18nTest {
    @After fun english() = I18n.useForTests("en", emptyMap())

    @Test fun englishIsItsOwnKey() {
        assertEquals("Saved ₹240 · Food", tr("Saved {0} · {1}", "₹240", "Food"))
        assertEquals("Hello", tr("Hello"))
    }

    @Test fun aTranslationCanMoveThePlaceholders() {
        I18n.useForTests("hi", mapOf("Added {0} to {1}" to "{1} में {0} जोड़ा"))
        assertEquals("खाना में ₹240 जोड़ा", tr("Added {0} to {1}", "₹240", "खाना"))
        // missing ones stay English
        assertEquals("Undo", tr("Undo"))
    }

    @Test fun bracesThatArentPlaceholdersStay() =
        assertEquals("{x} and {9}", I18n.fill("{x} and {9}", arrayOf("a")))

    private val assetsDir = listOf(File("src/main/assets/i18n"), File("app/src/main/assets/i18n")).firstOrNull { it.isDirectory }

    /** Every translation has exactly the placeholders of its English text, so no amount goes missing. */
    @Test fun languageFilesKeepTheirPlaceholders() {
        val files = assetsDir?.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        val ph = Regex("\\{\\d\\}")
        val bad = files.flatMap { f ->
            I18n.parse(f.readText()).filter { (en, t) -> ph.findAll(en).map { it.value }.sorted().toList() != ph.findAll(t).map { it.value }.sorted().toList() }
                .map { "${f.name}: \"${it.key}\" → \"${it.value}\"" }
        }
        assertTrue("Placeholders differ:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** Every language the app offers has a file (English needs none). */
    @Test fun offeredLanguagesHaveFiles() {
        I18n.languages.filter { it.code != "en" }.forEach { l ->
            assertTrue("No file for ${l.code}", File(assetsDir, "${l.code}.json").isFile)
        }
    }

    /** New on-screen text is written as Text(tr("…")), not Text("…"), so it can be translated. */
    @Test fun screensDontShowBareEnglish() {
        val src = listOf(File("src/main/kotlin/com/owl/tracker/ui"), File("app/src/main/kotlin/com/owl/tracker/ui")).first { it.isDirectory }
        val bare = Regex("""\bText\(\s*(text\s*=\s*)?"[^"$]*[A-Za-z]{2,}""")
        val found = src.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (bare.containsMatchIn(line)) "${f.name}:${i + 1}: ${line.trim()}" else null }
        }.toList()
        assertTrue("Wrap these in tr(…):\n" + found.joinToString("\n"), found.isEmpty())
    }

    /** The owl on screen is always the app's own drawing (OwlMark, MoodOwl, Glyph for a badge), never the 🦉 emoji as text. */
    @Test fun screensDrawTheOwlNotTheEmoji() {
        val src = listOf(File("src/main/kotlin/com/owl/tracker/ui"), File("app/src/main/kotlin/com/owl/tracker/ui")).first { it.isDirectory }
        val emojiText = Regex("""\bText\([^)]*🦉""")
        val found = src.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (emojiText.containsMatchIn(line)) "${f.name}:${i + 1}: ${line.trim()}" else null }
        }.toList()
        assertTrue("Draw the owl with OwlMark or Glyph:\n" + found.joinToString("\n"), found.isEmpty())
    }
}
