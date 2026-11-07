package com.owl.tracker.core

/**
 * Scrubs text before it leaves the phone in a backup. Amounts, dates, merchant names, categories
 * and notes are the user's own data and stay; anything that identifies an account, a phone number,
 * a UPI ID or a bank reference does not.
 */
object Privacy {

    /** Runs of 5 or more digits: phone numbers, account and card numbers, UPI and bank references. */
    private val LONG_DIGITS = Regex("""\d{5,}""")

    /** A UPI ID such as 98xxxxxx10@ybl or name.surname@okhdfcbank. */
    private val VPA = Regex("""[A-Za-z0-9._\-]{2,}@[A-Za-z][A-Za-z0-9.]{1,}""")

    /** Replaces every run of 5 or more digits with •••••. */
    fun maskDigits(s: String): String = LONG_DIGITS.replace(s) { "•••••" }

    /**
     * A merchant name safe to export: a UPI ID keeps only the part before "@" (for a person's VPA
     * that is the name they chose), and long digit runs are masked.
     */
    fun merchant(name: String): String {
        val noVpa = VPA.replace(name) { it.value.substringBefore('@') }
        return maskDigits(noVpa).trim().ifEmpty { "UPI payment" }
    }

    /** The grouping key (slug) for a merchant, scrubbed the same way. */
    fun merchantKey(key: String): String =
        maskDigits(key.substringBefore('@')).replace("•", "x").trim().ifEmpty { "unknown" }

    /** True when [s] still holds anything [maskDigits] or [merchant] would remove. */
    fun looksSensitive(s: String): Boolean = LONG_DIGITS.containsMatchIn(s) || VPA.containsMatchIn(s)
}
