package com.owl.tracker.core

/**
 * Default category keys. The app seeds these; users can rename, recolor or add their own.
 *
 * Since 1.8 most categories sit in a group (see [CategoryGroups]). A group is a category too: budgets are set on
 * groups, and a payment put in the group itself (an Amazon order, whose SMS never says what was bought) waits there,
 * not sorted yet, until the user picks one of the group's categories.
 */
object Cat {
    // Groups. FOOD, SHOPPING, TRANSPORT, BILLS and HEALTH were categories before 1.8 and keep their keys.
    const val SHOPPING = "shopping"

    /** Getting around. */
    const val TRANSPORT = "transport"
    const val KIDS = "kids"

    /** Food: delivery, eating out and groceries. */
    const val FOOD = "food"

    /** Home & Bills. */
    const val BILLS = "bills"
    const val HEALTH = "health"

    /** Fun & Travel. */
    const val FUN = "fun"

    /** Gifts & Giving. */
    const val GIVING = "giving"

    // Shopping
    const val FASHION = "fashion"
    const val ELECTRONICS = "electronics"
    const val FURNITURE = "furniture"
    const val BEAUTY = "beauty"
    const val JEWELLERY = "jewellery"

    // Getting around
    /** Daily Commute: buses, local trains and autos paid on the spot. */
    const val COMMUTE = "commute"

    /** Cabs & Bike Taxis: Uber, Ola, Rapido, Namma Yatri and taxis (1.9.15). */
    const val CABS = "cabs"

    /** Metro: metro rides, metro card and NCMC top-ups (1.9.13). */
    const val METRO = "metro"
    const val FUEL = "fuel"

    /** Car & Bike: tolls, FASTag, parking, service. */
    const val CAR_BIKE = "car_bike"

    // Kids
    const val KIDS_EDUCATION = "kids_education"

    /** Kids' Sports & Extracurricular. */
    const val KIDS_ACTIVITIES = "kids_activities"

    /** Kids' Clothes (was Kids' Shopping & Care before 1.9.12, when Toys and Baby Essentials got their own). */
    const val KIDS_CARE = "kids_care"

    /** Baby Essentials: diapers, wipes, formula, baby food (1.9.12). */
    const val BABY = "baby_essentials"

    /** Daycare & Nanny: creche, daycare and playgroup fees, a nanny or babysitter (1.9.12). */
    const val DAYCARE = "daycare"

    /** Kids' Health & Vaccines: the paediatrician, children's hospitals, vaccinations (1.9.12). */
    const val KIDS_HEALTH = "kids_health"

    /** Toys (1.9.12). */
    const val KIDS_TOYS = "kids_toys"

    // Food
    const val FOOD_DELIVERY = "food_delivery"

    /** Eating Out. */
    const val DINING = "dining"
    const val GROCERIES = "groceries"

    // Home & Bills
    const val RENT = "rent"

    /** Electricity, Water & Gas. */
    const val UTILITIES = "utilities"

    /** Mobile & Internet (was Recharge & Subscriptions). */
    const val RECHARGE = "recharge"

    /** OTT & Subscriptions. */
    const val SUBSCRIPTIONS = "subscriptions"
    const val HOME_HELP = "home_help"
    const val INSURANCE = "insurance"

    // Health
    const val MEDICINES = "medicines"

    /** Doctor & Tests. */
    const val DOCTOR = "doctor"
    /** Sports & Fitness: gyms, yoga, courts and turfs (in Health since 1.8.1; in Fun & Travel in 1.8.0). */
    const val SPORTS = "sports"

    // Fun & Travel
    /** Movies & Events (was Entertainment). */
    const val ENTERTAINMENT = "entertainment"
    const val TRAVEL = "travel"

    /** Trips and vacations: where you stay and the packages you book. Flights, trains and buses stay in [TRAVEL]. */
    const val HOLIDAY = "holiday"

    /** Courses & Books: your own learning (was Education; children's school is [KIDS_EDUCATION]). */
    const val EDUCATION = "education"

    // Gifts & Giving
    const val TEMPLE = "temple"
    const val GIFTS = "gifts"

    // On their own
    const val EMI = "emi"
    const val CASH = "cash"
    const val PETS = "pets"
    const val OTHERS = "others"
    const val INVESTMENTS = "investments"
    const val TRANSFERS = "transfers"
    const val INCOME = "income"
}

/** Which built-in group each built-in category sits in, and what the Kids categories become when Kids is off. */
object CategoryGroups {
    val GROUPS: List<String> = listOf(Cat.TRANSPORT, Cat.SHOPPING, Cat.FOOD, Cat.KIDS, Cat.BILLS, Cat.HEALTH, Cat.FUN, Cat.GIVING)

    val CHILDREN: Map<String, List<String>> = mapOf(
        Cat.TRANSPORT to listOf(Cat.CABS, Cat.COMMUTE, Cat.METRO, Cat.FUEL, Cat.CAR_BIKE),
        Cat.SHOPPING to listOf(Cat.FASHION, Cat.ELECTRONICS, Cat.FURNITURE, Cat.BEAUTY, Cat.JEWELLERY),
        Cat.FOOD to listOf(Cat.FOOD_DELIVERY, Cat.DINING, Cat.GROCERIES),
        Cat.KIDS to listOf(Cat.BABY, Cat.DAYCARE, Cat.KIDS_EDUCATION, Cat.KIDS_ACTIVITIES, Cat.KIDS_HEALTH, Cat.KIDS_TOYS, Cat.KIDS_CARE),
        Cat.BILLS to listOf(Cat.RENT, Cat.UTILITIES, Cat.RECHARGE, Cat.SUBSCRIPTIONS, Cat.HOME_HELP, Cat.INSURANCE),
        Cat.HEALTH to listOf(Cat.MEDICINES, Cat.DOCTOR, Cat.SPORTS),
        Cat.FUN to listOf(Cat.ENTERTAINMENT, Cat.TRAVEL, Cat.HOLIDAY, Cat.EDUCATION),
        Cat.GIVING to listOf(Cat.TEMPLE, Cat.GIFTS),
    )

    val PARENT: Map<String, String> = CHILDREN.flatMap { (g, kids) -> kids.map { it to g } }.toMap()

    val KIDS: Set<String> = setOf(Cat.KIDS, Cat.KIDS_EDUCATION, Cat.KIDS_ACTIVITIES, Cat.KIDS_CARE, Cat.BABY, Cat.DAYCARE, Cat.KIDS_HEALTH, Cat.KIDS_TOYS)

    /** The Kids categories added in 1.9.12: payments the usual rules now put there are moved once (categories 4). */
    val NEW_KIDS: Set<String> = setOf(Cat.BABY, Cat.DAYCARE, Cat.KIDS_HEALTH, Cat.KIDS_TOYS)

    /** Where a Kids category's payments go for someone who doesn't pay for children. */
    fun withoutKids(id: String): String = when (id) {
        Cat.KIDS_EDUCATION -> Cat.EDUCATION
        Cat.KIDS_ACTIVITIES -> Cat.SPORTS
        Cat.KIDS_CARE -> Cat.FASHION
        Cat.BABY, Cat.KIDS_TOYS, Cat.KIDS -> Cat.SHOPPING
        Cat.DAYCARE -> Cat.HOME_HELP
        Cat.KIDS_HEALTH -> Cat.DOCTOR
        else -> id
    }

    // Places whose payments could be for you or for your children: an academy, coaching, classes, a swimming pool
    private val EITHER = Regex(
        """academy|coaching|classes|\bclass\b|institute|tuition|tution|\bswim|dance|music|chess|karate|taekwondo|skating|abacus|\bcamp\b|drawing""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * For a payment to [merchantName] in [category] that could be anyone's ("Aqua Swim Academy" in Sports & Fitness):
     * the category if it was for you, and the Kids one if it was for your children. Null when there's nothing to ask.
     */
    fun forYouOrKids(merchantName: String?, category: String): Pair<String, String>? {
        if (merchantName == null || !EITHER.containsMatchIn(merchantName)) return null
        return when (category) {
            Cat.SPORTS, Cat.KIDS_ACTIVITIES -> Cat.SPORTS to Cat.KIDS_ACTIVITIES
            Cat.EDUCATION, Cat.KIDS_EDUCATION -> Cat.EDUCATION to Cat.KIDS_EDUCATION
            else -> null
        }
    }

    /** The group a built-in category sits in, or the category itself when it sits on its own. */
    fun top(id: String): String = PARENT[id] ?: id

    /**
     * Before 1.8, what each old category was split into: a payment there that today's rules put in one of these is
     * moved once (Myntra from Shopping to Fashion & Clothing, Uber from Transport to Cabs & Bike Taxis since 1.9.15). Payments in Others
     * may move into any of the new categories. Anything else stays where it is: the user may have put it there.
     */
    val SPLIT_FROM: Map<String, Set<String>> by lazy {
        val new = setOf(
            Cat.FASHION, Cat.ELECTRONICS, Cat.FURNITURE, Cat.BEAUTY, Cat.JEWELLERY, Cat.COMMUTE, Cat.CABS, Cat.METRO, Cat.CAR_BIKE,
            Cat.KIDS_EDUCATION, Cat.KIDS_ACTIVITIES, Cat.KIDS_CARE, Cat.FOOD_DELIVERY, Cat.DINING, Cat.UTILITIES,
            Cat.SUBSCRIPTIONS, Cat.HOME_HELP, Cat.INSURANCE, Cat.MEDICINES, Cat.DOCTOR, Cat.TEMPLE, Cat.GIFTS, Cat.PETS,
        )
        mapOf(
            Cat.SHOPPING to setOf(Cat.FASHION, Cat.ELECTRONICS, Cat.FURNITURE, Cat.BEAUTY, Cat.JEWELLERY, Cat.KIDS_CARE, Cat.SPORTS, Cat.EDUCATION, Cat.GIFTS, Cat.PETS),
            Cat.TRANSPORT to setOf(Cat.COMMUTE, Cat.CABS, Cat.METRO, Cat.CAR_BIKE),
            Cat.FOOD to setOf(Cat.FOOD_DELIVERY, Cat.DINING),
            Cat.BILLS to setOf(Cat.UTILITIES, Cat.RECHARGE, Cat.INSURANCE, Cat.HOME_HELP),
            Cat.RECHARGE to setOf(Cat.SUBSCRIPTIONS),
            Cat.HEALTH to setOf(Cat.MEDICINES, Cat.DOCTOR, Cat.INSURANCE, Cat.SUBSCRIPTIONS, Cat.SPORTS),
            Cat.ENTERTAINMENT to setOf(Cat.SUBSCRIPTIONS),
            Cat.EDUCATION to setOf(Cat.KIDS_EDUCATION, Cat.KIDS_ACTIVITIES),
            Cat.OTHERS to new,
        )
    }
}

/**
 * A merchant we recognise. [packageName] is the Android app whose icon is shown when that app is
 * installed on the phone; every package listed here must also be listed in the app manifest's
 * <queries> block, or Android 11+ hides it from us.
 */
/**
 * [keepName]: for networks of outlets (petrol pumps, toll plazas) the SMS's own name says which one, so it is
 * kept, each outlet stays its own merchant, and only the brand's icon and category are added.
 */
data class KnownMerchant(
    val key: String,
    val displayName: String,
    val packageName: String?,
    val category: String,
    val patterns: List<String>,
    val keepName: Boolean = false,
)

object MerchantRegistry {

    private fun m(key: String, name: String, pkg: String?, cat: String, vararg patterns: String) =
        KnownMerchant(key, name, pkg, cat, patterns.toList())

    /** An outlet of a network (a petrol pump, a toll plaza): keeps the SMS's name, adds the brand's icon. */
    private fun outlet(key: String, name: String, pkg: String?, cat: String, vararg patterns: String) =
        KnownMerchant(key, name, pkg, cat, patterns.toList(), keepName = true)

    /**
     * Order matters: more specific names (Instamart, Prime Video) come before broader ones (Swiggy, Amazon).
     * A pattern matches from the start of a word; one ending in $ must also end where a word ends, so
     * "canva$" finds CANVA but not CANVAS PRINTS.
     */
    val all: List<KnownMerchant> = listOf(
        m("swiggy_instamart", "Swiggy Instamart", "in.swiggy.android", Cat.GROCERIES, "swiggyinstamart", "instamart"),
        m("swiggy", "Swiggy", "in.swiggy.android", Cat.FOOD_DELIVERY, "swiggy", "bundl"),
        m("zomato", "Zomato", "com.application.zomato", Cat.FOOD_DELIVERY, "zomato"),
        m("blinkit", "Blinkit", "com.grofers.customerapp", Cat.GROCERIES, "blinkit", "grofers"),
        m("zepto", "Zepto", "com.zeptoconsumerapp", Cat.GROCERIES, "zepto", "kiranakart"),
        m("bigbasket", "BigBasket", "com.bigbasket.mobileapp", Cat.GROCERIES, "bigbasket", "bb now", "bbnow", "supermarket grocery supplies"),
        m("jiomart", "JioMart", "com.jpl.jiomart", Cat.GROCERIES, "jiomart"),
        m("dmart", "DMart Ready", "in.dmart", Cat.GROCERIES, "dmart", "avenue supermarts", "avenue e-commerce"),
        m("licious", "Licious", "com.licious", Cat.GROCERIES, "licious", "delightful gourmet"),
        m("primevideo", "Prime Video", "com.amazon.avod.thirdpartyclient", Cat.SUBSCRIPTIONS, "primevideo", "prime video"),
        m("kindle", "Kindle", "com.amazon.kindle", Cat.EDUCATION, "kindle"),
        m("audible", "Audible", "com.audible.application", Cat.SUBSCRIPTIONS, "audible"),
        m("amazonmusic", "Amazon Music", "com.amazon.mp3", Cat.SUBSCRIPTIONS, "amazon music", "amazonmusic"),
        m("amazon", "Amazon", "in.amazon.mShop.android.shopping", Cat.SHOPPING, "amazon", "amzn"),
        m("shopsy", "Shopsy", "com.flipkart.shopsy", Cat.SHOPPING, "shopsy"),
        m("flipkart", "Flipkart", "com.flipkart.android", Cat.SHOPPING, "flipkart", "fkrt"),
        m("myntra", "Myntra", "com.myntra.android", Cat.FASHION, "myntra"),
        m("ajio", "AJIO", "com.ril.ajio", Cat.FASHION, "ajio"),
        m("nykaa", "Nykaa", "com.fsn.nykaa", Cat.BEAUTY, "nykaa"),
        m("meesho", "Meesho", "com.meesho.supply", Cat.SHOPPING, "meesho"),
        m("tatacliq", "Tata CLiQ", "com.tul.tatacliq", Cat.SHOPPING, "tatacliq", "tata cliq"),
        m("lenskart", "Lenskart", "com.lenskart.app", Cat.FASHION, "lenskart"),
        m("snapdeal", "Snapdeal", "com.snapdeal.main", Cat.SHOPPING, "snapdeal"),
        m("firstcry", "FirstCry", "fc.admin.fcexpressadmin", Cat.BABY, "firstcry", "brainbees"),
        m("babychakra", "BabyChakra", null, Cat.BABY, "babychakra"),
        m("pampers", "Pampers", null, Cat.BABY, "pampers"),
        m("huggies", "Huggies", null, Cat.BABY, "huggies"),
        m("mamypoko", "MamyPoko", null, Cat.BABY, "mamypoko", "mamy poko"),
        m("luvlap", "LuvLap", null, Cat.BABY, "luvlap"),
        m("rainbowhospitals", "Rainbow Children's Hospital", null, Cat.KIDS_HEALTH, "rainbow child", "rainbow hospital"),
        m("funskool", "Funskool", null, Cat.KIDS_TOYS, "funskool"),
        m("toycra", "Toycra", null, Cat.KIDS_TOYS, "toycra"),
        m("ikea", "IKEA", "com.ingka.ikea.app", Cat.FURNITURE, "ikea"),
        m("tataneu", "Tata Neu", "com.tatadigital.tcp", Cat.SHOPPING, "tata neu", "tataneu", "tata digital"),
        // Jewellers. Short or common words ("gold", "malabar", "giva") only match with the brand's own words.
        m("tanishq", "Tanishq", "com.titancompany.tanishqapp", Cat.JEWELLERY, "tanishq"),
        m("titaneye", "Titan Eye+", "com.titan.eyecare", Cat.FASHION, "titan eye", "titaneye"),
        m("caratlane", "CaratLane", "com.caratlane.android", Cat.JEWELLERY, "caratlane", "carat lane"),
        m("malabargold", "Malabar Gold & Diamonds", "malabargold.qburst.com.malabargold", Cat.JEWELLERY, "malabar gold", "malabargold", "malabar diamond"),
        m("joyalukkas", "Joyalukkas", "com.joyalukkas.mcstaging.twa", Cat.JEWELLERY, "joyalukkas", "joy alukkas", "joyalukas"),
        m("bluestone", "BlueStone", "com.bluestone.android", Cat.JEWELLERY, "bluestone"),
        m("giva", "GIVA", "co.giva.jewellery", Cat.JEWELLERY, "giva$", "indiejewel"),
        m("senco", "Senco Gold & Diamonds", "com.india.senco", Cat.JEWELLERY, "senco gold", "sencogold", "senco$"),
        // Clothing. Zara only by its company or site (Zara is also a name), Westside not by Trent (Zudio is Trent too).
        m("westside", "Westside", "com.westside", Cat.FASHION, "westside"),
        m("maxfashion", "Max Fashion", "com.applications.max", Cat.FASHION, "max fashion", "maxfashion"),
        m("lifestyle", "Lifestyle", "com.applications.lifestyle", Cat.FASHION, "lifestyle international", "lifestyle stores", "lifestylestores"),
        m("pantaloons", "Pantaloons", "com.pantaloons", Cat.FASHION, "pantaloons"),
        m("shoppersstop", "Shoppers Stop", "shoppersstop.shoppersstop", Cat.FASHION, "shoppers stop", "shoppersstop", "shopper's stop", "shoppers' stop"),
        m("hm", "H&M", "com.hm.goe", Cat.FASHION, "h&m$", "h & m$", "hennes"),
        m("zara", "Zara", "com.inditex.zara", Cat.FASHION, "inditex", "zara india", "zara.com"),
        m("uniqlo", "UNIQLO", "com.uniqlo.in.catalogue", Cat.FASHION, "uniqlo"),
        m("fabindia", "Fabindia", "com.fabindia.shopping", Cat.FASHION, "fabindia", "fab india"),
        m("souledstore", "The Souled Store", "com.thesouledstore", Cat.FASHION, "souled store", "souledstore", "thesouledstore"),
        m("snitch", "SNITCH", "co.shopney.snitch", Cat.FASHION, "snitch"),
        m("bewakoof", "Bewakoof", "com.bewakoof.bewakoof", Cat.FASHION, "bewakoof"),
        // Shops for the finer Shopping categories. No app icons until each package name is checked on Google Play.
        m("manyavar", "Manyavar", null, Cat.FASHION, "manyavar", "vedant fashions"),
        m("abfrl", "Aditya Birla Fashion", null, Cat.FASHION, "aditya birla fashion", "abfrl"),
        m("reliancetrends", "Trends", null, Cat.FASHION, "reliance trends"),
        m("bata", "Bata", null, Cat.FASHION, "bata india", "bata store", "bata shoe"),
        m("metroshoes", "Metro Shoes", null, Cat.FASHION, "metro brands", "metro shoes"),
        m("metrocash", "Metro Cash & Carry", null, Cat.GROCERIES, "metro cash", "metro wholesale"),
        // The metro systems (1.9.13): rides, smart cards and their apps
        m("dmrc", "Delhi Metro", null, Cat.METRO, "dmrc", "delhi metro"),
        m("bmrcl", "Namma Metro", null, Cat.METRO, "bmrcl", "namma metro", "bangalore metro", "bengaluru metro"),
        m("mmopl", "Mumbai Metro", null, Cat.METRO, "mmopl", "mumbai metro", "mmmocl", "mmrda metro"),
        m("hmrl", "Hyderabad Metro", null, Cat.METRO, "hyderabad metro", "l&t metro", "ltmrhl", "hmrl"),
        m("cmrl", "Chennai Metro", null, Cat.METRO, "cmrl", "chennai metro"),
        m("kmrl", "Kochi Metro", null, Cat.METRO, "kmrl", "kochi metro"),
        m("mahametro", "Maha Metro", null, Cat.METRO, "maha metro", "mahametro", "pune metro", "nagpur metro"),
        m("nmrc", "Noida Metro", null, Cat.METRO, "nmrc", "noida metro"),
        m("lmrc", "Lucknow Metro", null, Cat.METRO, "lmrc", "lucknow metro", "upmrc"),
        m("gmrc", "Gujarat Metro", null, Cat.METRO, "gmrc", "ahmedabad metro", "surat metro"),
        m("kolkatametro", "Kolkata Metro", null, Cat.METRO, "kolkata metro"),
        m("croma", "Croma", null, Cat.ELECTRONICS, "croma", "infiniti retail"),
        m("reliancedigital", "Reliance Digital", null, Cat.ELECTRONICS, "reliance digital", "reliancedigital"),
        m("applestore", "Apple Store", null, Cat.ELECTRONICS, "apple store", "apple india", "apple retail"),
        m("samsung", "Samsung", null, Cat.ELECTRONICS, "samsung"),
        m("oneplus", "OnePlus", null, Cat.ELECTRONICS, "oneplus"),
        m("xiaomi", "Xiaomi", null, Cat.ELECTRONICS, "xiaomi"),
        m("pepperfry", "Pepperfry", null, Cat.FURNITURE, "pepperfry"),
        m("urbanladder", "Urban Ladder", null, Cat.FURNITURE, "urban ladder", "urbanladder"),
        m("homecentre", "Home Centre", null, Cat.FURNITURE, "home centre", "homecentre"),
        m("wakefit", "Wakefit", null, Cat.FURNITURE, "wakefit"),
        m("purplle", "Purplle", null, Cat.BEAUTY, "purplle"),
        m("mamaearth", "Mamaearth", null, Cat.BEAUTY, "mamaearth", "honasa"),
        m("sugarcosmetics", "SUGAR Cosmetics", null, Cat.BEAUTY, "sugar cosmetics"),
        m("hamleys", "Hamleys", null, Cat.KIDS_TOYS, "hamleys"),
        m("hopscotch", "Hopscotch", null, Cat.KIDS_CARE, "hopscotch"),
        m("mothercare", "Mothercare", null, Cat.KIDS_CARE, "mothercare"),
        m("decathlon", "Decathlon", null, Cat.SPORTS, "decathlon"),
        m("supertails", "Supertails", null, Cat.PETS, "supertails"),
        m("huft", "Heads Up For Tails", null, Cat.PETS, "heads up for tails"),
        m("fnp", "Ferns N Petals", null, Cat.GIFTS, "ferns n petals", "fernsnpetals", "fnp$"),
        m("igp", "IGP", null, Cat.GIFTS, "igp.com", "indian gifts portal"),
        m("uber", "Uber", "com.ubercab", Cat.CABS, "uber"),
        m("ola", "Ola", "com.olacabs.customer", Cat.CABS, "olacabs", "ola cabs", "ani technologies", "ola money"),
        m("rapido", "Rapido", "com.rapido.passenger", Cat.CABS, "rapido", "roppen"),
        m("nammayatri", "Namma Yatri", "in.juspay.nammayatri", Cat.CABS, "namma yatri", "nammayatri"),
        m("blusmart", "BluSmart", null, Cat.CABS, "blusmart"),
        m("indrive", "inDrive", null, Cat.CABS, "indrive", "indriver"),
        m("meru", "Meru Cabs", null, Cat.CABS, "meru cab", "meru travel"),
        m("irctc", "IRCTC", "cris.org.in.prs.ima", Cat.TRAVEL, "irctc"),
        m("redbus", "redBus", "in.redbus.android", Cat.TRAVEL, "redbus"),
        m("makemytrip", "MakeMyTrip", "com.makemytrip", Cat.TRAVEL, "makemytrip", "mmt"),
        m("goibibo", "Goibibo", "com.goibibo", Cat.TRAVEL, "goibibo"),
        m("cleartrip", "Cleartrip", "com.cleartrip.android", Cat.TRAVEL, "cleartrip"),
        m("indigo", "IndiGo", "in.goindigo.android", Cat.TRAVEL, "goindigo", "interglobe", "indigo air"),
        m("ixigo", "ixigo", "com.ixigo", Cat.TRAVEL, "ixigo", "le travenues"),
        m("clubmahindra", "Club Mahindra", "co.clubmahindra.mahindraholidays", Cat.HOLIDAY, "club mahindra", "clubmahindra", "mahindra holidays"),
        // Holiday packages. No app icons until each package name is checked on Google Play.
        m("sterlingholidays", "Sterling Holidays", null, Cat.HOLIDAY, "sterling holiday"),
        m("thomascook", "Thomas Cook", null, Cat.HOLIDAY, "thomas cook"),
        m("sotc", "SOTC", null, Cat.HOLIDAY, "sotc$"),
        // Cooking gas before the fuel brands: same companies and apps, but a bill, not fuel
        m("indane", "Indane Gas", "cx.indianoil.in", Cat.UTILITIES, "indane"),
        m("hpgas", "HP Gas", "com.drivetrackplusrefuel", Cat.UTILITIES, "hp gas", "hpgas"),
        m("bharatgas", "Bharatgas", "com.cgt.bharatgas", Cat.UTILITIES, "bharatgas", "bharat gas"),
        // Petrol pumps keep their own name (which pump) and get the brand's icon
        outlet("hpcl", "HPCL", "com.drivetrackplusrefuel", Cat.FUEL, "hpcl", "hindustan petroleum", "hp petrol", "hp pay", "hppay", "hp fuel"),
        outlet("bpcl", "Bharat Petroleum", "com.cgt.bharatgas", Cat.FUEL, "bpcl", "bharat petroleum", "bharatpetroleum", "hellobpcl", "ufill"),
        outlet("indianoil", "IndianOil", "cx.indianoil.in", Cat.FUEL, "indianoil", "indian oil", "iocl"),
        outlet("shell", "Shell", "com.shell.sitibv.shellgoplusindia", Cat.FUEL, "shell india", "shell markets", "shell petrol", "shell fuel", "shell station", "shell select", "shell energy"),
        // No app to show, but these are fuel (and Jio-bp must not be taken for Jio the phone company)
        outlet("jiobp", "Jio-bp", null, Cat.FUEL, "jio-bp", "jio bp", "jiobp", "reliance bp"),
        outlet("nayara", "Nayara", null, Cat.FUEL, "nayara", "essar petrol", "essar oil"),
        // Tolls: the NHAI annual pass, then any FASTag payment or toll plaza (which plaza is kept)
        m("rajmargyatra", "Rajmargyatra", "com.nhai.rajmargyatra", Cat.CAR_BIKE, "rajmargyatra"),
        outlet("fastag", "FASTag", "com.fastaguser", Cat.CAR_BIKE, "fastag", "toll plaza", "ihmcl", "netc$"),
        m("easemytrip", "EaseMyTrip", "com.easemytrip.android", Cat.TRAVEL, "easemytrip", "easy trip planners"),
        m("yatra", "Yatra", "com.yatra.base", Cat.TRAVEL, "yatra.com", "yatra online"),
        // Stays: Holiday (getting there stays Travel)
        m("agoda", "Agoda", "com.agoda.mobile.consumer", Cat.HOLIDAY, "agoda"),
        m("booking", "Booking.com", "com.booking", Cat.HOLIDAY, "booking.com"),
        m("airbnb", "Airbnb", "com.airbnb.android", Cat.HOLIDAY, "airbnb"),
        m("oyo", "OYO", "com.oyo.consumer", Cat.HOLIDAY, "oyo", "oravel"),
        m("netflix", "Netflix", "com.netflix.mediaclient", Cat.SUBSCRIPTIONS, "netflix"),
        m("spotify", "Spotify", "com.spotify.music", Cat.SUBSCRIPTIONS, "spotify"),
        m("hotstar", "JioHotstar", "in.startv.hotstar", Cat.SUBSCRIPTIONS, "hotstar", "jiohotstar", "jiocinema", "jio cinema", "jiostar", "novi digital"),
        m("sunnxt", "Sun NXT", "com.suntv.sunnxt", Cat.SUBSCRIPTIONS, "sunnxt", "sun nxt"),
        m("altt", "ALTT", "com.balaji.alt", Cat.SUBSCRIPTIONS, "altt$", "altbalaji", "alt balaji"),
        m("hoichoi", "hoichoi", "com.viewlift.hoichoi", Cat.SUBSCRIPTIONS, "hoichoi"),
        m("aha", "aha", "ahaflix.tv", Cat.SUBSCRIPTIONS, "ahaflix", "aha video", "arha media"),
        m("mxplayer", "MX Player", "com.mxtech.videoplayer.ad", Cat.SUBSCRIPTIONS, "mx player", "mxplayer"),
        m("tataplay", "Tata Play", "com.ryzmedia.tatasky", Cat.RECHARGE, "tata play", "tataplay", "tata sky", "tatasky"),
        m("airtelxstream", "Airtel Xstream", "tv.accedo.airtel.wynk", Cat.SUBSCRIPTIONS, "xstream"),
        m("applemusic", "Apple Music", "com.apple.android.music", Cat.SUBSCRIPTIONS, "apple music", "applemusic"),
        // iCloud, App Store and Apple TV+ charges ("APPLE MEDIA SERVICES"); a phone from an Apple shop is Apple Store
        m("applemedia", "Apple", null, Cat.SUBSCRIPTIONS, "apple media", "apple.com/bill", "itunes"),
        m("pocketfm", "Pocket FM", "com.radio.pocketfm", Cat.SUBSCRIPTIONS, "pocket fm", "pocketfm"),
        m("kukufm", "Kuku FM", "com.vlv.aravali", Cat.SUBSCRIPTIONS, "kuku fm", "kukufm", "kuku tv", "mebigo"),
        // Live sport and regional streaming. Common words (stage, waves, chaupal) only match with the company name.
        m("fancode", "FanCode", "com.dream11sportsguru", Cat.SUBSCRIPTIONS, "fancode", "sporta technologies"),
        m("cricbuzz", "Cricbuzz", "com.cricbuzz.android", Cat.SUBSCRIPTIONS, "cricbuzz"),
        m("waves", "WAVES", "com.prasarbharati.android", Cat.SUBSCRIPTIONS, "wavespb", "waves pb", "waves ott", "prasar bharati"),
        m("shemaroome", "ShemarooMe", "com.saranyu.shemarooworld", Cat.SUBSCRIPTIONS, "shemaroo"),
        m("stage", "STAGE", "in.stage", Cat.SUBSCRIPTIONS, "stage technologies", "stage ott", "stage.in"),
        m("chaupal", "Chaupal", "tv.chaupal.android", Cat.SUBSCRIPTIONS, "chaupal ott", "chaupal tv", "chaupal.tv", "bosna digital"),
        m("manoramamax", "manoramaMAX", "com.mmtv.manoramamax.android", Cat.SUBSCRIPTIONS, "manoramamax", "manorama max"),
        m("lionsgate", "Lionsgate Play", "com.lionsgateplay.videoapp", Cat.SUBSCRIPTIONS, "lionsgate"),
        m("ottplay", "OTTplay", "com.ht.ottplay", Cat.SUBSCRIPTIONS, "ottplay"),
        m("sonyliv", "SonyLIV", "com.sonyliv", Cat.SUBSCRIPTIONS, "sonyliv", "sony liv", "culver max"),
        m("zee5", "ZEE5", "com.graymatrix.did", Cat.SUBSCRIPTIONS, "zee5", "zee entertainment"),
        m("jiosaavn", "JioSaavn", "com.jio.media.jiobeats", Cat.SUBSCRIPTIONS, "jiosaavn", "saavn"),
        m("gaana", "Gaana", "com.gaana", Cat.SUBSCRIPTIONS, "gaana"),
        m("youtubemusic", "YouTube Music", "com.google.android.apps.youtube.music", Cat.SUBSCRIPTIONS, "youtube music", "youtubemusic"),
        m("youtube", "YouTube", "com.google.android.youtube", Cat.SUBSCRIPTIONS, "youtube", "google youtube"),
        m("googleone", "Google One", "com.google.android.apps.subscriptions.red", Cat.SUBSCRIPTIONS, "google one", "googleone"),
        // Books and stories (Play Books before Google Play, whose name it starts with)
        m("playbooks", "Google Play Books", "com.google.android.apps.books", Cat.EDUCATION, "google play books", "play books", "playbooks"),
        m("storytel", "Storytel", "grit.storytel.app", Cat.SUBSCRIPTIONS, "storytel"),
        m("pratilipi", "Pratilipi", "com.pratilipi.mobile.android", Cat.SUBSCRIPTIONS, "pratilipi", "nasadiya"),
        m("googleplay", "Google Play", "com.android.vending", Cat.SUBSCRIPTIONS, "google play", "googleplay", "play store"),
        m("linkedin", "LinkedIn", "com.linkedin.android", Cat.SUBSCRIPTIONS, "linkedin"),
        m("microsoft", "Microsoft", "com.microsoft.office.officehubrow", Cat.SUBSCRIPTIONS, "microsoft"),
        m("chatgpt", "ChatGPT", "com.openai.chatgpt", Cat.SUBSCRIPTIONS, "openai", "chatgpt"),
        m("claude", "Claude", "com.anthropic.claude", Cat.SUBSCRIPTIONS, "anthropic", "claude.ai"),
        m("perplexity", "Perplexity", "ai.perplexity.app.android", Cat.SUBSCRIPTIONS, "perplexity"),
        m("canva", "Canva", "com.canva.editor", Cat.SUBSCRIPTIONS, "canva$"),
        m("adobe", "Adobe", "com.adobe.reader", Cat.SUBSCRIPTIONS, "adobe"),
        m("notion", "Notion", "notion.id", Cat.SUBSCRIPTIONS, "notion.so", "notion labs", "notion$"),
        m("dropbox", "Dropbox", "com.dropbox.android", Cat.SUBSCRIPTIONS, "dropbox"),
        m("zoom", "Zoom", "us.zoom.videomeetings", Cat.SUBSCRIPTIONS, "zoom.us", "zoom video", "zoom communications"),
        m("grammarly", "Grammarly", "com.grammarly.android", Cat.SUBSCRIPTIONS, "grammarly"),
        m("truecaller", "Truecaller", "com.truecaller", Cat.SUBSCRIPTIONS, "truecaller"),
        m("tinder", "Tinder", "com.tinder", Cat.SUBSCRIPTIONS, "tinder"),
        m("bumble", "Bumble", "com.bumble.app", Cat.SUBSCRIPTIONS, "bumble$"),
        m("telegram", "Telegram", "org.telegram.messenger", Cat.SUBSCRIPTIONS, "telegram"),
        m("discord", "Discord", "com.discord", Cat.SUBSCRIPTIONS, "discord"),
        m("snapchat", "Snapchat", "com.snapchat.android", Cat.SUBSCRIPTIONS, "snapchat"),
        m("bookmyshow", "BookMyShow", "com.bt.bms", Cat.ENTERTAINMENT, "bookmyshow", "bigtree"),
        m("airtel", "Airtel", "com.myairtelapp", Cat.RECHARGE, "airtel"),
        m("vi", "Vi", "com.mventus.selfcare.activity", Cat.RECHARGE, "vodafone idea", "vodafone", "myvi"),
        m("jio", "Jio", "com.jio.myjio", Cat.RECHARGE, "reliance jio", "jio"),
        m("cred", "CRED", "com.dreamplug.androidapp", Cat.TRANSFERS, "cred.club", "cred club", "dreamplug"),
        m("paytm", "Paytm", "net.one97.paytm", Cat.OTHERS, "paytm"),
        m("phonepe", "PhonePe", "com.phonepe.app", Cat.OTHERS, "phonepe"),
        m("gpay", "Google Pay", "com.google.android.apps.nbu.paisa.user", Cat.OTHERS, "google pay", "gpay"),
        m("mobikwik", "MobiKwik", "com.mobikwik_new", Cat.OTHERS, "mobikwik"),
        m("bhim", "BHIM", "in.org.npci.upiapp", Cat.OTHERS, "bhim upi", "bhimupi"),
        m("whatsapp", "WhatsApp", "com.whatsapp", Cat.OTHERS, "whatsapp"),
        m("supermoney", "super.money", "money.super.payments", Cat.OTHERS, "super.money", "supermoney"),
        m("navi", "Navi", "com.naviapp", Cat.EMI, "navi finserv", "navi technologies", "naviapp"),
        m("freecharge", "Freecharge", "com.freecharge.android", Cat.OTHERS, "freecharge"),
        m("payzapp", "PayZapp", "com.enstage.wibmo.hdfc", Cat.OTHERS, "payzapp"),
        m("groww", "Groww", "com.nextbillion.groww", Cat.INVESTMENTS, "groww", "nextbillion"),
        m("zerodha", "Zerodha", "com.zerodha.kite3", Cat.INVESTMENTS, "zerodha"),
        m("indmoney", "INDmoney", "in.indwealth", Cat.INVESTMENTS, "indmoney", "indwealth"),
        m("pharmeasy", "PharmEasy", "com.phonegap.rxpal", Cat.MEDICINES, "pharmeasy"),
        m("tata1mg", "Tata 1mg", "com.aranoah.healthkart.plus", Cat.MEDICINES, "1mg", "healthkart"),
        m("apollo", "Apollo 24|7", "com.apollo.patientapp", Cat.MEDICINES, "apollo"),
        m("netmeds", "Netmeds", "com.NetmedsMarketplace.Netmeds", Cat.MEDICINES, "netmeds"),
        m("practo", "Practo", "com.practo.fabric", Cat.DOCTOR, "practo"),
        m("cultfit", "cult.fit", "fit.cure.android", Cat.SPORTS, "cult.fit", "cultfit", "curefit"),
        m("healthifyme", "HealthifyMe", "com.healthifyme.basic", Cat.SPORTS, "healthifyme", "healthify"),
        m("headspace", "Headspace", "com.getsomeheadspace.android", Cat.SUBSCRIPTIONS, "headspace"),
        m("calm", "Calm", "com.calm.android", Cat.SUBSCRIPTIONS, "calm.com"),
        m("strava", "Strava", "com.strava", Cat.SPORTS, "strava"),
        // Insurance: aggregators, then insurers. Health insurers count as Health, the rest as Bills.
        m("policybazaar", "Policybazaar", "com.policybazaar", Cat.INSURANCE, "policybazaar", "policy bazaar", "pb fintech", "pbfintech"),
        m("policyx", "PolicyX", "com.policyx.policyx", Cat.INSURANCE, "policyx"),
        m("digit", "Digit Insurance", "com.godigit.digit", Cat.INSURANCE, "go digit", "godigit", "digit insurance", "digit general", "digit life"),
        m("acko", "ACKO", "com.acko.android", Cat.INSURANCE, "acko$", "acko general", "acko tech"),
        m("lic", "LIC", "com.lic.liccustomer", Cat.INSURANCE, "lic of india", "licindia", "life insurance corp"),
        m("hdfcergo", "HDFC ERGO", "com.hdfcergo.here", Cat.INSURANCE, "hdfc ergo", "hdfcergo"),
        m("icicilombard", "ICICI Lombard", "icici.lombard.ghi", Cat.INSURANCE, "icici lombard", "icicilombard"),
        m("tataaig", "Tata AIG", "com.tataaig.android", Cat.INSURANCE, "tata aig", "tataaig"),
        m("tataaia", "Tata AIA Life", "com.tataaialife", Cat.INSURANCE, "tata aia", "tataaia"),
        m("bajajgeneral", "Bajaj General Insurance", "com.ba.cp.controller", Cat.INSURANCE, "bajaj allianz general", "bajaj allianz gen", "bajaj general"),
        m("hdfclife", "HDFC Life", "com.hdfcclife.activities", Cat.INSURANCE, "hdfc life", "hdfclife", "hdfc standard life"),
        m("sbilife", "SBI Life", "com.sbilife.smartcare", Cat.INSURANCE, "sbi life", "sbilife"),
        m("icicipru", "ICICI Prudential Life", "com.ipru.iciciprulife.customer.release", Cat.INSURANCE, "icici prudential life", "icici pru life", "iciciprulife"),
        m("axismaxlife", "Axis Max Life", "com.maxlifeinsurance.www.twa", Cat.INSURANCE, "max life$", "max life insurance", "maxlifeinsurance"),
        m("starhealth", "Star Health", "com.star.customer_app", Cat.INSURANCE, "star health", "starhealth"),
        m("nivabupa", "Niva Bupa", "com.maxbupa.healthapp", Cat.INSURANCE, "niva bupa", "nivabupa", "max bupa", "maxbupa"),
        m("carehealth", "Care Health Insurance", "com.religare.healthinsurance", Cat.INSURANCE, "care health insurance", "care health ins", "religare health", "careinsurance"),
        m("byjus", "BYJU'S", "com.byjus.thelearningapp", Cat.KIDS_EDUCATION, "byju", "think and learn"),
        m("unacademy", "Unacademy", "com.unacademyapp", Cat.EDUCATION, "unacademy", "sorting hat"),
        m("coursera", "Coursera", "org.coursera.android", Cat.EDUCATION, "coursera"),
        m("udemy", "Udemy", "com.udemy.android", Cat.EDUCATION, "udemy"),
        m("duolingo", "Duolingo", "com.duolingo", Cat.EDUCATION, "duolingo"),
        m("dominos", "Domino's", "com.Dominos", Cat.FOOD_DELIVERY, "dominos", "domino's", "jubilant foodworks"),
        m("eatsure", "EatSure", "com.done.faasos", Cat.FOOD_DELIVERY, "eatsure", "faasos", "rebel foods"),
        m("urbancompany", "Urban Company", "com.urbanclap.urbanclap", Cat.HOME_HELP, "urban company", "urbanclap"),
    )

    val packageNames: List<String> = all.mapNotNull { it.packageName }.distinct()

    private val compiled: List<Pair<KnownMerchant, List<Regex>>> =
        all.map { km -> km to km.patterns.map(::compile) }

    private fun compile(pattern: String): Regex {
        val whole = pattern.endsWith("$")
        val text = Regex.escape(pattern.removeSuffix("$"))
        return Regex("""(?i)(?:^|[^a-z0-9])""" + text + if (whole) """(?![a-z0-9])""" else "")
    }

    // The @handle of a UPI ID names the payer's app, not the payee: ramesh@freecharge is a person.
    private val UPI_HANDLE = Regex("""@[a-z0-9.\-]+""")

    fun match(merchantRaw: String?, vpa: String?): KnownMerchant? {
        val hay = listOfNotNull(merchantRaw, vpa).joinToString(" ").lowercase().replace(UPI_HANDLE, " ")
        if (hay.isBlank()) return null
        return compiled.firstOrNull { (_, regs) -> regs.any { it.containsMatchIn(hay) } }?.first
    }

    /** Stable key used to group a merchant and to attach rules: known key, else a slug of the cleaned name. */
    fun keyFor(known: KnownMerchant?, displayName: String?, vpa: String?): String =
        known?.takeUnless { it.keepName && !displayName.isNullOrBlank() }?.key
            ?: displayName?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')?.takeIf { it.isNotEmpty() }
            ?: vpa?.lowercase()
            ?: "unknown"
}

/**
 * Merchants whose payments can be for anything: stores that sell everything, and payment gateways whose name stands
 * in for whichever shop was paid. Their SMS ("WWW AMAZON IN", "PAYU") never says what was bought, so changing one
 * payment's category doesn't make a rule for all of them unless the user turns it on.
 */
object MixedStores {
    val keys: Set<String> = setOf(
        "amazon", "flipkart", "shopsy", "jiomart", "tataneu", "tatacliq", "dmart", "meesho", "snapdeal",
        "paytm", "payu", "razorpay", "billdesk", "ccavenue", "cashfree",
    )

    fun isMixed(merchantKey: String): Boolean = merchantKey.lowercase() in keys
}

/** Every Android app whose icon Owl Tracker may show: recognised merchants, banks, cards and wallets. */
object KnownApps {
    val packageNames: List<String> = (MerchantRegistry.packageNames + Institutions.packageNames).distinct()

    private val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

    /** Whether [s] is shaped like an Android package name (for icons chosen by hand, e.g. any UPI app). */
    fun isPackageName(s: String?): Boolean = s != null && s.length <= 150 && PACKAGE.matches(s)

    /** A name for the app when Android can't give its label. */
    fun nameFor(pkg: String): String =
        MerchantRegistry.all.firstOrNull { it.packageName == pkg }?.displayName ?: Institutions.nameForApp(pkg) ?: pkg
}
