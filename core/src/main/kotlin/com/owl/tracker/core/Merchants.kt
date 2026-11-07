package com.owl.tracker.core

/** Default category keys. The app seeds these; users can rename, recolor or add their own. */
object Cat {
    const val FOOD = "food"
    const val GROCERIES = "groceries"
    const val SHOPPING = "shopping"
    const val TRANSPORT = "transport"
    const val FUEL = "fuel"
    const val BILLS = "bills"
    const val RECHARGE = "recharge"
    const val ENTERTAINMENT = "entertainment"
    const val HEALTH = "health"
    const val TRAVEL = "travel"
    const val EDUCATION = "education"
    const val RENT = "rent"
    const val EMI = "emi"
    const val INVESTMENTS = "investments"
    const val CASH = "cash"
    const val TRANSFERS = "transfers"
    const val OTHERS = "others"
    const val INCOME = "income"
}

/**
 * A merchant we recognise. [packageName] is the Android app whose icon is shown when that app is
 * installed on the phone; every package listed here must also be listed in the app manifest's
 * <queries> block, or Android 11+ hides it from us.
 */
data class KnownMerchant(
    val key: String,
    val displayName: String,
    val packageName: String?,
    val category: String,
    val patterns: List<String>,
)

object MerchantRegistry {

    private fun m(key: String, name: String, pkg: String?, cat: String, vararg patterns: String) =
        KnownMerchant(key, name, pkg, cat, patterns.toList())

    /** Order matters: more specific names (Instamart, Prime Video) come before broader ones (Swiggy, Amazon). */
    val all: List<KnownMerchant> = listOf(
        m("swiggy_instamart", "Swiggy Instamart", "in.swiggy.android", Cat.GROCERIES, "swiggyinstamart", "instamart"),
        m("swiggy", "Swiggy", "in.swiggy.android", Cat.FOOD, "swiggy", "bundl"),
        m("zomato", "Zomato", "com.application.zomato", Cat.FOOD, "zomato"),
        m("blinkit", "Blinkit", "com.grofers.customerapp", Cat.GROCERIES, "blinkit", "grofers"),
        m("zepto", "Zepto", "com.zeptoconsumerapp", Cat.GROCERIES, "zepto", "kiranakart"),
        m("bigbasket", "BigBasket", "com.bigbasket.mobileapp", Cat.GROCERIES, "bigbasket", "supermarket grocery supplies"),
        m("jiomart", "JioMart", "com.jpl.jiomart", Cat.GROCERIES, "jiomart"),
        m("dmart", "DMart Ready", "in.dmart", Cat.GROCERIES, "dmart", "avenue supermarts", "avenue e-commerce"),
        m("licious", "Licious", "com.licious", Cat.GROCERIES, "licious", "delightful gourmet"),
        m("primevideo", "Prime Video", "com.amazon.avod.thirdpartyclient", Cat.ENTERTAINMENT, "primevideo", "prime video"),
        m("kindle", "Kindle", "com.amazon.kindle", Cat.EDUCATION, "kindle"),
        m("audible", "Audible", "com.audible.application", Cat.RECHARGE, "audible"),
        m("amazon", "Amazon", "in.amazon.mShop.android.shopping", Cat.SHOPPING, "amazon", "amzn"),
        m("shopsy", "Shopsy", "com.flipkart.shopsy", Cat.SHOPPING, "shopsy"),
        m("flipkart", "Flipkart", "com.flipkart.android", Cat.SHOPPING, "flipkart", "fkrt"),
        m("myntra", "Myntra", "com.myntra.android", Cat.SHOPPING, "myntra"),
        m("ajio", "AJIO", "com.ril.ajio", Cat.SHOPPING, "ajio"),
        m("nykaa", "Nykaa", "com.fsn.nykaa", Cat.SHOPPING, "nykaa"),
        m("meesho", "Meesho", "com.meesho.supply", Cat.SHOPPING, "meesho"),
        m("tatacliq", "Tata CLiQ", "com.tul.tatacliq", Cat.SHOPPING, "tatacliq", "tata cliq"),
        m("lenskart", "Lenskart", "com.lenskart.app", Cat.SHOPPING, "lenskart"),
        m("snapdeal", "Snapdeal", "com.snapdeal.main", Cat.SHOPPING, "snapdeal"),
        m("firstcry", "FirstCry", "fc.admin.fcexpressadmin", Cat.SHOPPING, "firstcry", "brainbees"),
        m("ikea", "IKEA", "com.ingka.ikea.app", Cat.SHOPPING, "ikea"),
        m("tataneu", "Tata Neu", "com.tatadigital.tcp", Cat.SHOPPING, "tata neu", "tataneu", "tata digital"),
        m("uber", "Uber", "com.ubercab", Cat.TRANSPORT, "uber"),
        m("ola", "Ola", "com.olacabs.customer", Cat.TRANSPORT, "olacabs", "ola cabs", "ani technologies", "ola money"),
        m("rapido", "Rapido", "com.rapido.passenger", Cat.TRANSPORT, "rapido", "roppen"),
        m("nammayatri", "Namma Yatri", "in.juspay.nammayatri", Cat.TRANSPORT, "namma yatri", "nammayatri"),
        m("irctc", "IRCTC", "cris.org.in.prs.ima", Cat.TRAVEL, "irctc"),
        m("redbus", "redBus", "in.redbus.android", Cat.TRAVEL, "redbus"),
        m("makemytrip", "MakeMyTrip", "com.makemytrip", Cat.TRAVEL, "makemytrip", "mmt"),
        m("goibibo", "Goibibo", "com.goibibo", Cat.TRAVEL, "goibibo"),
        m("cleartrip", "Cleartrip", "com.cleartrip.android", Cat.TRAVEL, "cleartrip"),
        m("indigo", "IndiGo", "in.goindigo.android", Cat.TRAVEL, "goindigo", "interglobe", "indigo air"),
        m("ixigo", "ixigo", "com.ixigo", Cat.TRAVEL, "ixigo", "le travenues"),
        m("easemytrip", "EaseMyTrip", "com.easemytrip.android", Cat.TRAVEL, "easemytrip", "easy trip planners"),
        m("yatra", "Yatra", "com.yatra.base", Cat.TRAVEL, "yatra.com", "yatra online"),
        m("agoda", "Agoda", "com.agoda.mobile.consumer", Cat.TRAVEL, "agoda"),
        m("booking", "Booking.com", "com.booking", Cat.TRAVEL, "booking.com"),
        m("airbnb", "Airbnb", "com.airbnb.android", Cat.TRAVEL, "airbnb"),
        m("oyo", "OYO", "com.oyo.consumer", Cat.TRAVEL, "oyo", "oravel"),
        m("netflix", "Netflix", "com.netflix.mediaclient", Cat.RECHARGE, "netflix"),
        m("spotify", "Spotify", "com.spotify.music", Cat.RECHARGE, "spotify"),
        m("hotstar", "JioHotstar", "in.startv.hotstar", Cat.RECHARGE, "hotstar", "jiohotstar", "novi digital"),
        m("sonyliv", "SonyLIV", "com.sonyliv", Cat.RECHARGE, "sonyliv", "sony liv", "culver max"),
        m("zee5", "ZEE5", "com.graymatrix.did", Cat.RECHARGE, "zee5", "zee entertainment"),
        m("jiosaavn", "JioSaavn", "com.jio.media.jiobeats", Cat.RECHARGE, "jiosaavn", "saavn"),
        m("gaana", "Gaana", "com.gaana", Cat.RECHARGE, "gaana"),
        m("youtubemusic", "YouTube Music", "com.google.android.apps.youtube.music", Cat.RECHARGE, "youtube music", "youtubemusic"),
        m("youtube", "YouTube", "com.google.android.youtube", Cat.RECHARGE, "youtube", "google youtube"),
        m("googleone", "Google One", "com.google.android.apps.subscriptions.red", Cat.RECHARGE, "google one", "googleone"),
        m("googleplay", "Google Play", "com.android.vending", Cat.RECHARGE, "google play", "googleplay", "play store"),
        m("linkedin", "LinkedIn", "com.linkedin.android", Cat.RECHARGE, "linkedin"),
        m("microsoft", "Microsoft", "com.microsoft.office.officehubrow", Cat.RECHARGE, "microsoft"),
        m("chatgpt", "ChatGPT", "com.openai.chatgpt", Cat.RECHARGE, "openai", "chatgpt"),
        m("bookmyshow", "BookMyShow", "com.bt.bms", Cat.ENTERTAINMENT, "bookmyshow", "bigtree"),
        m("airtel", "Airtel", "com.myairtelapp", Cat.RECHARGE, "airtel"),
        m("vi", "Vi", "com.mventus.selfcare.activity", Cat.RECHARGE, "vodafone idea", "vodafone", "myvi"),
        m("jio", "Jio", "com.jio.myjio", Cat.RECHARGE, "reliance jio", "jio"),
        m("cred", "CRED", "com.dreamplug.androidapp", Cat.TRANSFERS, "cred.club", "cred club", "dreamplug"),
        m("paytm", "Paytm", "net.one97.paytm", Cat.OTHERS, "paytm"),
        m("phonepe", "PhonePe", "com.phonepe.app", Cat.OTHERS, "phonepe"),
        m("gpay", "Google Pay", "com.google.android.apps.nbu.paisa.user", Cat.OTHERS, "google pay", "gpay"),
        m("mobikwik", "MobiKwik", "com.mobikwik_new", Cat.OTHERS, "mobikwik"),
        m("groww", "Groww", "com.nextbillion.groww", Cat.INVESTMENTS, "groww", "nextbillion"),
        m("zerodha", "Zerodha", "com.zerodha.kite3", Cat.INVESTMENTS, "zerodha"),
        m("indmoney", "INDmoney", "in.indwealth", Cat.INVESTMENTS, "indmoney", "indwealth"),
        m("pharmeasy", "PharmEasy", "com.phonegap.rxpal", Cat.HEALTH, "pharmeasy"),
        m("tata1mg", "Tata 1mg", "com.aranoah.healthkart.plus", Cat.HEALTH, "1mg", "healthkart"),
        m("apollo", "Apollo 24|7", "com.apollo.patientapp", Cat.HEALTH, "apollo"),
        m("netmeds", "Netmeds", "com.NetmedsMarketplace.Netmeds", Cat.HEALTH, "netmeds"),
        m("practo", "Practo", "com.practo.fabric", Cat.HEALTH, "practo"),
        m("cultfit", "cult.fit", "fit.cure.android", Cat.HEALTH, "cult.fit", "cultfit", "curefit"),
        m("healthifyme", "HealthifyMe", "com.healthifyme.basic", Cat.HEALTH, "healthifyme", "healthify"),
        m("byjus", "BYJU'S", "com.byjus.thelearningapp", Cat.EDUCATION, "byju", "think and learn"),
        m("unacademy", "Unacademy", "com.unacademyapp", Cat.EDUCATION, "unacademy", "sorting hat"),
        m("coursera", "Coursera", "org.coursera.android", Cat.EDUCATION, "coursera"),
        m("udemy", "Udemy", "com.udemy.android", Cat.EDUCATION, "udemy"),
        m("duolingo", "Duolingo", "com.duolingo", Cat.EDUCATION, "duolingo"),
        m("dominos", "Domino's", "com.Dominos", Cat.FOOD, "dominos", "domino's", "jubilant foodworks"),
        m("eatsure", "EatSure", "com.done.faasos", Cat.FOOD, "eatsure", "faasos", "rebel foods"),
        m("urbancompany", "Urban Company", "com.urbanclap.urbanclap", Cat.BILLS, "urban company", "urbanclap"),
    )

    val packageNames: List<String> = all.mapNotNull { it.packageName }.distinct()

    private val compiled: List<Pair<KnownMerchant, List<Regex>>> =
        all.map { km -> km to km.patterns.map { Regex("""(?i)(?:^|[^a-z0-9])""" + Regex.escape(it)) } }

    fun match(merchantRaw: String?, vpa: String?): KnownMerchant? {
        val hay = listOfNotNull(merchantRaw, vpa).joinToString(" ").lowercase()
        if (hay.isBlank()) return null
        return compiled.firstOrNull { (_, regs) -> regs.any { it.containsMatchIn(hay) } }?.first
    }

    /** Stable key used to group a merchant and to attach rules: known key, else a slug of the cleaned name. */
    fun keyFor(known: KnownMerchant?, displayName: String?, vpa: String?): String =
        known?.key
            ?: displayName?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')?.takeIf { it.isNotEmpty() }
            ?: vpa?.lowercase()
            ?: "unknown"
}
