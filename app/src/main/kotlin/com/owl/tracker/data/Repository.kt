package com.owl.tracker.data

import android.database.Cursor
import androidx.core.content.contentValuesOf
import androidx.sqlite.db.SupportSQLiteDatabase
import com.owl.tracker.backup.BackupContent
import com.owl.tracker.core.Cat
import com.owl.tracker.core.CategoryRule
import com.owl.tracker.core.Direction
import com.owl.tracker.core.KnownApps
import com.owl.tracker.core.MerchantPicture
import com.owl.tracker.core.MerchantRegistry
import com.owl.tracker.core.RuleMatch
import com.owl.tracker.core.TxKind
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * All reads and writes go through here, on one background thread. Reads are exposed as Flows that
 * re-run whenever any write happens. [bankApp] gives the installed app of a bank by its sender id,
 * the automatic icon of a card bill payment.
 */
class Repository(private val db: Db, private val clock: Clock, private val bankApp: (String?) -> String? = { null }) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    private suspend fun <T> read(block: (SupportSQLiteDatabase) -> T): T = withContext(io) { block(db.get()) }

    private suspend fun <T> write(block: (SupportSQLiteDatabase) -> T): T {
        val r = withContext(io) { db.transaction(block) }
        db.notifyChanged()
        return r
    }

    /**
     * Re-runs [block] after every write. A read that fails (for example while Reset is replacing the
     * database) is skipped rather than ending the flow, so the screen keeps updating afterwards.
     */
    private fun <T> observe(block: (SupportSQLiteDatabase) -> T): Flow<T> =
        db.changes.transform {
            val value = try {
                read(block)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@transform
            }
            emit(value)
        }.distinctUntilChanged()

    /** Runs [block] on the database thread, so no query is in flight while it runs. */
    suspend fun <T> exclusive(block: () -> T): T = withContext(io) { block() }

    /** Opens the database once; throws if the encryption key can't be unlocked on this phone. */
    suspend fun open() = read { it.query("SELECT COUNT(*) FROM categories").use { c -> c.moveToFirst() } }

    // ---------------- settings ----------------

    fun observeSettings(): Flow<AppSettings> = observe { readSettings(it) }

    suspend fun settings(): AppSettings = read { readSettings(it) }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = write { d ->
        val next = transform(readSettings(d))
        next.toMap().forEach { (k, v) -> d.insert("settings", CONFLICT_REPLACE, contentValuesOf("key" to k, "value" to v)) }
    }

    private fun readSettings(d: SupportSQLiteDatabase): AppSettings {
        val m = HashMap<String, String>()
        d.query("SELECT key, value FROM settings").use { c -> while (c.moveToNext()) m[c.getString(0)] = c.getString(1) }
        return AppSettings.fromMap(m)
    }

    // ---------------- categories ----------------

    fun observeCategories(): Flow<List<Category>> = observe { readCategories(it) }

    suspend fun categories(): List<Category> = read { readCategories(it) }

    private fun readCategories(d: SupportSQLiteDatabase): List<Category> =
        d.query("SELECT id, name, icon, color, kind, sort_order, is_system, archived FROM categories ORDER BY sort_order, name").use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Category(
                        id = c.getString(0), name = c.getString(1), icon = c.getString(2), color = c.getLong(3),
                        kind = runCatching { CategoryKind.valueOf(c.getString(4)) }.getOrDefault(CategoryKind.EXPENSE),
                        sortOrder = c.getInt(5), isSystem = c.getInt(6) == 1, archived = c.getInt(7) == 1,
                    )
                )
            }
        }

    /** Adds a new category (id null) or saves changes to an existing one. Returns its id. */
    suspend fun saveCategory(id: String?, name: String, icon: String, color: Long, countsAsSpend: Boolean): String = write { d ->
        if (id == null) {
            val newId = "c_" + clock.millis()
            val order = d.query("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories").use { c -> c.moveToFirst(); c.getInt(0) }
            d.insert(
                "categories", CONFLICT_REPLACE,
                contentValuesOf(
                    "id" to newId, "name" to name, "icon" to icon, "color" to color,
                    "kind" to (if (countsAsSpend) CategoryKind.EXPENSE else CategoryKind.TRANSFER).name,
                    "sort_order" to order, "is_system" to 0, "archived" to 0,
                )
            )
            newId
        } else {
            val currentKind = d.query("SELECT kind FROM categories WHERE id = ?", arrayOf<Any?>(id)).use { c ->
                if (c.moveToFirst()) runCatching { CategoryKind.valueOf(c.getString(0)) }.getOrDefault(CategoryKind.EXPENSE) else CategoryKind.EXPENSE
            }
            val kind = when {
                currentKind == CategoryKind.INCOME -> CategoryKind.INCOME
                countsAsSpend -> CategoryKind.EXPENSE
                currentKind == CategoryKind.EXPENSE -> CategoryKind.TRANSFER
                else -> currentKind
            }
            d.update("categories", CONFLICT_REPLACE, contentValuesOf("name" to name, "icon" to icon, "color" to color, "kind" to kind.name), "id = ?", arrayOf<Any?>(id))
            id
        }
    }

    /** Archives a category; its transactions move to Others and its budget is removed. Returns how many moved. */
    suspend fun archiveCategory(id: String): Int = write { d ->
        val moved = d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to Cat.OTHERS), "category_id = ?", arrayOf<Any?>(id))
        // Split payments with a part in it: that part moves to Others too (joined with an Others part already there)
        d.query("SELECT id, parts, category_id FROM transactions WHERE parts LIKE ?", arrayOf<Any?>("%$id=%")).use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) }
        }.forEach { (txId, raw, main) ->
            val merged = Parts.decode(raw).map { if (it.categoryId == id) it.copy(categoryId = Cat.OTHERS) else it }
                .groupBy { it.categoryId }.map { (cat, l) -> TxnPart(cat, l.sumOf { it.paise }) }
            val v = contentValuesOf("parts" to Parts.encode(merged))
            if (merged.size < 2) v.put("category_id", merged.firstOrNull()?.categoryId ?: main)
            d.update("transactions", CONFLICT_REPLACE, v, "id = ?", arrayOf<Any?>(txId))
        }
        d.update("rules", CONFLICT_REPLACE, contentValuesOf("category_id" to Cat.OTHERS), "category_id = ?", arrayOf<Any?>(id))
        d.delete("budgets", "category_key = ?", arrayOf<Any?>(id))
        d.update("categories", CONFLICT_REPLACE, contentValuesOf("archived" to 1), "id = ?", arrayOf<Any?>(id))
        moved
    }

    // ---------------- budgets ----------------

    fun observeBudgets(): Flow<Map<String, Long>> = observe { d ->
        d.query("SELECT category_key, limit_paise FROM budgets").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }
    }

    suspend fun setBudget(key: String, limitPaise: Long?): Unit = write { d ->
        if (limitPaise == null || limitPaise <= 0) d.delete("budgets", "category_key = ?", arrayOf<Any?>(key))
        else d.insert("budgets", CONFLICT_REPLACE, contentValuesOf("category_key" to key, "limit_paise" to limitPaise))
        Unit
    }

    // ---------------- transactions ----------------

    fun observeTransactions(fromMs: Long, toMs: Long): Flow<List<Txn>> = observe { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC, id DESC", arrayOf<Any?>(fromMs, toMs))
            .use { c -> c.toTxns() }
    }

    suspend fun transactions(fromMs: Long, toMs: Long): List<Txn> = read { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC, id DESC", arrayOf<Any?>(fromMs, toMs))
            .use { c -> c.toTxns() }
    }

    suspend fun transaction(id: Long): Txn? = read { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE id = ?", arrayOf<Any?>(id)).use { c -> c.toTxns().firstOrNull() }
    }

    fun observeTransaction(id: Long): Flow<Txn?> = observe { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE id = ?", arrayOf<Any?>(id)).use { c -> c.toTxns().firstOrNull() }
    }

    /**
     * Transactions an SMS import must not duplicate: earlier SMS imports, and payments restored from a
     * backup (which have no SMS details, so they match on amount, direction and time).
     */
    suspend fun smsTransactionsSince(fromMs: Long): List<Txn> = read { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE source IN ('SMS', 'BACKUP') AND occurred_at >= ?", arrayOf<Any?>(fromMs)).use { c -> c.toTxns() }
    }

    suspend fun existingSmsIds(): Set<Long> = read { d ->
        d.query("SELECT sms_id FROM transactions WHERE sms_id IS NOT NULL").use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } } +
            d.query("SELECT sms_id FROM deleted_sms").use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    suspend fun deletedSmsIds(): Set<Long> = read { d ->
        d.query("SELECT sms_id FROM deleted_sms").use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    /** Inserts imported or manual transactions. Rows whose SMS is already stored are skipped. Returns how many were added. */
    suspend fun insertAll(rows: List<NewTxn>): Int = write { d ->
        var added = 0
        rows.forEach { r -> if (d.insert("transactions", CONFLICT_IGNORE, r.toValues()) != -1L) added++ }
        added
    }

    suspend fun addManual(row: NewTxn): Long = write { d -> d.insert("transactions", CONFLICT_IGNORE, row.toValues()) }

    /** Re-import: removes SMS transactions from [fromMs] onward whose message is not in [keepSmsIds]. */
    suspend fun removeSmsTransactionsNotIn(fromMs: Long, keepSmsIds: Set<Long>): Int = write { d ->
        val ids = d.query("SELECT id, sms_id FROM transactions WHERE source = 'SMS' AND occurred_at >= ?", arrayOf<Any?>(fromMs)).use { c ->
            buildList { while (c.moveToNext()) if (c.getLong(1) !in keepSmsIds) add(c.getLong(0)) }
        }
        ids.forEach { d.delete("transactions", "id = ?", arrayOf<Any?>(it)) }
        ids.size
    }

    suspend fun setCategory(txId: Long, categoryId: String, rememberForMerchant: Boolean): Int = write { d ->
        val key = d.query("SELECT merchant_key FROM transactions WHERE id = ?", arrayOf<Any?>(txId)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: return@write 0
        if (rememberForMerchant) {
            d.insert(
                "rules", CONFLICT_REPLACE,
                contentValuesOf("match_type" to RuleMatch.MERCHANT.name, "pattern" to key, "category_id" to categoryId, "created_at" to clock.millis())
            )
            // This payment, if split, becomes one category; other split payments keep their parts
            d.update("transactions", CONFLICT_REPLACE, contentValuesOf("parts" to null), "id = ?", arrayOf<Any?>(txId))
            d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to categoryId, "needs_review" to 0), "merchant_key = ? AND parts IS NULL", arrayOf<Any?>(key))
        } else {
            d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to categoryId, "needs_review" to 0, "parts" to null), "id = ?", arrayOf<Any?>(txId))
        }
    }

    // ---------------- merchant names and icons ----------------

    /** Names and icons the user chose, by merchant key. */
    suspend fun merchantOverrides(): Map<String, MerchantOverride> = read { d -> readMerchants(d).associateBy { it.key } }

    /** How many payments share this payment's merchant (itself included). */
    fun observeSameMerchantCount(txId: Long): Flow<Int> = observe { d ->
        d.query(
            "SELECT COUNT(*) FROM transactions WHERE merchant_key = (SELECT merchant_key FROM transactions WHERE id = ?)",
            arrayOf<Any?>(txId),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /**
     * Renames one payment, or ([all]) every payment from the same merchant, and remembers the name so new
     * imports from that merchant use it too. Returns how many payments changed.
     */
    suspend fun renameMerchant(txId: Long, name: String, all: Boolean): Int = write { d ->
        val clean = name.filter { !it.isISOControl() }.trim().take(MAX_NAME)
        if (clean.isEmpty()) return@write 0
        if (!all) return@write d.update("transactions", CONFLICT_REPLACE, contentValuesOf("merchant_name" to clean), "id = ?", arrayOf<Any?>(txId))
        val key = merchantKeyOf(d, txId) ?: return@write 0
        upsertMerchant(d, key, "name" to clean)
        d.update("transactions", CONFLICT_REPLACE, contentValuesOf("merchant_name" to clean), "merchant_key = ?", arrayOf<Any?>(key))
    }

    /**
     * Sets the icon for every payment from this payment's merchant, now and in future imports.
     * [logo]: null = automatic (the recognised app, if any), "" = no app icon, else an app's package
     * (a recognised app or any UPI app on the phone) or a picture already saved by [setMerchantPicture].
     */
    suspend fun setMerchantLogo(txId: Long, logo: String?): Int = write { d ->
        val key = merchantKeyOf(d, txId) ?: return@write 0
        val pkg = when (logo) {
            null -> automaticIcon(d, key)
            "" -> null
            else -> logo.takeIf { KnownApps.isPackageName(it) || (MerchantPicture.isPicture(it) && hasPicture(d, it)) } ?: return@write 0
        }
        upsertMerchant(d, key, "logo" to logo)
        val n = d.update("transactions", CONFLICT_REPLACE, contentValuesOf("merchant_package" to pkg), "merchant_key = ?", arrayOf<Any?>(key))
        prunePictures(d)
        if (logo == null && pkg == null) {
            // A card bill payment has no merchant app: automatic means the app of the bank that sent the SMS.
            val bill = TxKind.CARD_BILL_PAYMENT.name
            val groups = d.query("SELECT DISTINCT sender_group FROM transactions WHERE merchant_key = ? AND kind = ?", arrayOf<Any?>(key, bill))
                .use { c -> buildList { while (c.moveToNext()) add(c.str(0)) } }
            groups.forEach { g ->
                val app = bankApp(g) ?: return@forEach
                d.update("transactions", CONFLICT_REPLACE, contentValuesOf("merchant_package" to app), "merchant_key = ? AND kind = ? AND sender_group IS ?", arrayOf<Any?>(key, bill, g))
            }
        }
        n
    }

    /**
     * Gives an icon to payments that have none yet: merchants whose apps a newer version recognises, and
     * card bill payments whose bank's app has since been installed. Only the icon changes, and merchants
     * whose icon the user chose are left alone. Writes (and refreshes screens) only when something changes.
     */
    suspend fun fillMissingIcons(): Int {
        val bill = TxKind.CARD_BILL_PAYMENT.name
        val open = "merchant_package IS NULL AND merchant_key NOT IN (SELECT merchant_key FROM merchants WHERE logo IS NOT NULL)"
        val (byMerchant, byBank) = read { d ->
            val merchants = d.query("SELECT merchant_key, MAX(merchant_name), MAX(vpa) FROM transactions WHERE $open GROUP BY merchant_key")
                .use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.str(1), c.str(2))) } }
                .mapNotNull { (key, name, vpa) -> MerchantRegistry.match(name, vpa)?.packageName?.let { key to it } }
            val bills = d.query("SELECT DISTINCT merchant_key, sender_group FROM transactions WHERE kind = ? AND $open", arrayOf<Any?>(bill))
                .use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.str(1)) } }
                .filter { (key, _) -> merchants.none { it.first == key } }
                .mapNotNull { (key, group) -> bankApp(group)?.let { Triple(key, group, it) } }
            merchants to bills
        }
        if (byMerchant.isEmpty() && byBank.isEmpty()) return 0
        return write { d ->
            byMerchant.sumOf { (key, pkg) ->
                d.update("transactions", CONFLICT_REPLACE, contentValuesOf("merchant_package" to pkg), "merchant_key = ? AND merchant_package IS NULL", arrayOf<Any?>(key))
            } + byBank.sumOf { (key, group, pkg) ->
                d.update(
                    "transactions", CONFLICT_REPLACE, contentValuesOf("merchant_package" to pkg),
                    "merchant_key = ? AND kind = ? AND sender_group IS ? AND merchant_package IS NULL", arrayOf<Any?>(key, bill, group),
                )
            }
        }
    }

    /**
     * Uses a picture (a small square PNG, see [MerchantPicture]) as the icon for every payment from this
     * payment's merchant, now and in future imports. Returns how many payments changed.
     */
    suspend fun setMerchantPicture(txId: Long, png: ByteArray): Int {
        require(MerchantPicture.isPng(png)) { "Not a PNG picture icon" }
        val id = MerchantPicture.idFor(png)
        return write { d ->
            val key = merchantKeyOf(d, txId) ?: return@write 0
            d.insert("pictures", CONFLICT_IGNORE, contentValuesOf("id" to id, "png" to png))
            upsertMerchant(d, key, "logo" to id)
            val n = d.update("transactions", CONFLICT_REPLACE, contentValuesOf("merchant_package" to id), "merchant_key = ?", arrayOf<Any?>(key))
            prunePictures(d)
            n
        }
    }

    /** A picture icon's PNG, for drawing it. Small and quick; called once per picture by the icon cache. */
    fun pictureBytes(id: String): ByteArray? {
        if (!MerchantPicture.isPicture(id)) return null
        return db.get().query("SELECT png FROM pictures WHERE id = ?", arrayOf<Any?>(id)).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
    }

    /**
     * The icon a merchant gets on its own: its recognised app, found by key, or for outlets that keep their own
     * name (a petrol pump, a toll plaza) by matching that name again.
     */
    private fun automaticIcon(d: SupportSQLiteDatabase, key: String): String? =
        MerchantRegistry.all.firstOrNull { it.key == key }?.packageName
            ?: d.query("SELECT MAX(merchant_name), MAX(vpa) FROM transactions WHERE merchant_key = ?", arrayOf<Any?>(key))
                .use { c -> if (c.moveToFirst()) MerchantRegistry.match(c.str(0), c.str(1))?.packageName else null }

    private fun hasPicture(d: SupportSQLiteDatabase, id: String): Boolean =
        d.query("SELECT 1 FROM pictures WHERE id = ?", arrayOf<Any?>(id)).use { it.moveToFirst() }

    /** Removes pictures no merchant or payment uses any more. */
    private fun prunePictures(d: SupportSQLiteDatabase) {
        d.delete(
            "pictures",
            "id NOT IN (SELECT logo FROM merchants WHERE logo IS NOT NULL) AND id NOT IN (SELECT merchant_package FROM transactions WHERE merchant_package IS NOT NULL)",
            null,
        )
    }

    private fun readPictures(d: SupportSQLiteDatabase): Map<String, ByteArray> =
        d.query("SELECT id, png FROM pictures").use { c -> buildMap<String, ByteArray> { while (c.moveToNext()) put(c.getString(0), c.getBlob(1)) } }

    private fun merchantKeyOf(d: SupportSQLiteDatabase, txId: Long): String? =
        d.query("SELECT merchant_key FROM transactions WHERE id = ?", arrayOf<Any?>(txId)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** Sets one column of a merchant's row, creating the row if needed; a row left with nothing in it is removed. */
    private fun upsertMerchant(d: SupportSQLiteDatabase, key: String, column: Pair<String, String?>) {
        d.insert("merchants", CONFLICT_IGNORE, contentValuesOf("merchant_key" to key, "updated_at" to clock.millis()))
        d.update("merchants", CONFLICT_REPLACE, contentValuesOf(column, "updated_at" to clock.millis()), "merchant_key = ?", arrayOf<Any?>(key))
        d.delete("merchants", "merchant_key = ? AND name IS NULL AND logo IS NULL", arrayOf<Any?>(key))
    }

    private fun readMerchants(d: SupportSQLiteDatabase): List<MerchantOverride> =
        d.query("SELECT merchant_key, name, logo FROM merchants ORDER BY merchant_key").use { c ->
            buildList { while (c.moveToNext()) add(MerchantOverride(c.getString(0), c.str(1), c.str(2))) }
        }

    suspend fun setNote(txId: Long, note: String) = write { d ->
        d.update("transactions", CONFLICT_REPLACE, contentValuesOf("note" to note.ifBlank { null }), "id = ?", arrayOf<Any?>(txId))
    }

    suspend fun setExcluded(txId: Long, excluded: Boolean) = write { d ->
        d.update("transactions", CONFLICT_REPLACE, contentValuesOf("excluded" to if (excluded) 1 else 0), "id = ?", arrayOf<Any?>(txId))
    }

    suspend fun confirmReview(txId: Long) = write { d ->
        d.update("transactions", CONFLICT_REPLACE, contentValuesOf("needs_review" to 0), "id = ?", arrayOf<Any?>(txId))
    }

    /**
     * SMS payments stored as money out whose text says the money came to you ("sent to your A/c",
     * "paid to you by"): versions before 0.8.0 read some of these as spending. A cheap text filter;
     * the parser decides (see ImportEngine.fixMoneyIn).
     */
    suspend fun smsMoneyOutMentioningYou(): List<Txn> = read { d ->
        d.query(
            "SELECT $TX_COLS FROM transactions WHERE source = 'SMS' AND direction = ? AND sms_body IS NOT NULL AND " +
                "(sms_body LIKE '% to you%' OR sms_body LIKE '% to ur %' OR sms_body LIKE '% into your%' OR sms_body LIKE '% in your%')",
            arrayOf<Any?>(Direction.DEBIT.name),
        ).use { c -> c.toTxns() }
    }

    /**
     * Imported payments whose text may be a reminder rather than money that moved ("… is due on 07-Nov-26 … Ignore
     * if paid."): versions before 1.5.2 read some of these as spending. A cheap text filter; the parser decides
     * (see ImportEngine.dropReminders).
     */
    suspend fun smsMaybeReminders(): List<Txn> = read { d ->
        d.query(
            "SELECT $TX_COLS FROM transactions WHERE source = 'SMS' AND sms_body IS NOT NULL AND " +
                "(sms_body LIKE '%due%' OR sms_body LIKE '%paid%' OR sms_body LIKE '%to be %')",
        ).use { c -> c.toTxns() }
    }

    /** Turns a payment into money in, with [categoryId] when the user hadn't picked one. */
    suspend fun setMoneyIn(txId: Long, categoryId: String?): Unit = write { d ->
        val v = contentValuesOf("direction" to Direction.CREDIT.name, "parts" to null)
        if (categoryId != null) v.put("category_id", categoryId)
        d.update("transactions", CONFLICT_REPLACE, v, "id = ?", arrayOf<Any?>(txId))
        Unit
    }

    /** Deletes one transaction. If it came from an SMS, remembers that so it never comes back. */
    suspend fun deleteTransaction(id: Long): Txn? = write { d ->
        val t = d.query("SELECT $TX_COLS FROM transactions WHERE id = ?", arrayOf<Any?>(id)).use { c -> c.toTxns().firstOrNull() } ?: return@write null
        if (t.smsId != null) d.insert("deleted_sms", CONFLICT_REPLACE, contentValuesOf("sms_id" to t.smsId, "deleted_at" to clock.millis()))
        d.delete("transactions", "id = ?", arrayOf<Any?>(id))
        t
    }

    /** Undo for [deleteTransaction]. */
    suspend fun restoreTransaction(t: Txn) = write { d ->
        if (t.smsId != null) d.delete("deleted_sms", "sms_id = ?", arrayOf<Any?>(t.smsId))
        val v = t.toNew().toValues()
        v.put("id", t.id)
        d.insert("transactions", CONFLICT_REPLACE, v)
    }

    // ---------------- bulk delete ----------------

    suspend fun countSmsBefore(cutoffMs: Long): Int = count("SELECT COUNT(*) FROM transactions WHERE source = 'SMS' AND occurred_at < ?", cutoffMs)
    suspend fun countSmsInGroup(group: String): Int = count("SELECT COUNT(*) FROM transactions WHERE source = 'SMS' AND sender_group = ?", group)
    suspend fun countAllSms(): Int = count("SELECT COUNT(*) FROM transactions WHERE source = 'SMS'")

    private suspend fun count(sql: String, vararg args: Any?): Int = read { d ->
        d.query(sql, arrayOf(*args)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun deleteSmsBefore(cutoffMs: Long): Int = write { d -> d.delete("transactions", "source = 'SMS' AND occurred_at < ?", arrayOf<Any?>(cutoffMs)) }
    suspend fun deleteSmsInGroup(group: String): Int = write { d -> d.delete("transactions", "source = 'SMS' AND sender_group = ?", arrayOf<Any?>(group)) }
    suspend fun deleteAllSms(): Int = write { d -> d.delete("transactions", "source = 'SMS'", null) }

    data class Storage(val total: Int, val oldestMs: Long?, val bySenderGroup: Map<String, Int>)

    fun observeStorage(): Flow<Storage> = observe { d ->
        val (total, oldest) = d.query("SELECT COUNT(*), MIN(occurred_at) FROM transactions").use { c ->
            c.moveToFirst(); c.getInt(0) to (if (c.isNull(1)) null else c.getLong(1))
        }
        val groups = d.query("SELECT sender_group, COUNT(*) FROM transactions WHERE source = 'SMS' GROUP BY sender_group").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0) ?: "unknown", c.getInt(1)) }
        }
        Storage(total, oldest, groups)
    }

    // ---------------- rules ----------------

    suspend fun rules(): List<CategoryRule> = read { d ->
        d.query("SELECT match_type, pattern, category_id FROM rules ORDER BY created_at DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val m = runCatching { RuleMatch.valueOf(c.getString(0)) }.getOrNull() ?: continue
                    add(CategoryRule(m, c.getString(1), c.getString(2)))
                }
            }
        }
    }

    fun observeRuleCount(): Flow<Int> = observe { d -> d.query("SELECT COUNT(*) FROM rules").use { c -> c.moveToFirst(); c.getInt(0) } }

    /** Every rule, newest first, with the name to show and how many payments it covers. */
    fun observeRules(): Flow<List<RuleInfo>> = observe { d ->
        val rows = d.query("SELECT id, match_type, pattern, category_id, created_at FROM rules ORDER BY created_at DESC, id DESC").use { c ->
            buildList { while (c.moveToNext()) add(StoredRule(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)) to c.getLong(4)) }
        }
        rows.mapNotNull { (r, created) ->
            val match = runCatching { RuleMatch.valueOf(r.matchType) }.getOrNull() ?: return@mapNotNull null
            val (where, args) = ruleWhere(match, r.pattern)
            val count = d.query("SELECT COUNT(*) FROM transactions WHERE $where", args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            val name = when (match) {
                RuleMatch.MERCHANT -> d.query("SELECT name FROM merchants WHERE merchant_key = ? AND name IS NOT NULL", arrayOf<Any?>(r.pattern)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: d.query("SELECT merchant_name FROM transactions WHERE merchant_key = ? ORDER BY occurred_at DESC LIMIT 1", arrayOf<Any?>(r.pattern)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: MerchantRegistry.all.firstOrNull { it.key == r.pattern }?.displayName ?: r.pattern
                RuleMatch.VPA -> r.pattern
                RuleMatch.KEYWORD -> "“${r.pattern}”"
            }
            RuleInfo(r.id, match, r.pattern, r.categoryId, created, name, count)
        }
    }

    private fun ruleWhere(match: RuleMatch, pattern: String): Pair<String, Array<Any?>> = when (match) {
        RuleMatch.MERCHANT -> "merchant_key = ?" to arrayOf<Any?>(pattern)
        RuleMatch.VPA -> "LOWER(vpa) = LOWER(?)" to arrayOf<Any?>(pattern)
        RuleMatch.KEYWORD -> "(merchant_name LIKE ? OR vpa LIKE ?)" to arrayOf<Any?>("%$pattern%", "%$pattern%")
    }

    /** The payments a rule covers that are still in its category and not split: the ones deleting it could put back. */
    suspend fun ruleCovered(rule: RuleInfo): List<Txn> = read { d ->
        val (where, args) = ruleWhere(rule.match, rule.pattern)
        d.query("SELECT $TX_COLS FROM transactions WHERE $where AND category_id = ? AND parts IS NULL", arrayOf<Any?>(*args, rule.categoryId)).use { c -> c.toTxns() }
    }

    /**
     * Deletes a rule, and moves payments to new categories ([moves]: id to category) in the same write.
     * Returns what was there before, for undo.
     */
    suspend fun deleteRule(rule: RuleInfo, moves: Map<Long, String>): Map<Long, String> = write { d ->
        val before = HashMap<Long, String>()
        moves.forEach { (id, cat) ->
            d.query("SELECT category_id FROM transactions WHERE id = ? AND parts IS NULL", arrayOf<Any?>(id)).use { c -> if (c.moveToFirst()) before[id] = c.getString(0) }
            if (before.containsKey(id)) d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to cat), "id = ?", arrayOf<Any?>(id))
        }
        d.delete("rules", "id = ?", arrayOf<Any?>(rule.id))
        before
    }

    /** Undo for [deleteRule]: the rule as it was, and the payments back in their categories. */
    suspend fun restoreRule(rule: RuleInfo, before: Map<Long, String>) = write { d ->
        d.insert(
            "rules", CONFLICT_REPLACE,
            contentValuesOf("match_type" to rule.match.name, "pattern" to rule.pattern, "category_id" to rule.categoryId, "created_at" to rule.createdAt),
        )
        before.forEach { (id, cat) -> d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to cat), "id = ? AND parts IS NULL", arrayOf<Any?>(id)) }
    }

    /**
     * Splits a payment across categories ([parts] adding up to its amount), or joins it back into one category
     * (empty [parts]; [joinTo] picks the category, else the biggest part's). The payment's own category becomes
     * the biggest part's, so lists that show one category show that one.
     */
    suspend fun setParts(txId: Long, parts: List<TxnPart>, joinTo: String? = null): Boolean = write { d ->
        val t = d.query("SELECT $TX_COLS FROM transactions WHERE id = ?", arrayOf<Any?>(txId)).use { c -> c.toTxns().firstOrNull() } ?: return@write false
        if (parts.isEmpty()) {
            val cat = joinTo ?: t.parts.maxByOrNull { it.paise }?.categoryId ?: t.categoryId
            d.update("transactions", CONFLICT_REPLACE, contentValuesOf("parts" to null, "category_id" to cat), "id = ?", arrayOf<Any?>(txId))
            return@write true
        }
        if (t.isMoneyIn || !Parts.valid(parts, t.amountPaise)) return@write false
        d.update(
            "transactions", CONFLICT_REPLACE,
            contentValuesOf("parts" to Parts.encode(parts), "category_id" to parts.maxBy { it.paise }.categoryId, "needs_review" to 0),
            "id = ?", arrayOf<Any?>(txId),
        )
        true
    }

    // ---------------- backup and restore ----------------

    data class RuleRow(val match: String, val pattern: String, val categoryId: String)

    /** Everything a backup is built from, read in one go. */
    data class Snapshot(
        val settings: AppSettings,
        val categories: List<Category>,
        val budgets: Map<String, Long>,
        val rules: List<RuleRow>,
        val transactions: List<Txn>,
        val merchants: List<MerchantOverride> = emptyList(),
        val pictures: Map<String, ByteArray> = emptyMap(),
    )

    suspend fun snapshot(): Snapshot = read { d ->
        val budgets = d.query("SELECT category_key, limit_paise FROM budgets").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }
        val rules = d.query("SELECT match_type, pattern, category_id FROM rules ORDER BY created_at").use { c ->
            buildList { while (c.moveToNext()) add(RuleRow(c.getString(0), c.getString(1), c.getString(2))) }
        }
        val txns = d.query("SELECT $TX_COLS FROM transactions ORDER BY occurred_at, id").use { c -> c.toTxns() }
        Snapshot(readSettings(d), readCategories(d), budgets, rules, txns, readMerchants(d), readPictures(d))
    }

    data class RestoreCounts(val added: Int, val skipped: Int, val categoriesAdded: Int, val budgetsSet: Int, val rulesAdded: Int)

    /**
     * Writes a checked backup in one database transaction, so it is all or nothing.
     *
     * [replace]: this phone's transactions, categories, budgets and rules are replaced by the backup's.
     * Otherwise the backup is added: a payment already here (same amount and direction within two
     * minutes) is skipped, and this phone's own categories, budgets and rules are kept.
     * [finishOnboarding]: a new phone restoring from its welcome screen, which then counts as set up.
     */
    suspend fun restore(b: BackupContent, replace: Boolean, finishOnboarding: Boolean): RestoreCounts = write { d ->
        if (replace) {
            d.delete("transactions", null, null)
            d.delete("rules", null, null)
            d.delete("budgets", null, null)
            d.delete("categories", null, null)
            d.delete("merchants", null, null)
            d.delete("pictures", null, null)
        }

        fun categoryIds(): Set<String> = d.query("SELECT id FROM categories").use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

        val before = categoryIds()
        var categoriesAdded = 0
        b.categories.forEach { cat ->
            if (cat.id in before) return@forEach
            val row = contentValuesOf(
                "id" to cat.id, "name" to cat.name, "icon" to cat.icon, "color" to cat.color, "kind" to cat.kind.name,
                "sort_order" to cat.sortOrder, "is_system" to if (cat.isSystem) 1 else 0, "archived" to if (cat.archived) 1 else 0,
            )
            if (d.insert("categories", CONFLICT_IGNORE, row) != -1L) categoriesAdded++
        }
        // The built-in categories must always exist, even if a backup file left some out.
        Schema.DEFAULT_CATEGORIES.forEachIndexed { i, s ->
            d.insert(
                "categories", CONFLICT_IGNORE,
                contentValuesOf("id" to s.id, "name" to s.name, "icon" to s.icon, "color" to s.color, "kind" to s.kind.name, "sort_order" to i, "is_system" to 1, "archived" to 0),
            )
        }
        val ids = categoryIds()

        var budgetsSet = 0
        b.budgets.forEach { (key, limit) ->
            if (key != Schema.OVERALL && key !in ids) return@forEach
            if (d.insert("budgets", CONFLICT_IGNORE, contentValuesOf("category_key" to key, "limit_paise" to limit)) != -1L) budgetsSet++
        }

        var rulesAdded = 0
        b.rules.forEach { r ->
            if (r.categoryKey !in ids) return@forEach
            val row = contentValuesOf("match_type" to r.match.name, "pattern" to r.pattern, "category_id" to r.categoryKey, "created_at" to clock.millis())
            if (d.insert("rules", CONFLICT_IGNORE, row) != -1L) rulesAdded++
        }

        // Pictures first, so the names and icons chosen on the other phone can use them.
        b.pictures.forEach { (id, png) ->
            if (MerchantPicture.isPng(png) && MerchantPicture.idFor(png) == id) d.insert("pictures", CONFLICT_IGNORE, contentValuesOf("id" to id, "png" to png))
        }
        // Names and icons chosen on the other phone; when adding, this phone's own choices win.
        b.merchants.forEach { m ->
            d.insert("merchants", CONFLICT_IGNORE, contentValuesOf("merchant_key" to m.key, "name" to m.name, "logo" to m.logo, "updated_at" to clock.millis()))
        }

        // Payments already on this phone, by amount and direction. Each can absorb one copy from the backup.
        val here = HashMap<Pair<Long, String>, MutableList<Long>>()
        if (!replace) {
            d.query("SELECT amount_paise, direction, occurred_at FROM transactions").use { c ->
                while (c.moveToNext()) here.getOrPut(c.getLong(0) to c.getString(1)) { mutableListOf() }.add(c.getLong(2))
            }
        }
        var added = 0
        var skipped = 0
        b.transactions.forEach { t ->
            val times = here[t.amountPaise to t.direction.name]
            val i = times?.indexOfFirst { kotlin.math.abs(it - t.occurredAt) <= RESTORE_DUPLICATE_MS } ?: -1
            if (i >= 0) {
                times!!.removeAt(i)
                skipped++
                return@forEach
            }
            // A split whose categories aren't all here is restored as one payment in its own category
            val row = (if (t.categoryId in ids) t else t.copy(categoryId = Cat.OTHERS))
                .let { r -> if (r.parts.all { it.categoryId in ids }) r else r.copy(parts = emptyList()) }
            if (d.insert("transactions", CONFLICT_IGNORE, row.toValues()) != -1L) added++
        }

        val s = readSettings(d)
        val next = s.copy(
            budgetStartDay = if (replace || finishOnboarding) b.budgetStartDay else s.budgetStartDay,
            onboarded = s.onboarded || finishOnboarding,
            sampleData = if (replace) false else s.sampleData,
        )
        next.toMap().forEach { (k, v) -> d.insert("settings", CONFLICT_REPLACE, contentValuesOf("key" to k, "value" to v)) }
        prunePictures(d)

        RestoreCounts(added, skipped, categoriesAdded, budgetsSet, rulesAdded)
    }

    companion object {
        /** A restored payment within this time of one already here, same amount and direction, is the same payment. */
        const val RESTORE_DUPLICATE_MS = 2 * 60_000L

        /** Longest merchant name the user can type. */
        const val MAX_NAME = 60

        private const val TX_COLS = "id, amount_paise, direction, occurred_at, merchant_name, merchant_key, merchant_package, vpa, category_id, " +
            "account_label, sender_group, sender, ref_no, source, sms_id, needs_review, excluded, note, kind, sms_body, parts"

        private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)

        private fun Cursor.toTxns(): List<Txn> = buildList {
            while (moveToNext()) add(
                Txn(
                    id = getLong(0), amountPaise = getLong(1),
                    direction = if (getString(2) == Direction.CREDIT.name) Direction.CREDIT else Direction.DEBIT,
                    occurredAt = getLong(3), merchantName = getString(4), merchantKey = getString(5),
                    merchantPackage = str(6), vpa = str(7), categoryId = getString(8), accountLabel = str(9),
                    senderGroup = str(10), sender = str(11), refNo = str(12),
                    source = runCatching { Source.valueOf(getString(13)) }.getOrDefault(Source.SMS),
                    smsId = if (isNull(14)) null else getLong(14), needsReview = getInt(15) == 1, excluded = getInt(16) == 1,
                    note = str(17), kind = runCatching { TxKind.valueOf(getString(18)) }.getOrDefault(TxKind.NORMAL), smsBody = str(19),
                    parts = Parts.decode(str(20)),
                )
            )
        }

        private fun NewTxn.toValues() = contentValuesOf(
            "amount_paise" to amountPaise, "direction" to direction.name, "occurred_at" to occurredAt,
            "merchant_name" to merchantName, "merchant_key" to merchantKey, "merchant_package" to merchantPackage,
            "vpa" to vpa, "category_id" to categoryId, "account_label" to accountLabel, "sender_group" to senderGroup,
            "sender" to sender, "ref_no" to refNo, "source" to source.name, "sms_id" to smsId,
            "needs_review" to if (needsReview) 1 else 0, "excluded" to if (excluded) 1 else 0, "note" to note,
            "kind" to kind.name, "sms_body" to smsBody, "parts" to Parts.encode(parts),
        )

        fun Txn.toNew() = NewTxn(
            amountPaise, direction, occurredAt, merchantName, merchantKey, merchantPackage, vpa, categoryId, accountLabel,
            senderGroup, sender, refNo, source, smsId, needsReview, note, kind, smsBody, excluded, parts,
        )
    }
}
