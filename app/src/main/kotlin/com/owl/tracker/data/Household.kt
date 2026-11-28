package com.owl.tracker.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A household of up to four people, each on their own phone, with no server: they swap small files (through
 * WhatsApp, Quick Share, anything). One person starts it and sends an invite holding the household's key; each
 * member then sends updates, encrypted with that key, that the others open in Owl Tracker.
 *
 * An update carries, for the last [Households.DAYS] days, what the sender spent each day by category group, and,
 * only if they chose to share every payment, the payments themselves (shop, amount, date, category: never card or
 * account numbers, SMS text or UPI IDs). Kept in settings; never in backups (the key and other people's spending
 * stay on this phone).
 */
data class Household(
    val id: String,
    val name: String,
    /** The household's AES-256 key, base64. */
    val key: String,
    val meId: String,
    val meName: String,
    /** Whether my updates carry every payment (otherwise totals by category only). */
    val shareAll: Boolean,
    /** The household's monthly budget (0: none), and when it was last set, by whoever set it last. */
    val budgetPaise: Long,
    val budgetAt: Long,
    val others: List<HouseMember>,
    val lastSentAt: Long = 0,
)

/** Another member, as of their latest update. */
data class HouseMember(
    val id: String,
    val name: String,
    /** When they made the update this comes from (0: none yet, only known from the invite). */
    val updatedAt: Long,
    val sharesAll: Boolean,
    /** What they spent each day, by category group id. */
    val days: Map<LocalDate, Map<String, Long>>,
    /** Names of their category groups that aren't built in. */
    val names: Map<String, String>,
    val payments: List<HousePayment>,
)

data class HousePayment(val date: LocalDate, val merchant: String, val paise: Long, val category: String)

/** What a file from another member turned out to be. */
sealed interface HouseFile {
    data class Invite(val id: String, val name: String, val key: String, val fromId: String, val fromName: String, val budgetPaise: Long, val budgetAt: Long) : HouseFile
    data class Update(val householdId: String, val iv: String, val data: String) : HouseFile
}

class HouseholdException(val problem: Problem) : Exception(problem.name) {
    enum class Problem { NOT_HOUSEHOLD, OTHER_HOUSEHOLD, DAMAGED, TOO_NEW, FULL, OWN }
}

object Households {
    const val MAX_PEOPLE = 4
    const val DAYS = 62L
    const val MAX_PAYMENTS = 1500
    const val MIME = "application/octet-stream"
    private const val MAGIC = "owltracker-household"
    private const val VERSION = 1

    private val random = SecureRandom()
    private fun token(bytes: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

    /** A new household that [meName] starts. */
    fun start(name: String, meName: String, shareAll: Boolean, budgetPaise: Long, now: Long) = Household(
        id = token(12), name = clean(name, 30).ifEmpty { "Home" }, key = Base64.getEncoder().encodeToString(ByteArray(32).also(random::nextBytes)),
        meId = token(9), meName = clean(meName, 20).ifEmpty { "Me" }, shareAll = shareAll, budgetPaise = budgetPaise.coerceIn(0, MAX_PAISE), budgetAt = if (budgetPaise > 0) now else 0,
        others = emptyList(),
    )

    /** Joining from [invite] as [meName]: the person who sent it is the first other member. */
    fun join(invite: HouseFile.Invite, meName: String, shareAll: Boolean) = Household(
        id = invite.id, name = invite.name, key = invite.key, meId = token(9), meName = clean(meName, 20).ifEmpty { "Me" }, shareAll = shareAll,
        budgetPaise = invite.budgetPaise, budgetAt = invite.budgetAt,
        others = listOf(HouseMember(invite.fromId, invite.fromName, 0, false, emptyMap(), emptyMap(), emptyList())),
    )

    fun invite(h: Household): ByteArray = JSONObject()
        .put("app", MAGIC).put("v", VERSION).put("kind", "invite")
        .put("id", h.id).put("name", h.name).put("key", h.key)
        .put("from", JSONObject().put("id", h.meId).put("name", h.meName))
        .put("budget", h.budgetPaise).put("budgetAt", h.budgetAt)
        .toString().toByteArray()

    /**
     * My update: [days] (what I spent each day, by group), [names] (my groups that aren't built in) and, when I
     * share them, [payments]. Encrypted with the household's key; the household id is bound in, so an update can't
     * be passed off as another household's.
     */
    fun update(h: Household, now: Long, days: Map<LocalDate, Map<String, Long>>, names: Map<String, String>, payments: List<HousePayment>): ByteArray {
        val body = JSONObject()
            .put("member", h.meId).put("name", h.meName).put("at", now).put("all", h.shareAll)
            .put("budget", h.budgetPaise).put("budgetAt", h.budgetAt)
            .put("names", JSONObject(names as Map<*, *>))
            .put("days", JSONObject().apply { days.forEach { (d, m) -> put(d.toString(), JSONObject(m as Map<*, *>)) } })
            .put("payments", JSONArray().apply {
                if (h.shareAll) payments.take(MAX_PAYMENTS).forEach { p -> put(JSONObject().put("d", p.date.toString()).put("m", p.merchant).put("p", p.paise).put("c", p.category)) }
            })
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(Base64.getDecoder().decode(h.key), "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(h.id.toByteArray())
        val data = cipher.doFinal(body.toString().toByteArray())
        return JSONObject().put("app", MAGIC).put("v", VERSION).put("kind", "update").put("id", h.id)
            .put("iv", Base64.getEncoder().encodeToString(iv)).put("data", Base64.getEncoder().encodeToString(data))
            .toString().toByteArray()
    }

    /** What a file is: an invite, or an update (not yet opened). */
    fun peek(bytes: ByteArray): HouseFile {
        val o = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: throw HouseholdException(HouseholdException.Problem.NOT_HOUSEHOLD)
        if (o.optString("app") != MAGIC) throw HouseholdException(HouseholdException.Problem.NOT_HOUSEHOLD)
        if (o.optInt("v", 0) > VERSION) throw HouseholdException(HouseholdException.Problem.TOO_NEW)
        val id = o.optString("id").takeIf { idOk(it) } ?: throw HouseholdException(HouseholdException.Problem.DAMAGED)
        return when (o.optString("kind")) {
            "invite" -> {
                val key = o.optString("key").takeIf { runCatching { Base64.getDecoder().decode(it).size == 32 }.getOrDefault(false) }
                    ?: throw HouseholdException(HouseholdException.Problem.DAMAGED)
                val from = o.optJSONObject("from") ?: throw HouseholdException(HouseholdException.Problem.DAMAGED)
                val fromId = from.optString("id").takeIf { idOk(it) } ?: throw HouseholdException(HouseholdException.Problem.DAMAGED)
                HouseFile.Invite(id, clean(o.optString("name"), 30).ifEmpty { "Home" }, key, fromId, clean(from.optString("name"), 20).ifEmpty { "?" },
                    o.optLong("budget", 0).coerceIn(0, MAX_PAISE), o.optLong("budgetAt", 0).coerceAtLeast(0))
            }
            "update" -> HouseFile.Update(id, o.optString("iv"), o.optString("data"))
            else -> throw HouseholdException(HouseholdException.Problem.NOT_HOUSEHOLD)
        }
    }

    /** Opens update [u] into [h]: the sender's member entry replaced (or added), and a newer household budget taken. */
    fun apply(h: Household, u: HouseFile.Update, today: LocalDate): Household {
        if (u.householdId != h.id) throw HouseholdException(HouseholdException.Problem.OTHER_HOUSEHOLD)
        val plain = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.getDecoder().decode(h.key), "AES"), GCMParameterSpec(128, Base64.getDecoder().decode(u.iv)))
            cipher.updateAAD(h.id.toByteArray())
            String(cipher.doFinal(Base64.getDecoder().decode(u.data)))
        }.getOrNull() ?: throw HouseholdException(HouseholdException.Problem.DAMAGED)
        val o = runCatching { JSONObject(plain) }.getOrNull() ?: throw HouseholdException(HouseholdException.Problem.DAMAGED)
        val member = o.optString("member").takeIf { idOk(it) } ?: throw HouseholdException(HouseholdException.Problem.DAMAGED)
        if (member == h.meId) throw HouseholdException(HouseholdException.Problem.OWN)
        val at = o.optLong("at", 0)
        val existing = h.others.firstOrNull { it.id == member }
        // An older update than the one already here changes nothing
        if (existing != null && existing.updatedAt >= at) return h
        if (existing == null && h.others.size >= MAX_PEOPLE - 1) throw HouseholdException(HouseholdException.Problem.FULL)
        val oldest = today.minusDays(DAYS)
        val days = mutableMapOf<LocalDate, Map<String, Long>>()
        o.optJSONObject("days")?.let { d ->
            d.keys().asSequence().take(DAYS.toInt() + 2).forEach { k ->
                val date = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@forEach
                if (date.isBefore(oldest) || date.isAfter(today.plusDays(1))) return@forEach
                val m = d.optJSONObject(k) ?: return@forEach
                days[date] = m.keys().asSequence().take(60).filter(::idOk).associateWith { m.optLong(it, 0).coerceIn(0, MAX_PAISE) }.filterValues { it > 0 }
            }
        }
        val names = o.optJSONObject("names")?.let { n -> n.keys().asSequence().take(60).filter(::idOk).associateWith { clean(n.optString(it), 40) }.filterValues { it.isNotEmpty() } } ?: emptyMap()
        val all = o.optBoolean("all", false)
        val payments = if (!all) emptyList() else o.optJSONArray("payments")?.let { a ->
            (0 until minOf(a.length(), MAX_PAYMENTS)).mapNotNull { i ->
                val p = a.optJSONObject(i) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(p.optString("d")) }.getOrNull()?.takeIf { !it.isBefore(oldest) } ?: return@mapNotNull null
                val cat = p.optString("c").takeIf(::idOk) ?: return@mapNotNull null
                HousePayment(date, clean(p.optString("m"), 60), p.optLong("p", 0).coerceIn(0, MAX_PAISE), cat)
            }
        } ?: emptyList()
        val m = HouseMember(member, clean(o.optString("name"), 20).ifEmpty { existing?.name ?: "?" }, at, all, days, names, payments)
        val budgetAt = o.optLong("budgetAt", 0)
        val newer = budgetAt > h.budgetAt
        return h.copy(
            others = if (existing == null) h.others + m else h.others.map { if (it.id == member) m else it },
            budgetPaise = if (newer) o.optLong("budget", 0).coerceIn(0, MAX_PAISE) else h.budgetPaise,
            budgetAt = if (newer) budgetAt else h.budgetAt,
        )
    }

    /** What member [m] spent between [from] and [until] (inclusive), by category group. */
    fun spentBetween(m: HouseMember, from: LocalDate, until: LocalDate): Map<String, Long> =
        m.days.filterKeys { !it.isBefore(from) && !it.isAfter(until) }.values
            .flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sum() }

    private const val MAX_PAISE = 100_000_000_00L
    private fun idOk(s: String) = s.isNotEmpty() && s.length <= 60 && s.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    private fun clean(s: String?, max: Int) = (s ?: "").filter { !it.isISOControl() }.trim().take(max)

    fun toJson(h: Household?): String {
        if (h == null) return ""
        return JSONObject()
            .put("id", h.id).put("name", h.name).put("key", h.key).put("meId", h.meId).put("meName", h.meName).put("all", h.shareAll)
            .put("budget", h.budgetPaise).put("budgetAt", h.budgetAt).put("sent", h.lastSentAt)
            .put("others", JSONArray().apply {
                h.others.forEach { m ->
                    put(
                        JSONObject().put("id", m.id).put("name", m.name).put("at", m.updatedAt).put("all", m.sharesAll)
                            .put("names", JSONObject(m.names as Map<*, *>))
                            .put("days", JSONObject().apply { m.days.forEach { (d, c) -> put(d.toString(), JSONObject(c as Map<*, *>)) } })
                            .put("payments", JSONArray().apply { m.payments.forEach { p -> put(JSONObject().put("d", p.date.toString()).put("m", p.merchant).put("p", p.paise).put("c", p.category)) } }),
                    )
                }
            }).toString()
    }

    fun fromJson(raw: String?): Household? = runCatching {
        if (raw.isNullOrBlank()) return null
        val o = JSONObject(raw)
        val others = o.optJSONArray("others")?.let { a ->
            (0 until minOf(a.length(), MAX_PEOPLE - 1)).mapNotNull { i ->
                val m = a.optJSONObject(i) ?: return@mapNotNull null
                val days = m.optJSONObject("days")?.let { d ->
                    d.keys().asSequence().mapNotNull { k ->
                        val date = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@mapNotNull null
                        val c = d.optJSONObject(k) ?: return@mapNotNull null
                        date to c.keys().asSequence().associateWith { c.optLong(it, 0) }
                    }.toMap()
                } ?: emptyMap()
                val names = m.optJSONObject("names")?.let { n -> n.keys().asSequence().associateWith { n.optString(it) } } ?: emptyMap()
                val payments = m.optJSONArray("payments")?.let { pa ->
                    (0 until pa.length()).mapNotNull { j ->
                        val p = pa.optJSONObject(j) ?: return@mapNotNull null
                        HousePayment(runCatching { LocalDate.parse(p.optString("d")) }.getOrNull() ?: return@mapNotNull null, p.optString("m"), p.optLong("p"), p.optString("c"))
                    }
                } ?: emptyList()
                HouseMember(m.getString("id"), m.optString("name"), m.optLong("at"), m.optBoolean("all"), days, names, payments)
            }
        } ?: emptyList()
        Household(o.getString("id"), o.getString("name"), o.getString("key"), o.getString("meId"), o.getString("meName"), o.optBoolean("all"),
            o.optLong("budget"), o.optLong("budgetAt"), others, o.optLong("sent"))
    }.getOrNull()
}
