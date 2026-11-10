package com.owl.tracker.ui.scene

import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import kotlin.math.roundToLong

/** What the category's picture is measured against. */
enum class SceneBasis { CATEGORY_BUDGET, MONTH_BUDGET, ALL_SPENDING }

/**
 * Everything the picture of an opened category needs (see assets/scenes/scenes.js): which scene, how far along
 * it is ([fraction] of the budget, past 1 when over), its labels, and for scenes made of parts (Travel, Food,
 * Sports, ...) which parts to play, biggest spend first, each named with its top merchant.
 */
data class SceneSpec(
    val kind: String,
    val color: String,
    val fraction: Double,
    val spentRupees: Long,
    val budgetRupees: Long,
    val label: String,
    val over: String,
    val order: List<Pair<String, String>>,
    val basis: SceneBasis,
) {
    fun toJson(still: Boolean): String = buildString {
        append("{\"kind\":").append(quote(kind))
        append(",\"color\":").append(quote(color))
        append(",\"p\":").append(String.format(java.util.Locale.ROOT, "%.4f", fraction))
        append(",\"spent\":").append(spentRupees)
        append(",\"budget\":").append(budgetRupees)
        append(",\"label\":").append(quote(label))
        append(",\"over\":").append(quote(over))
        append(",\"order\":[")
        order.forEachIndexed { i, (act, name) ->
            if (i > 0) append(',')
            append('[').append(quote(act)).append(',').append(quote(name)).append(']')
        }
        append("],\"still\":").append(still).append('}')
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '<' -> append("\\u003c") // never closes a script, whatever a merchant is called
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
        "fuel" to "fuel", "bills" to "utilities", "recharge" to "recharge", "entertainment" to "fun", "sports" to "sports",
        "health" to "health", "travel" to "travel", "education" to "education", "rent" to "rent", "emi" to "emi",
        "investments" to "investments", "cash" to "cash", "transfer" to "transfers", "income" to "income",
    )

    fun kindOf(category: Category): String = KIND_OF_ICON[category.icon] ?: "others"

    /** A part of a scene made of parts: its id in scenes.js, its name, and words in a merchant's name that point to it. */
    private data class Part(val id: String, val name: String, val words: List<String>)

    private val PARTS: Map<String, List<Part>> = mapOf(
        "transport" to listOf(
            Part("metro", "Metro", listOf("metro", "dmrc", "bmrcl", "namma", "mmrc", "mmrda", "kmrl", "hmrl", "cmrl", "lmrc", "nmrc")),
            Part("bus", "Bus", listOf("bus", "bmtc", "best", "ksrtc", "msrtc", "tsrtc", "apsrtc", "dtc", "chalo", "upsrtc", "rsrtc")),
            Part("scooter", "Scooter", listOf("rapido", "bike", "scooty", "yulu", "bounce", "vogo", "auto")),
            Part("car", "Car", listOf("uber", "ola", "cab", "taxi", "blusmart", "meru", "savaari", "zoomcar")),
        ),
        "travel" to listOf(
            Part("flight", "Flight", listOf("indigo", "air india", "airindia", "vistara", "spicejet", "akasa", "airasia", "go first",
                "flight", "airline", "airways", "airport")),
            Part("train", "Train", listOf("irctc", "railway", "rail", "train")),
            Part("coach", "Coach", listOf("redbus", "abhibus", "zingbus", "intrcity", "flixbus", "volvo", "coach", "travels", "bus")),
        ),
        "utilities" to listOf(
            Part("power", "Electricity", listOf("electric", "power", "bescom", "tneb", "tangedco", "mseb", "msedcl", "bses", "tata power",
                "adani elec", "cesc", "kseb", "uppcl", "pspcl", "wbsedcl", "torrent", "discom", "energy", "apspdcl", "tsspdcl")),
            Part("water", "Water", listOf("water", "bwssb", " jal ", "djb", "hmwssb", "cmwssb")),
            Part("broadband", "Broadband", listOf("broadband", "fiber", "fibre", "fibernet", "hathway", "excitel", "bsnl", "internet",
                "wifi", "wi-fi", "tikona", "spectra", "xstream", " act ")),
            Part("gas", "Gas", listOf("gas", "indane", "lpg", "cylinder", "igl", "mgl", "gail")),
        ),
        "sports" to listOf(
            Part("cricket", "Cricket", listOf("cricket", "nets")),
            Part("football", "Football", listOf("football", "futsal", "soccer", "turf")),
            Part("badminton", "Badminton", listOf("badminton", "shuttle")),
            Part("pickleball", "Pickleball", listOf("pickleball", "pickle")),
            Part("chess", "Chess", listOf("chess")),
            Part("running", "Running", listOf("marathon", "running", "runner", "athletic", "strava", " run")),
        ),
        "health" to listOf(
            Part("gym", "Gym", listOf("gym", "cult", "fitness", "crossfit", "workout")),
            Part("yoga", "Yoga", listOf("yoga")),
            Part("meditation", "Meditation", listOf("meditat", "headspace", "calm", "mindful")),
            Part("running", "Running", listOf("marathon", "running", "strava")),
        ),
        "fun" to listOf(
            Part("park", "Amusement park", listOf("wonderla", "imagica", "essel world", "adlabs", "snow world", "water kingdom", "amusement",
                "theme park", "water park")),
            Part("movies", "Movies", listOf("pvr", "inox", "cinepolis", "cinema", "movie", "bookmyshow", "multiplex", "theatre", "theater")),
            Part("concert", "Concert", listOf("concert", "district", "insider", "live", "gig", "festival")),
            Part("bowling", "Bowling", listOf("bowling", "smaaash", "timezone", "arcade", "game zone", "gaming")),
            Part("streaming", "Streaming", listOf("netflix", "hotstar", "prime video", "primevideo", "sonyliv", "sony liv", "zee5", "jiocinema",
                "spotify", "youtube", "apple tv", " aha ", "mubi", " ott ")),
        ),
        "food" to listOf(
            Part("tiffin", "Home tiffin", listOf("tiffin", "mess", "canteen", "dabba", "meals", "khanaval", "home food")),
            Part("cafe", "Café", listOf("cafe", "café", "coffee", "starbucks", "ccd", "barista", "chai", " tea ", "blue tokai", "third wave")),
            Part("table", "Restaurant", emptyList()), // everything else: restaurants and delivery
        ),
    )

    /** When none of a category's merchants points anywhere, these play (the parts of a scene that tell its story best). */
    private val FALLBACK = mapOf(
        "transport" to "car", "travel" to "flight", "utilities" to "power", "food" to "table",
        "sports" to listOf("cricket", "football", "badminton"), "health" to listOf("running", "gym", "yoga", "meditation"),
        "fun" to listOf("movies", "park", "streaming"),
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
        val label = when {
            basis == SceneBasis.ALL_SPENDING -> "All spending $wholeText"
            kind == "rent" && fraction >= 1 -> "Rent $wholeText"
            kind == "emi" -> "EMI $wholeText"
            else -> "Budget $wholeText"
        }
        val over = if (basis != SceneBasis.ALL_SPENDING && spentPaise > whole) "${Money.formatInr(spentPaise - whole)} over" else ""
        return SceneSpec(
            kind = kind,
            color = "#%06X".format(category.color and 0xFFFFFF),
            fraction = fraction,
            spentRupees = (spentPaise / 100.0).roundToLong(),
            budgetRupees = (whole / 100.0).roundToLong(),
            label = label,
            over = over,
            order = partsFor(kind, merchants),
            basis = basis,
        )
    }

    /**
     * The parts of the scene to play: one for each kind of spending found in the merchants' names, biggest total
     * first, each named after its biggest merchant ("Bus · BMTC"). Merchants that point nowhere count toward the
     * scene's main part; with nothing found, the scene's usual parts play.
     */
    internal fun partsFor(kind: String, merchants: List<Pair<String, Long>>): List<Pair<String, String>> {
        val parts = PARTS[kind] ?: return emptyList()
        val totals = LinkedHashMap<String, Long>()
        val top = HashMap<String, Pair<String, Long>>()
        val fallback = FALLBACK[kind]
        val catchAll = parts.firstOrNull { it.words.isEmpty() }?.id ?: (fallback as? String)
        for ((name, paise) in merchants) {
            val padded = " ${name.lowercase()} "
            val part = parts.firstOrNull { p -> p.words.any { w -> padded.contains(w) } }?.id ?: catchAll ?: continue
            totals[part] = (totals[part] ?: 0L) + paise
            if ((top[part]?.second ?: -1L) < paise) top[part] = name to paise
        }
        if (totals.isEmpty()) {
            val ids = (fallback as? List<*>)?.filterIsInstance<String>() ?: listOfNotNull(fallback as? String)
            return ids.map { id -> id to parts.first { it.id == id }.name }
        }
        return totals.entries.sortedByDescending { it.value }.take(MAX_PARTS).map { (id, _) ->
            val name = parts.first { it.id == id }.name
            val merchant = top[id]?.first?.let(::shortName)
            id to if (merchant == null) name else "$name · $merchant"
        }
    }

    /** A merchant's name short enough for the label over the picture. */
    private fun shortName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= 16) clean else clean.take(15).trimEnd() + "…"
    }
}
