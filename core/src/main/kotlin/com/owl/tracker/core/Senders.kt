package com.owl.tracker.core

/** TRAI header suffix since May 2025: -T transactional, -S service, -P promotional, -G government. */
enum class HeaderType { TRANSACTIONAL, SERVICE, PROMOTIONAL, GOVERNMENT, UNMARKED }

data class SenderHeader(val raw: String, val entity: String, val type: HeaderType)

object SenderParser {
    // VM-HDFCBK-S, AD-SBIUPI, JD-ICICIT-T: operator and circle prefix, DLT entity, TRAI suffix
    private val DLT = Regex("""^([A-Za-z]{2})-([A-Za-z0-9]{3,9})(?:-([A-Za-z]))?$""")
    // HDFCBK-S: prefix stripped, suffix kept
    private val NO_PREFIX = Regex("""^([A-Za-z0-9]{3,9})-([A-Za-z])$""")
    // AXHDFCBK: prefix joined to the entity without a hyphen
    private val JOINED = Regex("""^([A-Za-z]{2})([A-Za-z0-9]{6})$""")
    // HDFCBK, SBIUPI: just the entity. Needs a letter; digits-only senders are phone numbers or short codes.
    private val BARE = Regex("""^[A-Za-z0-9]{3,9}$""")

    /**
     * Returns null for phone numbers and short codes: those are personal messages. In India only
     * registered businesses can send from a name, so any 3-9 character sender with a letter is a header.
     */
    fun parse(address: String?): SenderHeader? {
        val a = address?.trim()?.removePrefix("+")?.trim().orEmpty()
        if (a.isEmpty() || a.none { it.isLetter() }) return null
        DLT.matchEntire(a)?.let { m -> return SenderHeader(a, m.groupValues[2].uppercase(), typeOf(m.groupValues[3])) }
        NO_PREFIX.matchEntire(a)?.let { m -> return SenderHeader(a, m.groupValues[1].uppercase(), typeOf(m.groupValues[2])) }
        JOINED.matchEntire(a)?.let { m ->
            val entity = m.groupValues[2].uppercase()
            if (Institutions.forEntity(entity) != null) return SenderHeader(a, entity, HeaderType.UNMARKED)
        }
        if (BARE.matches(a)) return SenderHeader(a, a.uppercase(), HeaderType.UNMARKED)
        return null
    }

    private fun typeOf(suffix: String) = when (suffix.uppercase()) {
        "T" -> HeaderType.TRANSACTIONAL
        "S" -> HeaderType.SERVICE
        "P" -> HeaderType.PROMOTIONAL
        "G" -> HeaderType.GOVERNMENT
        else -> HeaderType.UNMARKED
    }
}

enum class InstitutionKind { BANK, CARD, WALLET }

/**
 * [apps]: the institution's own Android apps, most used first. The first one installed on the phone gives
 * the bank's icon. Like merchant apps, each must be listed in the app manifest's <queries> block.
 */
data class Institution(val id: String, val name: String, val kind: InstitutionKind, val apps: List<String> = emptyList())

/**
 * Known sender entities. Matching is by prefix or contains on the 6-character DLT entity,
 * so HDFCBK, HDFCBN and HDFCCC all map to HDFC Bank. Extend this list as you meet new senders.
 */
object Institutions {
    const val UNRECOGNISED = "unknown"

    private data class Entry(val pattern: Regex, val institution: Institution)

    private fun e(pattern: String, id: String, name: String, kind: InstitutionKind = InstitutionKind.BANK, vararg apps: String) =
        Entry(Regex(pattern), Institution(id, name, kind, apps.toList()))

    private val BANK = InstitutionKind.BANK

    private val entries = listOf(
        e("^SBICRD|^SBICARD|^SBMCRD", "sbicard", "SBI Card", InstitutionKind.CARD, "com.ge.capital.konysbiapp"),
        e("^HDFC", "hdfc", "HDFC Bank", BANK, "com.hdfcbank.android.now", "com.snapwork.hdfc", "com.enstage.wibmo.hdfc"),
        e("^ICICI", "icici", "ICICI Bank", BANK, "com.csam.icici.bank.imobile"),
        e("SBI", "sbi", "SBI", BANK, "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus"),
        e("^AXIS", "axis", "Axis Bank", BANK, "com.axis.mobile"),
        e("^KOTAK|^KOTAKB", "kotak", "Kotak Mahindra Bank", BANK, "com.msf.kbank.mobile"),
        e("^PAYTM", "paytm", "Paytm Payments Bank", InstitutionKind.WALLET, "net.one97.paytm"),
        e("^PHONPE|^PHONEPE", "phonepe", "PhonePe", InstitutionKind.WALLET, "com.phonepe.app"),
        e("^AMZPAY|^AMAZONP", "amazonpay", "Amazon Pay", InstitutionKind.WALLET, "in.amazon.mShop.android.shopping"),
        e("^MOBIKW", "mobikwik", "MobiKwik", InstitutionKind.WALLET, "com.mobikwik_new"),
        e("^AIRBNK|^AIRTLB", "airtelbank", "Airtel Payments Bank", BANK, "com.myairtelapp"),
        e("^IDFC", "idfc", "IDFC FIRST Bank", BANK, "com.idfcfirstbank.optimus"),
        e("^YESB", "yes", "Yes Bank", BANK, "in.irisbyyes.app", "com.atomyes"),
        e("^INDUS", "indusind", "IndusInd Bank", BANK, "com.indusind.indie", "com.fss.indus"),
        e("^PNB", "pnb", "Punjab National Bank", BANK, "com.Version1"),
        e("^BOIIND|^BOISMS|^BOI", "boi", "Bank of India", BANK, "com.boi.ua.android", "com.boi.mpay"),
        e("^BOB|^BARODA", "bob", "Bank of Baroda", BANK, "com.bankofbaroda.mconnect"),
        e("^CANB", "canara", "Canara Bank", BANK, "com.canarabank.mobility"),
        e("^UNION|^UBIN", "union", "Union Bank of India", BANK, "com.infrasoft.uboi"),
        e("^AUBANK|^AUSFB", "au", "AU Small Finance Bank", BANK, "com.ausmallfinancebank.amb"),
        e("^FEDB|^FEDBNK", "federal", "Federal Bank", BANK, "com.fedmobile"),
        e("^IDBI", "idbi", "IDBI Bank", BANK, "com.snapwork.IDBI"),
        e("^IOB", "iob", "Indian Overseas Bank", BANK, "com.iob.iobconnect", "com.fss.iob6"),
        e("^UCO", "uco", "UCO Bank", BANK, "com.lcode.ucomobilebanking"),
        e("^CENTB|^CBOI", "central", "Central Bank of India", BANK, "com.centralbank.retail.omni", "com.infrasofttech.CentralBank"),
        e("^INDBNK|^INDIAN", "indianbank", "Indian Bank", BANK, "com.iexceed.ib.digitalbankingprod", "com.IndianBank.IndOASIS"),
        e("^RBL", "rbl", "RBL Bank", BANK, "com.rblbank.mobank"),
        e("^BANDHN", "bandhan", "Bandhan Bank", BANK, "com.bandhan.mBandhan", "com.bandhan.mBandhanN"),
        e("^SCB|^SCBANK|^STANC", "scb", "Standard Chartered", BANK, "air.app.scb.breeze.android.main.in.prod"),
        // Citi's Indian cards and accounts moved to Axis Bank; its old app is not listed
        e("^CITI", "citi", "Citibank"),
        e("^AMEX", "amex", "American Express", InstitutionKind.CARD, "com.americanexpress.android.acctsvcs.in"),
        e("^ONECRD|^ONECARD", "onecard", "OneCard", InstitutionKind.CARD, "com.creditcard.onecard"),
        e("^SLICE", "slice", "slice", InstitutionKind.CARD, "indwin.c3.shareapp"),
    )

    fun forEntity(entity: String): Institution? = entries.firstOrNull { it.pattern.containsMatchIn(entity) }?.institution

    /** The institution's apps, most used first; empty when unknown. */
    fun appsFor(id: String?): List<String> = entries.firstOrNull { it.institution.id == id }?.institution?.apps.orEmpty()

    /** Every bank, card and wallet app we may show the icon of. */
    val packageNames: List<String> = entries.flatMap { it.institution.apps }.distinct()

    /** The institution an app belongs to, for naming it when its label can't be read. */
    fun nameForApp(pkg: String): String? = entries.firstOrNull { pkg in it.institution.apps }?.institution?.name

    fun nameFor(id: String?): String = when (id) {
        null, UNRECOGNISED -> "Unrecognised senders"
        else -> entries.firstOrNull { it.institution.id == id }?.institution?.name ?: id
    }
}
