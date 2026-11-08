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
    const val SPORTS = "sports"
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

    /**
     * Order matters: more specific names (Instamart, Prime Video) come before broader ones (Swiggy, Amazon).
     * A pattern matches from the start of a word; one ending in $ must also end where a word ends, so
     * "canva$" finds CANVA but not CANVAS PRINTS.
     */
    val all: List<KnownMerchant> = listOf(
        m("swiggy_instamart", "Swiggy Instamart", "in.swiggy.android", Cat.GROCERIES, "swiggyinstamart", "instamart"),
        m("swiggy", "Swiggy", "in.swiggy.android", Cat.FOOD, "swiggy", "bundl"),
        m("zomato", "Zomato", "com.application.zomato", Cat.FOOD, "zomato"),
        m("blinkit", "Blinkit", "com.grofers.customerapp", Cat.GROCERIES, "blinkit", "grofers"),
        m("zepto", "Zepto", "com.zeptoconsumerapp", Cat.GROCERIES, "zepto", "kiranakart"),
        m("bigbasket", "BigBasket", "com.bigbasket.mobileapp", Cat.GROCERIES, "bigbasket", "bb now", "bbnow", "supermarket grocery supplies"),
        m("jiomart", "JioMart", "com.jpl.jiomart", Cat.GROCERIES, "jiomart"),
        m("dmart", "DMart Ready", "in.dmart", Cat.GROCERIES, "dmart", "avenue supermarts", "avenue e-commerce"),
        m("licious", "Licious", "com.licious", Cat.GROCERIES, "licious", "delightful gourmet"),
        m("primevideo", "Prime Video", "com.amazon.avod.thirdpartyclient", Cat.ENTERTAINMENT, "primevideo", "prime video"),
        m("kindle", "Kindle", "com.amazon.kindle", Cat.EDUCATION, "kindle"),
        m("audible", "Audible", "com.audible.application", Cat.RECHARGE, "audible"),
        m("amazonmusic", "Amazon Music", "com.amazon.mp3", Cat.RECHARGE, "amazon music", "amazonmusic"),
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
        m("hotstar", "JioHotstar", "in.startv.hotstar", Cat.RECHARGE, "hotstar", "jiohotstar", "jiocinema", "jio cinema", "jiostar", "novi digital"),
        m("sunnxt", "Sun NXT", "com.suntv.sunnxt", Cat.RECHARGE, "sunnxt", "sun nxt"),
        m("altt", "ALTT", "com.balaji.alt", Cat.RECHARGE, "altt$", "altbalaji", "alt balaji"),
        m("hoichoi", "hoichoi", "com.viewlift.hoichoi", Cat.RECHARGE, "hoichoi"),
        m("aha", "aha", "ahaflix.tv", Cat.RECHARGE, "ahaflix", "aha video", "arha media"),
        m("mxplayer", "MX Player", "com.mxtech.videoplayer.ad", Cat.RECHARGE, "mx player", "mxplayer"),
        m("tataplay", "Tata Play", "com.ryzmedia.tatasky", Cat.RECHARGE, "tata play", "tataplay", "tata sky", "tatasky"),
        m("airtelxstream", "Airtel Xstream", "tv.accedo.airtel.wynk", Cat.RECHARGE, "xstream"),
        m("applemusic", "Apple Music", "com.apple.android.music", Cat.RECHARGE, "apple music", "applemusic"),
        m("pocketfm", "Pocket FM", "com.radio.pocketfm", Cat.RECHARGE, "pocket fm", "pocketfm"),
        m("kukufm", "Kuku FM", "com.vlv.aravali", Cat.RECHARGE, "kuku fm", "kukufm", "kuku tv", "mebigo"),
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
        m("claude", "Claude", "com.anthropic.claude", Cat.RECHARGE, "anthropic", "claude.ai"),
        m("perplexity", "Perplexity", "ai.perplexity.app.android", Cat.RECHARGE, "perplexity"),
        m("canva", "Canva", "com.canva.editor", Cat.RECHARGE, "canva$"),
        m("adobe", "Adobe", "com.adobe.reader", Cat.RECHARGE, "adobe"),
        m("notion", "Notion", "notion.id", Cat.RECHARGE, "notion.so", "notion labs", "notion$"),
        m("dropbox", "Dropbox", "com.dropbox.android", Cat.RECHARGE, "dropbox"),
        m("zoom", "Zoom", "us.zoom.videomeetings", Cat.RECHARGE, "zoom.us", "zoom video", "zoom communications"),
        m("grammarly", "Grammarly", "com.grammarly.android", Cat.RECHARGE, "grammarly"),
        m("truecaller", "Truecaller", "com.truecaller", Cat.RECHARGE, "truecaller"),
        m("tinder", "Tinder", "com.tinder", Cat.RECHARGE, "tinder"),
        m("bumble", "Bumble", "com.bumble.app", Cat.RECHARGE, "bumble$"),
        m("telegram", "Telegram", "org.telegram.messenger", Cat.RECHARGE, "telegram"),
        m("discord", "Discord", "com.discord", Cat.RECHARGE, "discord"),
        m("snapchat", "Snapchat", "com.snapchat.android", Cat.RECHARGE, "snapchat"),
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
        m("pharmeasy", "PharmEasy", "com.phonegap.rxpal", Cat.HEALTH, "pharmeasy"),
        m("tata1mg", "Tata 1mg", "com.aranoah.healthkart.plus", Cat.HEALTH, "1mg", "healthkart"),
        m("apollo", "Apollo 24|7", "com.apollo.patientapp", Cat.HEALTH, "apollo"),
        m("netmeds", "Netmeds", "com.NetmedsMarketplace.Netmeds", Cat.HEALTH, "netmeds"),
        m("practo", "Practo", "com.practo.fabric", Cat.HEALTH, "practo"),
        m("cultfit", "cult.fit", "fit.cure.android", Cat.SPORTS, "cult.fit", "cultfit", "curefit"),
        m("healthifyme", "HealthifyMe", "com.healthifyme.basic", Cat.HEALTH, "healthifyme", "healthify"),
        m("headspace", "Headspace", "com.getsomeheadspace.android", Cat.HEALTH, "headspace"),
        m("calm", "Calm", "com.calm.android", Cat.HEALTH, "calm.com"),
        m("strava", "Strava", "com.strava", Cat.SPORTS, "strava"),
        // Insurance: aggregators, then insurers. Health insurers count as Health, the rest as Bills.
        m("policybazaar", "Policybazaar", "com.policybazaar", Cat.BILLS, "policybazaar", "policy bazaar", "pb fintech", "pbfintech"),
        m("policyx", "PolicyX", "com.policyx.policyx", Cat.BILLS, "policyx"),
        m("digit", "Digit Insurance", "com.godigit.digit", Cat.BILLS, "go digit", "godigit", "digit insurance", "digit general", "digit life"),
        m("acko", "ACKO", "com.acko.android", Cat.BILLS, "acko$", "acko general", "acko tech"),
        m("lic", "LIC", "com.lic.liccustomer", Cat.BILLS, "lic of india", "licindia", "life insurance corp"),
        m("hdfcergo", "HDFC ERGO", "com.hdfcergo.here", Cat.BILLS, "hdfc ergo", "hdfcergo"),
        m("icicilombard", "ICICI Lombard", "icici.lombard.ghi", Cat.BILLS, "icici lombard", "icicilombard"),
        m("tataaig", "Tata AIG", "com.tataaig.android", Cat.BILLS, "tata aig", "tataaig"),
        m("tataaia", "Tata AIA Life", "com.tataaialife", Cat.BILLS, "tata aia", "tataaia"),
        m("bajajgeneral", "Bajaj General Insurance", "com.ba.cp.controller", Cat.BILLS, "bajaj allianz general", "bajaj allianz gen", "bajaj general"),
        m("hdfclife", "HDFC Life", "com.hdfcclife.activities", Cat.BILLS, "hdfc life", "hdfclife", "hdfc standard life"),
        m("sbilife", "SBI Life", "com.sbilife.smartcare", Cat.BILLS, "sbi life", "sbilife"),
        m("icicipru", "ICICI Prudential Life", "com.ipru.iciciprulife.customer.release", Cat.BILLS, "icici prudential life", "icici pru life", "iciciprulife"),
        m("axismaxlife", "Axis Max Life", "com.maxlifeinsurance.www.twa", Cat.BILLS, "max life$", "max life insurance", "maxlifeinsurance"),
        m("starhealth", "Star Health", "com.star.customer_app", Cat.HEALTH, "star health", "starhealth"),
        m("nivabupa", "Niva Bupa", "com.maxbupa.healthapp", Cat.HEALTH, "niva bupa", "nivabupa", "max bupa", "maxbupa"),
        m("carehealth", "Care Health Insurance", "com.religare.healthinsurance", Cat.HEALTH, "care health insurance", "care health ins", "religare health", "careinsurance"),
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
        known?.key
            ?: displayName?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')?.takeIf { it.isNotEmpty() }
            ?: vpa?.lowercase()
            ?: "unknown"
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
