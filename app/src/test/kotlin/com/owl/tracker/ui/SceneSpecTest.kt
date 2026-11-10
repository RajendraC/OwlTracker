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
            "food" to "food", "groceries" to "groceries", "shopping" to "shopping", "transport" to "transport", "fuel" to "fuel",
            "bills" to "utilities", "recharge" to "recharge", "entertainment" to "fun", "sports" to "sports", "health" to "health",
            "travel" to "travel", "education" to "education", "rent" to "rent", "emi" to "emi", "cash" to "cash", "others" to "others",
            "gift" to "others", "pet" to "others",
        )
        kinds.forEach { (icon, kind) -> assertEquals(icon, kind, Scenes.kindOf(cat(icon))) }
    }

    @Test
    fun measuredAgainstItsOwnBudgetThenTheMonthsThenAllSpending() {
        val own = Scenes.forCategory(cat("groceries"), 8_460_00, 10_000_00, 50_000_00, 30_000_00, emptyList())
        assertEquals(SceneBasis.CATEGORY_BUDGET, own.basis)
        assertEquals(0.846, own.fraction, 1e-9)
        assertEquals("Budget ₹10,000", own.label)
        assertEquals("", own.over)

        val month = Scenes.forCategory(cat("groceries"), 8_460_00, null, 50_000_00, 30_000_00, emptyList())
        assertEquals(SceneBasis.MONTH_BUDGET, month.basis)
        assertEquals("Budget ₹50,000", month.label)

        val share = Scenes.forCategory(cat("groceries"), 8_460_00, null, 0, 30_000_00, emptyList())
        assertEquals(SceneBasis.ALL_SPENDING, share.basis)
        assertEquals(0.282, share.fraction, 1e-9)
        assertEquals("All spending ₹30,000", share.label)
    }

    @Test
    fun overBudgetSaysByHowMuch() {
        val s = Scenes.forCategory(cat("shopping"), 6_120_00, 5_000_00, 0, 0, emptyList())
        assertEquals("₹1,120 over", s.over)
        assertTrue(s.fraction > 1)
        assertTrue(describe(s, "Shopping").contains("₹1,120 over its budget"))
    }

    @Test
    fun rentAndEmiNameTheirPayment() {
        assertEquals("Rent ₹22,000", Scenes.forCategory(cat("rent"), 22_000_00, 22_000_00, 0, 0, emptyList()).label)
        assertEquals("Budget ₹22,000", Scenes.forCategory(cat("rent"), 11_000_00, 22_000_00, 0, 0, emptyList()).label)
        assertEquals("EMI ₹14,700", Scenes.forCategory(cat("emi"), 14_700_00, 14_700_00, 0, 0, emptyList()).label)
    }

    @Test
    fun transportPlaysEachWayOfGettingAroundBiggestFirst() {
        val merchants = listOf("Uber" to 2_140_00L, "Rapido" to 1_860_00L, "Namma Metro" to 1_420_00L, "BMTC" to 1_060_00L, "Ola" to 900_00L)
        val parts = Scenes.forCategory(cat("transport"), 7_380_00, 8_000_00, 0, 0, merchants).order
        assertEquals(listOf("car", "scooter", "metro", "bus"), parts.map { it.first })
        assertEquals("Car · Uber", parts[0].second)
        assertEquals("Bus · BMTC", parts[3].second)
    }

    @Test
    fun foodSortsCafésTiffinsAndRestaurants() {
        val merchants = listOf("Swiggy" to 4_820_00L, "Zomato" to 3_560_00L, "Tiffin service" to 1_640_00L, "Café Coffee Day" to 1_220_00L)
        val parts = Scenes.forCategory(cat("food"), 11_240_00, 12_000_00, 0, 0, merchants).order
        assertEquals(listOf("table" to "Restaurant · Swiggy", "tiffin" to "Home tiffin · Tiffin service", "cafe" to "Café · Café Coffee Day"), parts)
    }

    @Test
    fun sportsShowsOnlyTheGamesPaidForElseItsUsualThree() {
        val paid = Scenes.forCategory(cat("sports"), 1_600_00, 2_500_00, 0, 0, listOf("Smash Badminton Arena" to 900_00L, "Chess.com" to 700_00L)).order
        assertEquals(listOf("badminton", "chess"), paid.map { it.first })
        val unknown = Scenes.forCategory(cat("sports"), 1_600_00, 2_500_00, 0, 0, listOf("Playo" to 1_600_00L)).order
        assertEquals(listOf("cricket", "football", "badminton"), unknown.map { it.first })
    }

    @Test
    fun noMoreThanFourPartsAndLongNamesAreShortened() {
        val merchants = listOf("Wonderla Holidays Limited" to 5_000_00L, "PVR INOX" to 4_000_00L, "Netflix" to 3_000_00L,
            "District by Zomato concert" to 2_000_00L, "Smaaash" to 1_000_00L)
        val parts = Scenes.forCategory(cat("entertainment"), 15_000_00, 20_000_00, 0, 0, merchants).order
        assertEquals(4, parts.size)
        assertEquals("Amusement park · Wonderla Holida…", parts[0].second)
    }

    @Test
    fun theSpecIsSafeToHandToThePage() {
        val s = Scenes.forCategory(cat("transport"), 100_00, 1_000_00, 0, 0, listOf("Ub\"r</script>" to 100_00L))
        val json = s.toJson(still = false)
        assertFalse(json.contains("</script>"))
        assertTrue(json.startsWith("{\"kind\":\"transport\",\"color\":\"#2A6FB5\",\"p\":0.1000"))
        assertTrue(json, json.contains("Car · Ub\\\"r\\u003c/script>"))
        assertTrue(json.endsWith("\"still\":false}"))
    }

    /** The scene page and its scripts are the app's own files and fetch nothing from anywhere. */
    @Test
    fun theScenePageLoadsNothingFromOutside() {
        val dir = listOf(File("src/main/assets/scenes"), File("app/src/main/assets/scenes")).first { it.isDirectory }
        val files = listOf("scene.html", "scenes.js", "acts.js").map { File(dir, it) }
        files.forEach { assertTrue("${it.name} is missing", it.isFile) }
        val all = files.joinToString("\n") { it.readText() }
        listOf("http://", "https://", "fetch(", "XMLHttpRequest", "WebSocket", "importScripts").forEach {
            assertFalse("the scenes must not use $it", all.contains(it))
        }
        val html = files[0].readText()
        assertEquals(listOf("acts.js", "scenes.js"), Regex("<script src=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList())
    }
}
