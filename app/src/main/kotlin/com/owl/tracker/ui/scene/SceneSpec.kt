package com.owl.tracker.ui.scene

import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What the category's picture is measured against. */
enum class SceneBasis { CATEGORY_BUDGET, MONTH_BUDGET, ALL_SPENDING }

/**
 * Everything a category's picture needs (see assets/scenes/bright.js): which scene, how full it is ([fraction] of
 * the budget, past 1 when over), the words printed on it (its [name], the amount, the [sub] line under the amount and
 * the [chip]), and for Transport which ways of getting around to show first, biggest spend first, each named with
 * its top merchant.
 */
data class SceneSpec(
    val kind: String,
    val name: String,
    val color: String,
    val fraction: Double,
    val spentRupees: Long,
    val budgetRupees: Long,
    val sub: String,
    val chip: String,
    val over: String,
    val order: List<Pair<String, String>>,
    val basis: SceneBasis,
) {
    fun toJson(still: Boolean): String = buildString {
        append("{\"kind\":").append(quote(kind))
        append(",\"name\":").append(quote(name))
        append(",\"color\":").append(quote(color))
        append(",\"p\":").append(String.format(java.util.Locale.ROOT, "%.4f", fraction))
        append(",\"spent\":").append(spentRupees)
        append(",\"budget\":").append(budgetRupees)
        append(",\"sub\":").append(quote(sub))
        append(",\"chip\":").append(quote(chip))
        append(",\"over\":").append(over.isNotEmpty())
        append(",\"order\":[")
        order.forEachIndexed { i, (part, _) ->
            if (i > 0) append(',')
            append(quote(part))
        }
        append("],\"still\":").append(still).append('}')
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '<' -> append("\\u003c") // never closes a script, whatever a category or merchant is called
            ch < ' ' || ch == ' ' || ch == ' ' -> append(String.format(java.util.Locale.ROOT, "\\u%04x", ch.code))
            else -> append(ch)
        }
        append('"')
    }
}

/** Picks the picture for a category and fills it in from the month's numbers. */
object Scenes {

    /** The scene for each category icon; any other icon gets the jar that fills. */
    private val KIND_OF_ICON = mapOf(
        "food" to "food", "coffee" to "food", "groceries" to "groceries", "shopping" to "shopping", "transport" to "transport",
        "fuel" to "fuel", "bills" to "bills", "recharge" to "recharge", "entertainment" to "entertainment", "sports" to "sports",
        "health" to "health", "travel" to "travel", "holiday" to "travel", "education" to "education", "rent" to "rent", "emi" to "emi",
        "investments" to "investments", "cash" to "cash", "transfer" to "transfers", "income" to "income",
    )

    fun kindOf(category: Category): String = KIND_OF_ICON[category.icon] ?: "others"

    /** A way of getting around: its id in bright.js, its name, and words in a merchant's name that point to it. */
    private data class Part(val id: String, val name: String, val words: List<String>)

    private val TRANSPORT = listOf(
        Part("metro", "Metro", listOf("metro", "dmrc", "bmrcl", "namma", "mmrc", "mmrda", "kmrl", "hmrl", "cmrl", "lmrc", "nmrc")),
        Part("bus", "Bus", listOf("bus", "bmtc", "best", "ksrtc", "msrtc", "tsrtc", "apsrtc", "dtc", "chalo", "upsrtc", "rsrtc")),
        Part("scooter", "Scooter", listOf("rapido", "bike", "scooty", "yulu", "bounce", "vogo", "auto")),
        Part("car", "Car", listOf("uber", "ola", "cab", "taxi", "blusmart", "meru", "savaari", "zoomcar")),
    )

    private const val MAX_PARTS = 4

    /**
     * The picture for [category] this month: [spentPaise] against its own budget if it has one, else against the
     * month's budget ([monthBudgetPaise]), else as its share of all spending ([totalSpentPaise]).
     * [merchants] are its merchants and their totals, biggest first.
     */
    fun forCategory(
        category: Category,
        spentPaise: Long,
        categoryBudgetPaise: Long?,
        monthBudgetPaise: Long,
        totalSpentPaise: Long,
        merchants: List<Pair<String, Long>>,
    ): SceneSpec {
        val kind = kindOf(category)
        val (basis, whole) = when {
            categoryBudgetPaise != null && categoryBudgetPaise > 0 -> SceneBasis.CATEGORY_BUDGET to categoryBudgetPaise
            monthBudgetPaise > 0 -> SceneBasis.MONTH_BUDGET to monthBudgetPaise
            else -> SceneBasis.ALL_SPENDING to totalSpentPaise.coerceAtLeast(1)
        }
        val fraction = spentPaise.toDouble() / whole
        val wholeText = Money.formatInr(whole)
        val pct = (fraction * 100).roundToInt()
        val over = if (basis != SceneBasis.ALL_SPENDING && spentPaise > whole) "${Money.formatInr(spentPaise - whole)} over" else ""
        val sub = when {
            basis == SceneBasis.ALL_SPENDING -> "of $wholeText all spending"
            basis == SceneBasis.MONTH_BUDGET -> "of $wholeText month budget"
            kind == "rent" -> "of $wholeText rent"
            kind == "emi" -> "of $wholeText EMI"
            else -> "of $wholeText budget"
        }
        val chip = when {
            over.isNotEmpty() -> over
            basis == SceneBasis.ALL_SPENDING -> "$pct% of all"
            basis == SceneBasis.MONTH_BUDGET -> "$pct% of month"
            kind == "rent" || kind == "emi" -> if (fraction >= 1) "Paid" else "$pct% paid"
            else -> "$pct% used"
        }
        return SceneSpec(
            kind = kind,
            name = category.name,
            color = "#%06X".format(category.color and 0xFFFFFF),
            fraction = fraction,
            spentRupees = (spentPaise / 100.0).roundToLong(),
            budgetRupees = (whole / 100.0).roundToLong(),
            sub = sub,
            chip = chip,
            over = over,
            order = if (kind == "transport") partsFor(merchants) else emptyList(),
            basis = basis,
        )
    }

    /**
     * Transport's ways of getting around to show first: one for each found in the merchants' names, biggest total
     * first, each named after its biggest merchant ("Bus · BMTC"). Merchants that point nowhere count as a car.
     */
    internal fun partsFor(merchants: List<Pair<String, Long>>): List<Pair<String, String>> {
        val totals = LinkedHashMap<String, Long>()
        val top = HashMap<String, Pair<String, Long>>()
        for ((name, paise) in merchants) {
            val padded = " ${name.lowercase()} "
            val part = TRANSPORT.firstOrNull { p -> p.words.any { w -> padded.contains(w) } }?.id ?: "car"
            totals[part] = (totals[part] ?: 0L) + paise
            if ((top[part]?.second ?: -1L) < paise) top[part] = name to paise
        }
        return totals.entries.sortedByDescending { it.value }.take(MAX_PARTS).map { (id, _) ->
            val name = TRANSPORT.first { it.id == id }.name
            val merchant = top[id]?.first?.let(::shortName)
            id to if (merchant == null) name else "$name · $merchant"
        }
    }

    /** A merchant's name short enough to say. */
    private fun shortName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= 16) clean else clean.take(15).trimEnd() + "…"
    }
}
