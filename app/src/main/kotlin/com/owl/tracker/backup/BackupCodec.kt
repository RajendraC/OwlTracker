package com.owl.tracker.backup

import com.owl.tracker.core.CategoryRule
import com.owl.tracker.core.Direction
import com.owl.tracker.core.MerchantRegistry
import com.owl.tracker.core.Privacy
import com.owl.tracker.core.RuleMatch
import com.owl.tracker.core.TxKind
import com.owl.tracker.data.Category
import com.owl.tracker.data.CategoryKind
import com.owl.tracker.data.MerchantOverride
import com.owl.tracker.data.NewTxn
import com.owl.tracker.data.Repository
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Source
import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** A backup's contents, already scrubbed of SMS details (when written) or checked (when read). */
data class BackupContent(
    val createdAt: Long,
    val appVersion: String,
    val budgetStartDay: Int,
    val categories: List<Category>,
    val budgets: Map<String, Long>,
    val rules: List<CategoryRule>,
    val transactions: List<NewTxn>,
    /** Names and icons the user chose for merchants. Absent in backups from before version 0.5. */
    val merchants: List<MerchantOverride> = emptyList(),
) {
    val firstAt: Long? get() = transactions.minOfOrNull { it.occurredAt }
    val lastAt: Long? get() = transactions.maxOfOrNull { it.occurredAt }
}

/** What can be told about a file before it is unlocked. */
data class BackupHeader(val version: Int, val createdAt: Long, val encrypted: Boolean, val appVersion: String?, val transactions: Int?)

class BackupException(val problem: Problem) : Exception(problem.name) {
    enum class Problem { NOT_A_BACKUP, NEWER_VERSION, NEEDS_PASSWORD, WRONG_PASSWORD, DAMAGED, TOO_LARGE }
}

/**
 * The backup file: JSON, either readable or with the contents encrypted under a password.
 *
 * Encrypted: AES-256-GCM, key from PBKDF2-HMAC-SHA256 (600,000 iterations, random 16-byte salt) over
 * the password's UTF-8 bytes (Base64-encoded first, so every Android version derives the same key).
 * The header (format and version) is authenticated too, so a changed file is refused.
 *
 * Never written: SMS text, sender IDs, account or card numbers, UPI IDs, bank references, SMS ids,
 * SMS import settings, rules keyed on a UPI ID. Long digit runs in merchant names are masked.
 */
object BackupCodec {
    const val FORMAT = "owl-tracker-backup"
    const val VERSION = 1
    const val MAX_BYTES = 50L * 1024 * 1024
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD = 6

    private const val MAX_TRANSACTIONS = 500_000
    private const val MAX_CATEGORIES = 500
    private const val MAX_RULES = 20_000
    private const val EARLIEST = 946_684_800_000L // 1 Jan 2000
    private const val MAX_PAISE = 1_000_000_000_000L // ₹1,000 crore
    private val AAD = "$FORMAT/$VERSION".toByteArray(Charsets.UTF_8)
    private val ICON_KEY = Regex("[a-z_]{1,24}")
    private val ID_KEY = Regex("[A-Za-z0-9_\\-]{1,64}")

    // ---------------- building ----------------

    /** Turns what's stored into a backup, leaving out everything SMS-specific. */
    fun fromSnapshot(s: Repository.Snapshot, now: Long, appVersion: String): BackupContent {
        val known = MerchantRegistry.packageNames.toSet()
        val txns = s.transactions.map { t ->
            NewTxn(
                amountPaise = t.amountPaise, direction = t.direction, occurredAt = t.occurredAt,
                merchantName = Privacy.merchant(t.merchantName), merchantKey = Privacy.merchantKey(t.merchantKey),
                merchantPackage = t.merchantPackage?.takeIf { it in known },
                vpa = null, categoryId = t.categoryId, accountLabel = null, senderGroup = null, sender = null, refNo = null,
                source = if (t.source == Source.MANUAL) Source.MANUAL else Source.BACKUP, smsId = null,
                needsReview = t.needsReview, note = t.note, kind = t.kind, smsBody = null, excluded = t.excluded,
            )
        }
        val rules = s.rules.mapNotNull { r ->
            when (runCatching { RuleMatch.valueOf(r.match) }.getOrNull()) {
                RuleMatch.MERCHANT -> CategoryRule(RuleMatch.MERCHANT, Privacy.merchantKey(r.pattern), r.categoryId)
                RuleMatch.KEYWORD -> CategoryRule(RuleMatch.KEYWORD, Privacy.maskDigits(r.pattern), r.categoryId)
                RuleMatch.VPA, null -> null // a UPI ID: never exported
            }
        }.distinctBy { it.match to it.pattern }
        val merchants = s.merchants.mapNotNull { m -> scrubMerchant(m.key, m.name, m.logo, known) }.distinctBy { it.key }
        return BackupContent(now, appVersion, s.settings.budgetStartDay, s.categories, s.budgets, rules, txns, merchants)
    }

    /** A merchant's chosen name and icon, scrubbed like the transactions' merchant names and keys. */
    private fun scrubMerchant(key: String, name: String?, logo: String?, known: Set<String>): MerchantOverride? {
        val k = Privacy.merchantKey(clean(key, 120)).ifEmpty { return null }
        val n = name?.let { Privacy.merchant(clean(it, 60)) }?.takeIf { it.isNotEmpty() && it != "UPI payment" }
        val l = logo?.takeIf { it.isEmpty() || it in known }
        return if (n == null && l == null) null else MerchantOverride(k, n, l)
    }

    /** The file's bytes. With a [password] the contents are encrypted; without, they are readable JSON. */
    fun encode(c: BackupContent, password: CharArray?, zone: ZoneId): ByteArray {
        val payload = payloadJson(c, zone)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", c.createdAt)
            .put("created", Instant.ofEpochMilli(c.createdAt).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("app", c.appVersion)
            .put(
                "contains",
                "Amounts, dates, merchant names and icons, categories, notes, budgets and category rules. " +
                    "Never SMS text, senders, account or card numbers, UPI IDs or bank references.",
            )
        if (password == null) {
            root.put("encrypted", false).put("transactionCount", c.transactions.size).put("payload", payload)
            return root.toString(1).toByteArray(Charsets.UTF_8)
        }
        require(password.size >= MIN_PASSWORD) { "Password too short" }
        val salt = random(16)
        val iv = random(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val sealed = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        root.put("encrypted", true)
            .put("kdf", JSONObject().put("algorithm", "PBKDF2WithHmacSHA256").put("iterations", ITERATIONS).put("salt", b64(salt)).put("password", "utf8+base64"))
            .put("cipher", JSONObject().put("algorithm", "AES-256-GCM").put("iv", b64(iv)).put("tagBits", 128))
            .put("payload", b64(sealed))
        return root.toString(1).toByteArray(Charsets.UTF_8)
    }

    private fun payloadJson(c: BackupContent, zone: ZoneId): JSONObject {
        val stamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(zone)
        return JSONObject()
            .put("settings", JSONObject().put("budgetStartDay", c.budgetStartDay))
            .put("categories", JSONArray().apply {
                c.categories.forEach { cat ->
                    put(
                        JSONObject().put("id", cat.id).put("name", cat.name).put("icon", cat.icon)
                            .put("color", String.format("#%08X", cat.color and 0xFFFFFFFFL)).put("kind", cat.kind.name)
                            .put("order", cat.sortOrder).put("system", cat.isSystem).put("archived", cat.archived),
                    )
                }
            })
            .put("budgets", JSONArray().apply {
                c.budgets.forEach { (k, v) -> put(JSONObject().put("category", k).put("limitPaise", v)) }
            })
            .put("rules", JSONArray().apply {
                c.rules.forEach { put(JSONObject().put("match", it.match.name).put("pattern", it.pattern).put("category", it.categoryKey)) }
            })
            .put("transactions", JSONArray().apply {
                c.transactions.forEach { t ->
                    val o = JSONObject()
                        .put("amountPaise", t.amountPaise).put("direction", t.direction.name)
                        .put("time", t.occurredAt).put("date", stamp.format(Instant.ofEpochMilli(t.occurredAt)))
                        .put("merchant", t.merchantName).put("merchantKey", t.merchantKey)
                        .put("category", t.categoryId).put("kind", t.kind.name)
                        .put("excluded", t.excluded).put("review", t.needsReview)
                        .put("origin", if (t.source == Source.MANUAL) "manual" else "sms")
                    t.merchantPackage?.let { o.put("app", it) }
                    t.note?.let { o.put("note", it) }
                    put(o)
                }
            })
            .put("merchants", JSONArray().apply {
                c.merchants.forEach { m ->
                    val o = JSONObject().put("merchantKey", m.key)
                    m.name?.let { o.put("name", it) }
                    m.logo?.let { o.put("app", it) } // "" means: no app icon
                    put(o)
                }
            })
    }

    // ---------------- reading ----------------

    /** Reads the outside of a file without unlocking it. Throws [BackupException]. */
    fun peek(bytes: ByteArray): BackupHeader {
        val root = root(bytes)
        val encrypted = root.optBoolean("encrypted", false)
        return BackupHeader(
            version = root.getInt("version"),
            createdAt = root.optLong("createdAt", 0L),
            encrypted = encrypted,
            appVersion = root.optString("app").takeIf { it.isNotBlank() }?.take(20),
            transactions = if (encrypted) null else root.optInt("transactionCount", -1).takeIf { it >= 0 },
        )
    }

    /** Unlocks (if needed) and checks a backup. Throws [BackupException]; never returns half a backup. */
    fun decode(bytes: ByteArray, password: CharArray?, now: Long): BackupContent {
        val root = root(bytes)
        val payload: JSONObject = if (root.optBoolean("encrypted", false)) {
            if (password == null || password.isEmpty()) throw BackupException(BackupException.Problem.NEEDS_PASSWORD)
            try {
                val kdf = root.getJSONObject("kdf")
                val cip = root.getJSONObject("cipher")
                val iterations = kdf.getInt("iterations")
                if (iterations !in 100_000..10_000_000) throw BackupException(BackupException.Problem.DAMAGED)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, unb64(kdf.getString("salt")), iterations), GCMParameterSpec(128, unb64(cip.getString("iv"))))
                cipher.updateAAD(AAD)
                JSONObject(String(cipher.doFinal(unb64(root.getString("payload"))), Charsets.UTF_8))
            } catch (e: AEADBadTagException) {
                throw BackupException(BackupException.Problem.WRONG_PASSWORD)
            } catch (e: BackupException) {
                throw e
            } catch (e: GeneralSecurityException) {
                throw BackupException(BackupException.Problem.DAMAGED)
            } catch (e: Exception) {
                throw BackupException(BackupException.Problem.DAMAGED)
            }
        } else {
            root.optJSONObject("payload") ?: throw BackupException(BackupException.Problem.DAMAGED)
        }
        return try {
            parsePayload(payload, root.optLong("createdAt", 0L), root.optString("app"), now)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Problem.DAMAGED)
        }
    }

    private fun root(bytes: ByteArray): JSONObject {
        if (bytes.size > MAX_BYTES) throw BackupException(BackupException.Problem.TOO_LARGE)
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException(BackupException.Problem.NOT_A_BACKUP)
        }
        if (root.optString("format") != FORMAT) throw BackupException(BackupException.Problem.NOT_A_BACKUP)
        val version = root.optInt("version", -1)
        if (version < 1) throw BackupException(BackupException.Problem.NOT_A_BACKUP)
        if (version > VERSION) throw BackupException(BackupException.Problem.NEWER_VERSION)
        return root
    }

    /** Checks every field; anything out of range is fixed to a safe value or the whole file is refused. */
    private fun parsePayload(p: JSONObject, createdAt: Long, app: String?, now: Long): BackupContent {
        val startDay = p.optJSONObject("settings")?.optInt("budgetStartDay", 1)?.coerceIn(1, 28) ?: 1

        val catsJson = p.optJSONArray("categories") ?: JSONArray()
        if (catsJson.length() > MAX_CATEGORIES) throw BackupException(BackupException.Problem.TOO_LARGE)
        val categories = (0 until catsJson.length()).mapNotNull { i ->
            val o = catsJson.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { ID_KEY.matches(it) && it != Schema.OVERALL } ?: return@mapNotNull null
            Category(
                id = id,
                name = clean(o.optString("name"), 40).ifEmpty { return@mapNotNull null },
                icon = o.optString("icon").takeIf { ICON_KEY.matches(it) } ?: "others",
                color = parseColor(o.optString("color")),
                kind = runCatching { CategoryKind.valueOf(o.optString("kind")) }.getOrDefault(CategoryKind.EXPENSE),
                sortOrder = o.optInt("order", 100).coerceIn(0, 10_000),
                isSystem = o.optBoolean("system", false),
                archived = o.optBoolean("archived", false),
            )
        }.distinctBy { it.id }

        val budgetsJson = p.optJSONArray("budgets") ?: JSONArray()
        if (budgetsJson.length() > MAX_CATEGORIES + 1) throw BackupException(BackupException.Problem.TOO_LARGE)
        val budgets = buildMap {
            for (i in 0 until budgetsJson.length()) {
                val o = budgetsJson.optJSONObject(i) ?: continue
                val key = o.optString("category")
                val limit = o.optLong("limitPaise", 0L)
                if ((key == Schema.OVERALL || ID_KEY.matches(key)) && limit in 1..MAX_PAISE) put(key, limit)
            }
        }

        val rulesJson = p.optJSONArray("rules") ?: JSONArray()
        if (rulesJson.length() > MAX_RULES) throw BackupException(BackupException.Problem.TOO_LARGE)
        val rules = (0 until rulesJson.length()).mapNotNull { i ->
            val o = rulesJson.optJSONObject(i) ?: return@mapNotNull null
            val match = runCatching { RuleMatch.valueOf(o.optString("match")) }.getOrNull()
            if (match == null || match == RuleMatch.VPA) return@mapNotNull null
            val pattern = clean(o.optString("pattern"), 120).ifEmpty { return@mapNotNull null }
            val cat = o.optString("category").takeIf { ID_KEY.matches(it) } ?: return@mapNotNull null
            CategoryRule(match, pattern, cat)
        }.distinctBy { it.match to it.pattern }

        val known = MerchantRegistry.packageNames.toSet()
        val txJson = p.optJSONArray("transactions") ?: JSONArray()
        if (txJson.length() > MAX_TRANSACTIONS) throw BackupException(BackupException.Problem.TOO_LARGE)
        val latest = now + 2 * 24 * 60 * 60 * 1000L
        val txns = (0 until txJson.length()).mapNotNull { i ->
            val o = txJson.optJSONObject(i) ?: return@mapNotNull null
            val amount = o.optLong("amountPaise", 0L).takeIf { it in 1..MAX_PAISE } ?: return@mapNotNull null
            val time = o.optLong("time", 0L).takeIf { it in EARLIEST..latest } ?: return@mapNotNull null
            val direction = runCatching { Direction.valueOf(o.optString("direction")) }.getOrNull() ?: return@mapNotNull null
            val manual = o.optString("origin") == "manual"
            // Re-scrubbed on the way in too, in case the file was edited by hand.
            val merchant = Privacy.merchant(clean(o.optString("merchant"), 120)).ifEmpty { "Unknown" }
            NewTxn(
                amountPaise = amount, direction = direction, occurredAt = time,
                merchantName = merchant,
                merchantKey = Privacy.merchantKey(clean(o.optString("merchantKey"), 120)).ifEmpty { "unknown" },
                merchantPackage = o.optString("app").takeIf { it in known },
                vpa = null,
                categoryId = o.optString("category").takeIf { ID_KEY.matches(it) } ?: com.owl.tracker.core.Cat.OTHERS,
                accountLabel = null, senderGroup = null, sender = null, refNo = null,
                source = if (manual) Source.MANUAL else Source.BACKUP, smsId = null,
                needsReview = o.optBoolean("review", false),
                note = clean(o.optString("note"), 500).ifEmpty { null },
                kind = runCatching { TxKind.valueOf(o.optString("kind")) }.getOrDefault(TxKind.NORMAL),
                smsBody = null,
                excluded = o.optBoolean("excluded", false),
            )
        }

        val merchantsJson = p.optJSONArray("merchants") ?: JSONArray()
        if (merchantsJson.length() > MAX_RULES) throw BackupException(BackupException.Problem.TOO_LARGE)
        val merchants = (0 until merchantsJson.length()).mapNotNull { i ->
            val o = merchantsJson.optJSONObject(i) ?: return@mapNotNull null
            scrubMerchant(
                o.optString("merchantKey"),
                if (o.has("name")) o.optString("name") else null,
                if (o.has("app")) o.optString("app") else null,
                known,
            )
        }.distinctBy { it.key }

        return BackupContent(
            createdAt = createdAt,
            appVersion = app?.take(20).orEmpty(),
            budgetStartDay = startDay,
            categories = categories,
            budgets = budgets,
            rules = rules,
            transactions = txns,
            merchants = merchants,
        )
    }

    // ---------------- helpers ----------------

    /** Trims, drops control characters and caps the length. */
    private fun clean(s: String?, max: Int): String =
        (s ?: "").filter { !it.isISOControl() }.trim().take(max)

    private fun parseColor(s: String?): Long {
        val hex = s?.removePrefix("#")?.takeIf { it.length in 6..8 && it.all { ch -> ch.isLetterOrDigit() } } ?: return 0xFF7F8682
        val v = hex.toLongOrNull(16) ?: return 0xFF7F8682
        return (v and 0x00FFFFFFL) or 0xFF000000L
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        // UTF-8, then Base64: plain ASCII going into PBKDF2, so every Android version and the JVM agree.
        val utf8 = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
        val bytes = ByteArray(utf8.remaining()).also { utf8.get(it) }
        val ascii = Base64.getEncoder().encodeToString(bytes).toCharArray()
        bytes.fill(0)
        val spec = PBEKeySpec(ascii, salt, iterations, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
            ascii.fill(' ')
        }
    }

    private val rng = SecureRandom()
    private fun random(n: Int) = ByteArray(n).also { rng.nextBytes(it) }
    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
}
