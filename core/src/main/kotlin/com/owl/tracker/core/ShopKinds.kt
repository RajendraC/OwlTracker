package com.owl.tracker.core

/** The kinds of shop that get their own icon when the shop has no app: "Sri Lakshmi Jewellers", "Vision Opticals". */
enum class ShopKind { JEWELLERY, CLOTHING, BOOKS, PHARMACY, OPTICALS, BAKERY, SALON, MOBILES, HARDWARE, CLINIC }

/**
 * Tells the kind of shop from words in its name, for merchants Owl Tracker doesn't otherwise recognise.
 *
 * A shop's name usually ends with what it sells, so the last word that names a kind wins: "Diamond Hair Salon"
 * is a salon and "Sri Krishna Sweets & Bakery" a bakery. Some words say the place is none of these ("Gold's Gym",
 * "Muthoot Gold Loan", "Book Cafe"), and when one of them comes last there is no kind. Words that are also
 * people's names ("Sweety", "Tailor", "Swarna") are left out or need the plural a shop would use, so a payment
 * to a person doesn't get a shop's icon. Like merchant names, a word matches from its start, and one ending in
 * $ must be the whole word.
 */
object ShopKinds {
    private val WORDS: List<Pair<ShopKind?, List<String>>> = listOf(
        ShopKind.JEWELLERY to listOf(
            "jewel", "diamond", "gold$", "golds$", "goldsmith", "ornament", "bullion", "thanga maligai", "thangamaligai",
        ),
        ShopKind.CLOTHING to listOf(
            "silk", "saree", "sari$", "textile", "fashion", "garment", "cloth", "apparel", "boutique", "tailors$",
            "readymade", "handloom", "hosiery", "kurti", "ethnic", "menswear", "womenswear", "kidswear", "jeans$",
        ),
        ShopKind.BOOKS to listOf(
            "book$", "books$", "bookstore", "bookshop", "bookstall", "bookhouse", "book house", "stationer",
            "publication", "publisher", "pustak", "kitab",
        ),
        ShopKind.PHARMACY to listOf(
            "pharma", "medical", "chemist", "druggist", "drug house", "drugs$", "medicine", "aushadh", "medplus",
            "dawakhana", "dawai",
        ),
        ShopKind.OPTICALS to listOf("optic", "spectacle", "eyewear", "eye wear", "eyeglass", "specs$"),
        ShopKind.BAKERY to listOf(
            "baker", "bakehouse", "cake", "sweets$", "sweet mart", "sweet shop", "sweet stall", "sweet house",
            "mithai", "misthan", "mishthan", "halwai", "confection", "patisserie", "pastry", "pastries", "donut",
            "doughnut",
        ),
        ShopKind.SALON to listOf(
            "salon", "saloon", "parlour", "parlor", "spa$", "beauty", "barber", "unisex", "hair", "grooming",
            "makeover", "nails$", "nail art",
        ),
        ShopKind.MOBILES to listOf("mobile", "cellular", "cellphone", "electronic", "computer", "laptop", "gadget", "telecom"),
        ShopKind.HARDWARE to listOf(
            "hardware", "paint", "tools$", "sanitar", "plywood", "electrical", "cement", "timber", "plumb",
        ),
        ShopKind.CLINIC to listOf(
            "hospital", "clinic", "polyclinic", "diagnostic", "dental", "dentist", "lab$", "labs$", "laborator",
            "pathology", "path lab", "nursing", "scans$", "imaging", "physio", "doctor",
        ),
        // None of the above, whatever came before
        null to listOf(
            "gym$", "gyms$", "fitness", "loan", "finance", "fincorp", "finserv", "bank$", "restaurant", "hotel",
            "cafe", "café", "dhaba", "academy", "institute", "college", "school", "university", "classes",
        ),
    )

    private val compiled: List<Pair<ShopKind?, Regex>> = WORDS.flatMap { (kind, words) ->
        words.map { word ->
            val whole = word.endsWith("$")
            val text = Regex.escape(word.removeSuffix("$"))
            // Group 1 is the word itself, so its position (not the character before it) is compared
            kind to Regex("""(?:^|[^\p{L}\p{N}])(""" + text + ")" + if (whole) """(?![\p{L}\p{N}])""" else "")
        }
    }

    /** The kind of shop [name] names, or null when it names none, or names a merchant Owl Tracker knows. */
    fun of(name: String?): ShopKind? {
        if (name.isNullOrBlank() || MerchantRegistry.match(name, null) != null) return null
        val hay = name.lowercase()
        var best: ShopKind? = null
        var bestAt = -1
        for ((kind, regex) in compiled) {
            for (m in regex.findAll(hay)) {
                val at = m.groups[1]!!.range.first
                if (at > bestAt) {
                    bestAt = at
                    best = kind
                }
            }
        }
        return best
    }
}
