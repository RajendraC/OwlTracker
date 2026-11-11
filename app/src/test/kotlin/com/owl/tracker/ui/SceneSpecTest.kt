package com.owl.tracker.ui

import com.owl.tracker.data.Category
import com.owl.tracker.data.CategoryKind
import com.owl.tracker.ui.scene.SceneBasis
import com.owl.tracker.ui.scene.Scenes
import com.owl.tracker.ui.scene.describe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The picture shown for an opened category: which scene, against which budget, which parts and in what order. */
class SceneSpecTest {

    private fun cat(icon: String, id: String = icon, color: Long = 0xFF2A6FB5) =
        Category(id, id.replaceFirstChar { it.uppercase() }, icon, color, CategoryKind.EXPENSE, 0, isSystem = true, archived = false)

    @Test
    fun eachCategoryIconHasItsScene() {
        val kinds = mapOf(
            "food" to "food", "coffee" to "food", "groceries" to "groceries", "shopping" to "shopping", "transport" to "transport",
            "fuel" to "fuel", "bills" to "bills", "recharge" to "recharge", "entertainment" to "entertainment", "sports" to "sports",
            "health" to "health", "travel" to "travel", "education" to "education", "rent" to "rent", "emi" to "emi",
            "investments" to "investments", "cash" to "cash", "transfer" to "transfers", "income" to "income",
            "others" to "others", "gift" to "others", "pet" to "others",
        )
        kinds.forEach { (icon, kind) -> assertEquals(icon, kind, Scenes.kindOf(cat(icon))) }
    }

    /** Every kind the app asks for is one the page can draw (bright.js), so none falls back to the jar by mistake. */
    @Test
    fun everyKindIsDrawnByThePage() {
        val dir = listOf(File("src/main/assets/scenes"), File("app/src/main/assets/scenes")).first { it.isDirectory }
        val js = File(dir, "bright.js").readText()
        val cluster = Regex("var CLUSTER = \\{([^}]+)\\}").find(js)!!.groupValues[1]
        val drawn = Regex("(\\w+):").findAll(cluster).map { it.groupValues[1] }.toSet()
        listOf("food", "groceries", "shopping", "transport", "fuel", "bills", "recharge", "entertainment", "sports", "health", "travel",
            "education", "rent", "emi", "investments", "cash", "transfer", "income").forEach { icon ->
            assertTrue("$icon has no scene", Scenes.kindOf(cat(icon)) in drawn)
        }
    }

    @Test
    fun measuredAgainstItsOwnBudgetThenTheMonthsThenAllSpending() {
        val own = Scenes.forCategory(cat("groceries"), 8_460_00, 10_000_00, 50_000_00, 30_000_00, emptyList())
        assertEquals(SceneBasis.CATEGORY_BUDGET, own.basis)
        assertEquals(0.846, own.fraction, 1e-9)
        assertEquals("of ₹10,000 budget", own.sub)
        assertEquals("85% used", own.chip)
        assertEquals("", own.over)
        assertEquals(8_460, own.spentRupees)

        val month = Scenes.forCategory(cat("groceries"), 8_460_00, null, 50_000_00, 30_000_00, emptyList())
        assertEquals(SceneBasis.MONTH_BUDGET, month.basis)
        assertEquals("of ₹50,000 month budget", month.sub)
        assertEquals("17% of month", month.chip)

        val share = Scenes.forCategory(cat("groceries"), 8_460_00, null, 0, 30_000_00, emptyList())
        assertEquals(SceneBasis.ALL_SPENDING, share.basis)
        assertEquals(0.282, share.fraction, 1e-9)
        assertEquals("of ₹30,000 all spending", share.sub)
        assertEquals("28% of all", share.chip)
    }

    @Test
    fun overBudgetSaysByHowMuch() {
        val s = Scenes.forCategory(cat("shopping"), 6_120_00, 5_000_00, 0, 0, emptyList())
        assertEquals("₹1,120 over", s.over)
        assertEquals("₹1,120 over", s.chip)
        assertTrue(s.fraction > 1)
        assertTrue(describe(s, "Shopping").contains("₹1,120 over its budget"))
        assertTrue(s.toJson(still = false).contains("\"over\":true"))
    }

    @Test
    fun rentAndEmiNameTheirPayment() {
        val rent = Scenes.forCategory(cat("rent"), 22_000_00, 22_000_00, 0, 0, emptyList())
        assertEquals("of ₹22,000 rent", rent.sub)
        assertEquals("Paid", rent.chip)
        assertEquals("50% paid", Scenes.forCategory(cat("rent"), 11_000_00, 22_000_00, 0, 0, emptyList()).chip)
        assertEquals("of ₹14,700 EMI", Scenes.forCategory(cat("emi"), 14_700_00, 14_700_00, 0, 0, emptyList()).sub)
    }

    @Test
    fun transportShowsEachWayOfGettingAroundBiggestFirst() {
        val merchants = listOf("Uber" to 2_140_00L, "Rapido" to 1_860_00L, "Namma Metro" to 1_420_00L, "BMTC" to 1_060_00L, "Ola" to 900_00L)
        val s = Scenes.forCategory(cat("transport"), 7_380_00, 8_000_00, 0, 0, merchants)
        assertEquals(listOf("car", "scooter", "metro", "bus"), s.order.map { it.first })
        assertEquals("Car · Uber", s.order[0].second)
        assertEquals("Bus · BMTC", s.order[3].second)
        assertTrue(s.toJson(still = true).contains("\"order\":[\"car\",\"scooter\",\"metro\",\"bus\"]"))
        assertTrue(describe(s, "Transport").contains("Shows Car · Uber, Scooter · Rapido"))
    }

    @Test
    fun otherScenesNeedNoParts() {
        val merchants = listOf("Swiggy" to 4_820_00L, "Café Coffee Day" to 1_220_00L)
        assertEquals(emptyList<Pair<String, String>>(), Scenes.forCategory(cat("food"), 6_040_00, 12_000_00, 0, 0, merchants).order)
    }

    @Test
    fun theSpecIsSafeToHandToThePage() {
        val c = Category("pets", "Pets \"&\" </script>", "pet", 0xFF2A6FB5, CategoryKind.EXPENSE, 0, isSystem = false, archived = false)
        val s = Scenes.forCategory(c, 100_00, 1_000_00, 0, 0, emptyList())
        val json = s.toJson(still = false)
        assertFalse(json.contains("</script>"))
        assertTrue(json, json.startsWith("{\"kind\":\"others\",\"name\":\"Pets \\\"&\\\" \\u003c/script>\",\"color\":\"#2A6FB5\",\"p\":0.1000"))
        assertTrue(json.endsWith("\"order\":[],\"still\":false}"))
    }

    /** The scene page and its scripts are the app's own files and fetch nothing from anywhere. */
    @Test
    fun theScenePageLoadsNothingFromOutside() {
        val dir = listOf(File("src/main/assets/scenes"), File("app/src/main/assets/scenes")).first { it.isDirectory }
        val files = listOf("scene.html", "bright-font.js", "bright-art.js", "bright.js").map { File(dir, it) }
        files.forEach { assertTrue("${it.name} is missing", it.isFile) }
        val all = files.joinToString("\n") { it.readText() }
        listOf("http://", "https://", "fetch(", "XMLHttpRequest", "WebSocket", "importScripts").forEach {
            assertFalse("the scenes must not use $it", all.contains(it))
        }
        // url() only ever points inside the picture itself (its gradients and filters), never at a file
        assertEquals(emptyList<String>(), Regex("url\\((?!#)[^)]*\\)").findAll(all).map { it.value }.toList())
        val html = files[0].readText()
        assertEquals(listOf("bright-font.js", "bright-art.js", "bright.js"), Regex("<script src=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList())
        // The font comes with its licence
        assertTrue(File(dir, "../fonts/OFL.txt").isFile)
    }
}
