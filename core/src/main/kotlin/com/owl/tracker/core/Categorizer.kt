package com.owl.tracker.core

enum class RuleMatch { MERCHANT, VPA, KEYWORD }

/** A user's "always use Food for Swiggy". User rules always beat the built-in defaults. */
data class CategoryRule(val match: RuleMatch, val pattern: String, val categoryKey: String)

/**
 * Picks a payment's category: the user's own rules first, then what kind of payment it is, the merchant if it's one
 * Owl Tracker knows, words in its name, and the kind of shop its name says. With [kids] off (someone who doesn't pay
 * for children) nothing goes to the Kids categories: school fees go to Courses & Books, classes to Sports & Fitness.
 */
class Categorizer(private val userRules: List<CategoryRule> = emptyList(), private val kids: Boolean = true) {

    fun categorize(tx: ParsedTransaction, merchantKey: String, known: KnownMerchant?): String =
        categorize(merchantKey, tx.merchantRaw, tx.vpa, tx.direction, tx.kind, known, tx.body)

    fun categorize(
        merchantKey: String,
        merchantName: String?,
        vpa: String?,
        direction: Direction,
        kind: TxKind,
        known: KnownMerchant?,
        body: String = "",
    ): String {
        userRules.firstOrNull { r ->
            when (r.match) {
                RuleMatch.MERCHANT -> r.pattern.equals(merchantKey, ignoreCase = true)
                RuleMatch.VPA -> vpa != null && r.pattern.equals(vpa, ignoreCase = true)
                RuleMatch.KEYWORD -> listOfNotNull(merchantName, vpa).joinToString(" ").contains(r.pattern, ignoreCase = true)
            }
        }?.let { return it.categoryKey }
        return builtIn(merchantName, vpa, direction, kind, known, body).let { if (kids) it else CategoryGroups.withoutKids(it) }
    }

    private fun builtIn(merchantName: String?, vpa: String?, direction: Direction, kind: TxKind, known: KnownMerchant?, body: String): String {
        when (kind) {
            TxKind.CASH_WITHDRAWAL -> return Cat.CASH
            TxKind.CARD_BILL_PAYMENT -> return Cat.TRANSFERS
            TxKind.SALARY -> return Cat.INCOME
            TxKind.INVESTMENT -> return Cat.INVESTMENTS
            TxKind.REFUND -> return known?.category ?: keywordCategory(merchantName, vpa, body) ?: Cat.OTHERS
            TxKind.NORMAL -> Unit
        }
        known?.let { return it.category }
        keywordCategory(merchantName, vpa, body)?.let { return it }
        return if (direction == Direction.CREDIT) Cat.INCOME else Cat.OTHERS
    }

    private fun keywordCategory(merchantName: String?, vpa: String?, body: String): String? {
        val hay = listOfNotNull(merchantName, vpa).joinToString(" ").lowercase()
        if (hay.isNotBlank()) {
            KEYWORDS.firstOrNull { (re, _) -> re.containsMatchIn(hay) }?.let { return it.second }
            // A shop with no app, by what its name says it sells ("Sri Lakshmi Jewellers", "Vision Opticals")
            ShopKinds.of(merchantName)?.let { return SHOP_CATEGORY.getValue(it) }
            SHOPPING.firstOrNull { (re, _) -> re.containsMatchIn(hay) }?.let { return it.second }
        }
        val b = body.lowercase()
        return BODY_KEYWORDS.firstOrNull { (re, _) -> re.containsMatchIn(b) }?.second
    }

    companion object {
        private fun k(pattern: String, cat: String) = Regex(pattern) to cat

        private val SHOP_CATEGORY = mapOf(
            ShopKind.JEWELLERY to Cat.JEWELLERY, ShopKind.CLOTHING to Cat.FASHION, ShopKind.BOOKS to Cat.EDUCATION,
            ShopKind.PHARMACY to Cat.MEDICINES, ShopKind.OPTICALS to Cat.FASHION, ShopKind.BAKERY to Cat.DINING,
            ShopKind.SALON to Cat.BEAUTY, ShopKind.MOBILES to Cat.ELECTRONICS, ShopKind.HARDWARE to Cat.FURNITURE,
            ShopKind.CLINIC to Cat.DOCTOR,
        )

        private val KEYWORDS = listOf(
            k("""petrol|fuel|hpcl|bpcl|iocl|indian ?oil|bharat petroleum|hindustan petroleum|\bshell\b|filling station|service station""", Cat.FUEL),
            k("""insurance|assurance""", Cat.INSURANCE),
            k("""recharge|prepaid|postpaid|vodafone|\bvi\b|dth|tata play|sun direct|broadband|fibernet|act fibernet""", Cat.RECHARGE),
            k("""electricity|\bpower\b|bescom|tneb|tangedco|msedcl|bses|tata power|water board|water supply|\bgas\b|bbps|billdesk""", Cat.UTILITIES),
            k("""pharma|medical|chemist|medplus|druggist""", Cat.MEDICINES),
            k("""hospital|clinic|diagnostic|\blabs?\b|dental|doctor""", Cat.DOCTOR),
            // Before Food: "Hotel & Resort" is a stay. A plain "hotel" stays Food: in India it is often a restaurant.
            k("""\bresorts?\b|home ?stay|\bholidays?\b|airbnb|booking\.com|agoda|\boyo\b|treebo|fabhotel|zostel|thomas cook|\bsotc\b""", Cat.HOLIDAY),
            k("""restaurant|\bcafe\b|coffee|bakery|bhavan|biryani|pizza|burger|dhaba|sweets|kitchen|\bfoods?\b|canteen|chai|tea stall|hotel""", Cat.DINING),
            k("""\bmart\b|grocery|groceries|supermarket|kirana|\bfresh\b|dmart|more retail|spencer|provision""", Cat.GROCERIES),
            // Children's school and care. Classes and academies could be anyone's, so they stay with Sports or Courses.
            k("""school|vidyalaya|vidyalay|vidya mandir|vidyashram|kindergarten|montessori|tuition|tution""", Cat.KIDS_EDUCATION),
            k("""abacus|summer camp|\bkids\b|tiny tots""", Cat.KIDS_ACTIVITIES),
            k("""day ?care|creche|\btoys?\b""", Cat.KIDS_CARE),
            k("""\bsports?\b|\bturf\b|playo|hudle|badminton|cricket|football|futsal|tennis|pickleball|\bswim|stadium|\bgym\b|gymnasium|fitness|\byoga\b|marathon|skating|bowling|\bgolf\b|decathlon""", Cat.SPORTS),
            k("""metro|railway|\bbus\b|\bcab\b|taxi|\bauto\b""", Cat.COMMUTE),
            k("""fastag|parking|\btoll\b|car wash|\btyres?\b|garage|service cent(?:er|re)""", Cat.CAR_BIKE),
            k("""airline|indigo|air india|vistara|akasa|spicejet|travels""", Cat.TRAVEL),
            k("""college|university|academy|course|udemy|coursera""", Cat.EDUCATION),
            k("""\brent\b|landlord|housing society|maintenance""", Cat.RENT),
            k("""\bemi\b|\bloan\b|bajaj fin|hdb financial|home credit""", Cat.EMI),
            k("""\bsip\b|mutual fund|zerodha|groww|upstox|\bnps\b|\bppf\b|indian clearing""", Cat.INVESTMENTS),
            k("""cinema|\bpvr\b|inox|movies|theatre|gaming""", Cat.ENTERTAINMENT),
            k("""laundry|dry ?clean|housekeeping|pest control|plumb|electrician|carpenter""", Cat.HOME_HELP),
            k("""temple|mandir|devasthan|tirumala|gurudwara|gurdwara|\bchurch\b|masjid|dargah|charitable|charity|donation""", Cat.TEMPLE),
            k("""\bpets?\b|veterinary|\bvet\b|pet ?shop""", Cat.PETS),
            k("""\bgifts?\b|florist|flowers""", Cat.GIFTS),
        )

        /** After the kind of shop: a store or retailer that could sell anything waits in Shopping to be sorted. */
        private val SHOPPING = listOf(
            k("""fashion|garment|textile|footwear|\bshoes\b|apparel|boutique""", Cat.FASHION),
            k("""electronics|croma|reliance digital|computers""", Cat.ELECTRONICS),
            k("""furniture|furnishing|mattress|home decor""", Cat.FURNITURE),
            k("""cosmetic|beauty""", Cat.BEAUTY),
            k("""store|retail|lifestyle|trends|shoppers""", Cat.SHOPPING),
        )
        private val BODY_KEYWORDS = listOf(
            k("""\bemi\b""", Cat.EMI),
            k("""\bsip\b|mutual fund""", Cat.INVESTMENTS),
            k("""\brent\b""", Cat.RENT),
        )
    }
}

/**
 * Drops the second copy of the same payment: banks and UPI apps often both send an SMS.
 * Two messages are the same payment when their references match, or (without references) when
 * amount and direction match within five minutes and the account numbers don't contradict.
 */
class Deduper(existing: Collection<DedupeEntry> = emptyList()) {

    data class DedupeEntry(
        val amountPaise: Long,
        val direction: Direction,
        val timestamp: Long,
        val last4: String?,
        val reference: String?,
    )

    private val seen = ArrayList(existing)

    fun isDuplicate(e: DedupeEntry): Boolean = seen.any { s -> same(s, e) }

    /** Returns true if [e] is new (and remembers it), false if it duplicates something seen. */
    fun offer(e: DedupeEntry): Boolean {
        if (isDuplicate(e)) return false
        seen.add(e)
        return true
    }

    private fun same(a: DedupeEntry, b: DedupeEntry): Boolean {
        if (a.reference != null && b.reference != null) return a.reference == b.reference
        if (a.amountPaise != b.amountPaise || a.direction != b.direction) return false
        if (kotlin.math.abs(a.timestamp - b.timestamp) > WINDOW_MS) return false
        return a.last4 == null || b.last4 == null || a.last4 == b.last4
    }

    companion object {
        const val WINDOW_MS = 5 * 60_000L
        fun entryOf(t: ParsedTransaction) = DedupeEntry(t.amountPaise, t.direction, t.timestamp, t.accountLast4, t.reference)
    }
}
