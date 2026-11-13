package com.owl.tracker.sms

import com.owl.tracker.data.MerchantOverride
import com.owl.tracker.data.NewTxn
import com.owl.tracker.data.Repository
import com.owl.tracker.data.Source
import com.owl.tracker.core.Categorizer
import com.owl.tracker.core.Deduper
import com.owl.tracker.core.Direction
import com.owl.tracker.core.ImportFilters
import com.owl.tracker.core.Institutions
import com.owl.tracker.core.KnownMerchant
import com.owl.tracker.core.MerchantRegistry
import com.owl.tracker.core.ParseResult
import com.owl.tracker.core.ParsedTransaction
import com.owl.tracker.core.RejectReason
import com.owl.tracker.core.SampleSms
import com.owl.tracker.core.SmsParser
import com.owl.tracker.core.TxKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Reads the inbox, keeps only bank/card/UPI transactions, applies the user's import choices,
 * removes duplicates and writes the result. Personal messages and OTPs are dropped in memory and
 * never stored.
 */
class ImportEngine(
    private val sms: SmsSource,
    private val repo: Repository,
    private val clock: Clock,
    /** The installed app of a bank (by sender id), if any: a card bill payment shows that bank's icon. */
    private val bankApp: (String?) -> String? = { null },
) {
    private val zone: ZoneId get() = clock.zone

    data class Candidate(
        val tx: ParsedTransaction,
        val known: KnownMerchant?,
        val merchantName: String,
        val merchantKey: String,
    ) {
        val smsId: Long get() = tx.smsId
    }

    data class SenderGroup(val id: String, val name: String, val detail: String)

    /**
     * A message the parser left out, kept in memory only for the "See what was skipped" list.
     * Messages from people keep neither sender nor text, and OTPs keep no text.
     */
    data class Skipped(val sender: String?, val reason: RejectReason, val body: String?, val timestamp: Long)

    /** One read of the inbox covering the longest range the user can pick (12 months). */
    data class Scan(
        val candidates: List<Candidate>,   // newest first
        val messagesRead: List<Long>,      // timestamps of every message read, for "ignored" counts
        val groups: List<SenderGroup>,
        val skipped: List<Skipped> = emptyList(),  // newest first
    )

    /**
     * Reads the inbox, or with [sample] the 21 built-in example messages instead (for trying the app,
     * store screenshots and Play review). Sample messages get negative ids so they can never collide
     * with a real message's id.
     */
    suspend fun scan(months: Int = MAX_MONTHS, sample: Boolean = false): Scan {
        val since = LocalDate.now(clock).minusMonths(months.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val messages = if (sample) {
            SampleSms.messages(clock.millis()).map { it.copy(id = -it.id) }.filter { it.timestamp >= since }
        } else {
            sms.read(since)
        }
        val candidates = ArrayList<Candidate>()
        val skipped = ArrayList<Skipped>()
        for (m in messages) {
            when (val r = SmsParser.parse(m)) {
                is ParseResult.Accepted -> candidates += toCandidate(r.tx)
                is ParseResult.Rejected -> skipped += Skipped(
                    sender = if (r.reason == RejectReason.PERSONAL_SENDER) null else m.address,
                    reason = r.reason,
                    body = if (r.reason == RejectReason.PERSONAL_SENDER || r.reason == RejectReason.OTP) null else m.body,
                    timestamp = m.timestamp,
                )
            }
        }
        candidates.sortByDescending { it.tx.timestamp }
        skipped.sortByDescending { it.timestamp }
        val groups = candidates.groupBy { it.tx.senderGroup }.map { (id, list) ->
            val name = list.first().tx.institutionName ?: "Unrecognised senders"
            val accounts = list.mapNotNull { c -> c.tx.accountLast4?.let { (if (c.tx.isCard) "Card ••" else "A/c ••") + it } }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }
            val headers = list.map { it.tx.sender }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val detail = if (id == Institutions.UNRECOGNISED) "${headers.size} senders · may not be transactions"
            else (accounts + listOfNotNull(headers.firstOrNull()?.key)).joinToString(" · ")
            SenderGroup(id, name, detail)
        }.sortedWith(compareBy<SenderGroup> { it.id == Institutions.UNRECOGNISED }.thenByDescending { g -> candidates.count { it.tx.senderGroup == g.id } })
        return Scan(candidates, messages.map { it.timestamp }, groups, skipped)
    }

    fun toCandidate(t: ParsedTransaction): Candidate {
        val known = MerchantRegistry.match(t.merchantRaw, t.vpa)
        val name = known?.takeUnless { it.keepName && t.merchantRaw != null }?.displayName
            ?: t.merchantRaw
            ?: when (t.kind) {
                TxKind.CASH_WITHDRAWAL -> "ATM withdrawal"
                TxKind.CARD_BILL_PAYMENT -> "Card bill payment"
                TxKind.SALARY -> "Salary"
                else -> t.institutionName ?: "Unknown"
            }
        return Candidate(t, known, name, MerchantRegistry.keyFor(known, t.merchantRaw ?: name, t.vpa))
    }

    fun rangeStart(filters: ImportFilters): Long =
        filters.startDate(LocalDate.now(clock)).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Candidates that pass the filters and the date range, newest first. */
    fun selectable(scan: Scan, filters: ImportFilters): List<Candidate> {
        val from = rangeStart(filters)
        return scan.candidates.filter { it.tx.timestamp >= from && filters.accepts(it.tx) }
    }

    fun messagesInRange(scan: Scan, filters: ImportFilters, group: String): Int {
        val from = rangeStart(filters)
        return scan.candidates.count { it.tx.senderGroup == group && it.tx.timestamp >= from }
    }

    fun ignoredInRange(scan: Scan, filters: ImportFilters): Int {
        val from = rangeStart(filters)
        val read = scan.messagesRead.count { it >= from }
        val tx = scan.candidates.count { it.tx.timestamp >= from }
        return (read - tx).coerceAtLeast(0)
    }

    fun skippedInRange(scan: Scan, filters: ImportFilters): List<Skipped> {
        val from = rangeStart(filters)
        return scan.skipped.filter { it.timestamp >= from }
    }

    fun monthOf(c: Candidate): YearMonth = YearMonth.from(Instant.ofEpochMilli(c.tx.timestamp).atZone(zone))

    data class Result(val saved: Int, val needsReview: Int, val duplicates: Int, val leftOut: Int, val removed: Int, val byGroup: Map<String, Int>)

    /**
     * Writes the chosen candidates. With [replaceRange] (re-import from Settings), SMS transactions in the
     * range that are no longer chosen are removed first; transactions you edited and still chose are kept as they are.
     */
    suspend fun commit(
        chosen: List<Candidate>,
        filters: ImportFilters,
        leftOut: Int,
        replaceRange: Boolean,
        sample: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val from = rangeStart(filters)
        val removed = if (replaceRange) repo.removeSmsTransactionsNotIn(from, chosen.map { it.smsId }.toSet()) else 0
        val known = repo.existingSmsIds()
        val existing = repo.smsTransactionsSince(from - Deduper.WINDOW_MS)
        val deduper = Deduper(existing.map { Deduper.DedupeEntry(it.amountPaise, it.direction, it.occurredAt, it.accountLabel?.filter { ch -> ch.isDigit() }?.takeLast(4), it.refNo) })
        val categorizer = Categorizer(repo.rules())
        val chosenNames = repo.merchantOverrides()

        var duplicates = 0
        val rows = ArrayList<NewTxn>()
        // Oldest first, so the first copy of a duplicated payment is the one kept.
        for (c in chosen.sortedBy { it.tx.timestamp }) {
            if (c.smsId in known) continue
            if (!deduper.offer(Deduper.entryOf(c.tx))) { duplicates++; continue }
            rows += c.toNewTxn(categorizer, chosenNames)
        }
        var saved = 0
        val chunks = rows.chunked(100)
        chunks.forEachIndexed { i, chunk ->
            saved += repo.insertAll(chunk)
            onProgress((i + 1).toFloat() / chunks.size)
        }
        if (chunks.isEmpty()) onProgress(1f)

        val latest = chosen.maxOfOrNull { it.tx.timestamp } ?: 0L
        repo.updateSettings { s ->
            if (sample) {
                // Sample data never turns on inbox sync.
                s.copy(filters = filters, smsEnabled = false, sampleData = true, parserVersion = SmsParser.VERSION)
            } else {
                // A full import reads everything with today's parser: nothing to look back for later
                s.copy(
                    filters = filters,
                    smsEnabled = true,
                    lastSyncAt = clock.millis(),
                    lastSmsTimestamp = maxOf(s.lastSmsTimestamp, latest, clock.millis() - 60_000),
                    parserVersion = SmsParser.VERSION,
                )
            }
        }
        return Result(
            saved = saved,
            needsReview = rows.count { it.needsReview },
            duplicates = duplicates,
            leftOut = leftOut,
            removed = removed,
            byGroup = rows.groupingBy { it.senderGroup ?: Institutions.UNRECOGNISED }.eachCount(),
        )
    }

    /**
     * Incremental sync with the saved filters: on app open, pull to refresh and every 6 hours.
     * Returns how many new transactions were added.
     */
    suspend fun syncNew(): Int {
        val s = repo.settings()
        if (s.onboarded) { fixMoneyIn(); dropReminders() }
        if (!s.onboarded || !s.smsEnabled || !sms.hasPermission()) return 0
        val rangeFrom = rangeStart(s.filters)
        val since = maxOf(rangeFrom, s.lastSmsTimestamp - OVERLAP_MS)
        // After an update that reads more kinds of message, look back over the whole import period once, but only
        // for messages of the new kinds: anything an older version read and the user has since deleted stays deleted.
        val catchUp = s.parserVersion < SmsParser.VERSION
        val messages = sms.read(if (catchUp) rangeFrom else since)
        val known = repo.existingSmsIds()
        val fresh = messages.filter { it.id !in known }
            .mapNotNull { (SmsParser.parse(it) as? ParseResult.Accepted)?.tx }
            .filter { it.timestamp >= since || it.format > s.parserVersion }
            .filter { s.filters.accepts(it) }
            .map { toCandidate(it) }
        var added = 0
        if (fresh.isNotEmpty()) {
            val oldest = minOf(since, fresh.minOf { it.tx.timestamp })
            val existing = repo.smsTransactionsSince(oldest - Deduper.WINDOW_MS)
            val deduper = Deduper(existing.map { Deduper.DedupeEntry(it.amountPaise, it.direction, it.occurredAt, it.accountLabel?.filter { ch -> ch.isDigit() }?.takeLast(4), it.refNo) })
            val categorizer = Categorizer(repo.rules())
            val chosenNames = repo.merchantOverrides()
            val rows = fresh.sortedBy { it.tx.timestamp }.filter { deduper.offer(Deduper.entryOf(it.tx)) }.map { it.toNewTxn(categorizer, chosenNames) }
            added = repo.insertAll(rows)
        }
        // Payments imported before this version learned their app (or before a bank app was installed)
        repo.fillMissingIcons()
        val latest = messages.maxOfOrNull { it.timestamp } ?: s.lastSmsTimestamp
        repo.updateSettings { it.copy(lastSyncAt = clock.millis(), lastSmsTimestamp = maxOf(it.lastSmsTimestamp, latest), parserVersion = SmsParser.VERSION) }
        return added
    }

    /**
     * Payments imported before 0.8.0 whose message says the money came to you ("sent to your A/c",
     * "paid to you by") but were stored as spending. Reads only the stored text of those payments.
     * Their category moves to what money in gets, unless the user had picked one. Returns how many changed.
     */
    suspend fun fixMoneyIn(): Int {
        val rows = repo.smsMoneyOutMentioningYou()
        if (rows.isEmpty()) return 0
        val categorizer = Categorizer(repo.rules())
        var fixed = 0
        for (t in rows) {
            val parsed = (SmsParser.parse(com.owl.tracker.core.SmsMessage(t.smsId ?: 0L, t.sender ?: continue, t.smsBody ?: continue, t.occurredAt)) as? ParseResult.Accepted)?.tx
                ?: continue
            if (parsed.direction != Direction.CREDIT) continue
            val c = toCandidate(parsed)
            val asSpending = categorizer.categorize(c.merchantKey, parsed.merchantRaw, parsed.vpa, Direction.DEBIT, parsed.kind, c.known, parsed.body)
            val userPicked = t.categoryId != asSpending
            repo.setMoneyIn(t.id, if (userPicked) null else categorizer.categorize(parsed, c.merchantKey, c.known))
            fixed++
        }
        return fixed
    }

    /**
     * Payment reminders that versions before 1.5.2 stored as spending ("Rs.12923 EMI … is due on 07-Nov-26 …
     * Ignore if paid."). Reads only the stored text of payments that mention "due", "paid" or "to be"; removes the
     * ones the parser now skips as reminders, and remembers them as deleted so no later scan brings them back.
     * Returns how many were removed.
     */
    suspend fun dropReminders(): Int {
        var dropped = 0
        for (t in repo.smsMaybeReminders()) {
            val r = SmsParser.parse(com.owl.tracker.core.SmsMessage(t.smsId ?: 0L, t.sender ?: continue, t.smsBody ?: continue, t.occurredAt))
            if ((r as? ParseResult.Rejected)?.reason != RejectReason.FUTURE_OR_REMINDER) continue
            if (repo.deleteTransaction(t.id) != null) dropped++
        }
        return dropped
    }

    /**
     * For the Rules screen: the payments a rule covers (still in its category, not split) and the category each
     * would get without it, from the other rules and the usual defaults. Only those that would change.
     */
    suspend fun usualCategories(rule: com.owl.tracker.data.RuleInfo): Map<Long, String> {
        val others = repo.rules().filterNot { it.match == rule.match && it.pattern.equals(rule.pattern, ignoreCase = true) }
        val categorizer = Categorizer(others)
        return repo.ruleCovered(rule).associate { t ->
            val known = MerchantRegistry.all.firstOrNull { it.key == t.merchantKey } ?: MerchantRegistry.match(t.merchantName, t.vpa)
            t.id to categorizer.categorize(t.merchantKey, t.merchantName, t.vpa, t.direction, t.kind, known, t.smsBody ?: "")
        }.filterValues { it != rule.categoryId }
    }

    /** [chosen]: names and icons the user picked for merchants; they win over what the SMS says. */
    private fun Candidate.toNewTxn(categorizer: Categorizer, chosen: Map<String, MerchantOverride>): NewTxn {
        val t = tx
        val mine = chosen[merchantKey]
        val account = t.accountLast4?.let { last4 ->
            val prefix = t.institutionName ?: "Account"
            if (t.isCard) "$prefix card ••$last4" else "$prefix ••$last4"
        }
        return NewTxn(
            amountPaise = t.amountPaise,
            direction = t.direction,
            occurredAt = t.timestamp,
            merchantName = mine?.name ?: merchantName,
            merchantKey = merchantKey,
            merchantPackage = when (val logo = mine?.logo) {
                null -> known?.packageName ?: if (t.kind == TxKind.CARD_BILL_PAYMENT) bankApp(t.senderGroup) else null
                "" -> null
                else -> logo
            },
            vpa = t.vpa,
            categoryId = categorizer.categorize(t, merchantKey, known),
            accountLabel = account,
            senderGroup = t.senderGroup,
            sender = t.sender,
            refNo = t.reference,
            source = Source.SMS,
            smsId = t.smsId,
            needsReview = t.needsReview,
            kind = t.kind,
            smsBody = t.body,
        )
    }

    companion object {
        const val MAX_MONTHS = 12
        private const val OVERLAP_MS = 24 * 60 * 60_000L

        /** A cash or other manual expense. */
        fun manual(amountPaise: Long, merchant: String, categoryId: String, occurredAt: Long, direction: Direction = Direction.DEBIT): NewTxn {
            val name = merchant.trim()
            return NewTxn(
                amountPaise = amountPaise, direction = direction, occurredAt = occurredAt, merchantName = name,
                merchantKey = MerchantRegistry.keyFor(null, name, null), merchantPackage = MerchantRegistry.match(name, null)?.packageName,
                vpa = null, categoryId = categoryId, accountLabel = "Cash", senderGroup = null, sender = null, refNo = null,
                source = Source.MANUAL, smsId = null, needsReview = false,
            )
        }
    }
}
