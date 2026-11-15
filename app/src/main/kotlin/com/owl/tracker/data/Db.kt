package com.owl.tracker.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.owl.tracker.core.Cat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The app's single database. On a phone it is SQLCipher (AES-256, key wrapped by Android Keystore);
 * in JVM tests it is plain framework SQLite in memory. Both sit behind the same SupportSQLite interface.
 *
 * Every write bumps [changes]; screens re-query when it moves, which keeps the UI live without an ORM.
 */
class Db(
    private val context: Context,
    private val factoryProvider: () -> SupportSQLiteOpenHelper.Factory,
    private val name: String?,
) {
    private val lock = Any()
    private var helper: SupportSQLiteOpenHelper? = null
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes

    fun get(): SupportSQLiteDatabase = synchronized(lock) {
        val h = helper ?: factoryProvider().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(SchemaCallback())
                .build()
        ).also { helper = it }
        h.writableDatabase
    }

    fun notifyChanged() = _changes.update { it + 1 }

    fun <T> transaction(block: (SupportSQLiteDatabase) -> T): T {
        val db = get()
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    /** Closes the database without deleting it. The next [get] reopens it. */
    fun close() {
        synchronized(lock) {
            runCatching { helper?.close() }
            helper = null
        }
    }

    /** Closes and deletes the database file. The next [get] creates a fresh one. */
    fun destroy() {
        synchronized(lock) {
            runCatching { helper?.close() }
            helper = null
            if (name != null) context.deleteDatabase(name)
        }
        notifyChanged()
    }

    private class SchemaCallback : SupportSQLiteOpenHelper.Callback(Schema.VERSION) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            Schema.CREATE.forEach { db.execSQL(it) }
            Schema.seed(db)
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) Schema.upgradeTo2(db)
            if (oldVersion < 3) Schema.upgradeTo3(db)
            if (oldVersion < 4) Schema.upgradeTo4(db)
            if (oldVersion < 5) Schema.upgradeTo5(db)
            if (oldVersion < 6) Schema.upgradeTo6(db)
        }
    }
}

object Schema {
    /**
     * 1: first release. 2: Sports & Fitness category, and the merchants table (names and icons the user chose).
     * 3: pictures. 4: payments split across categories (transactions.parts). 5: Holiday.
     * 6: groups of categories (categories.parent): Shopping into Fashion & Clothing, Phones & Electronics and the rest.
     */
    const val VERSION = 6
    const val OVERALL = "__overall__"

    val CREATE = listOf(
        """CREATE TABLE categories(
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            icon TEXT NOT NULL,
            color INTEGER NOT NULL,
            kind TEXT NOT NULL,
            sort_order INTEGER NOT NULL,
            is_system INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0,
            parent TEXT)""",
        """CREATE TABLE transactions(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            amount_paise INTEGER NOT NULL,
            direction TEXT NOT NULL,
            occurred_at INTEGER NOT NULL,
            merchant_name TEXT NOT NULL,
            merchant_key TEXT NOT NULL,
            merchant_package TEXT,
            vpa TEXT,
            category_id TEXT NOT NULL,
            account_label TEXT,
            sender_group TEXT,
            sender TEXT,
            ref_no TEXT,
            source TEXT NOT NULL,
            sms_id INTEGER,
            needs_review INTEGER NOT NULL DEFAULT 0,
            excluded INTEGER NOT NULL DEFAULT 0,
            note TEXT,
            kind TEXT NOT NULL DEFAULT 'NORMAL',
            sms_body TEXT,
            parts TEXT)""",
        "CREATE UNIQUE INDEX idx_tx_sms ON transactions(sms_id) WHERE sms_id IS NOT NULL",
        "CREATE INDEX idx_tx_time ON transactions(occurred_at)",
        "CREATE INDEX idx_tx_merchant ON transactions(merchant_key)",
        """CREATE TABLE rules(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            match_type TEXT NOT NULL,
            pattern TEXT NOT NULL,
            category_id TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE(match_type, pattern))""",
        "CREATE TABLE budgets(category_key TEXT PRIMARY KEY NOT NULL, limit_paise INTEGER NOT NULL)",
        // Remembers single transactions the user deleted, so no later scan or re-import brings them back.
        "CREATE TABLE deleted_sms(sms_id INTEGER PRIMARY KEY NOT NULL, deleted_at INTEGER NOT NULL)",
        "CREATE TABLE settings(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
        MERCHANTS_TABLE,
        PICTURES_TABLE,
    )

    /**
     * What the user chose for a merchant (by merchant key): a name, and which icon to show.
     * logo: NULL = automatic, '' = no app icon (category icon), otherwise an app package.
     * Applied to new imports, so they match what the user renamed or re-iconed.
     */
    private const val MERCHANTS_TABLE =
        "CREATE TABLE IF NOT EXISTS merchants(merchant_key TEXT PRIMARY KEY NOT NULL, name TEXT, logo TEXT, updated_at INTEGER NOT NULL)"

    /**
     * Pictures the user chose as merchant icons: small square PNGs, by id ("pic:" + hash of the PNG).
     * A merchant's logo, and its payments' merchant_package, then hold that id.
     */
    private const val PICTURES_TABLE =
        "CREATE TABLE IF NOT EXISTS pictures(id TEXT PRIMARY KEY NOT NULL, png BLOB NOT NULL)"

    /** 3: pictures as merchant icons. */
    fun upgradeTo3(db: SupportSQLiteDatabase) {
        db.execSQL(PICTURES_TABLE)
    }

    /** 4: a payment can be split across categories ("shopping=1299900|groceries=153709"; NULL when it isn't). */
    fun upgradeTo4(db: SupportSQLiteDatabase) {
        val has = db.query("PRAGMA table_info(transactions)").use { c ->
            val name = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) if (c.getString(name) == "parts") found = true
            found
        }
        if (!has) db.execSQL("ALTER TABLE transactions ADD COLUMN parts TEXT")
    }

    /** A built-in category. [parent]: the group it sits in (null for a group, or one that sits on its own). */
    data class SeedCategory(val id: String, val name: String, val icon: String, val color: Long, val kind: CategoryKind, val parent: String? = null)

    private fun e(id: String, name: String, icon: String, color: Long, parent: String? = null) = SeedCategory(id, name, icon, color, CategoryKind.EXPENSE, parent)

    /** In the order they're listed: each group, then its categories; then the ones on their own. */
    val DEFAULT_CATEGORIES = listOf(
        e(Cat.TRANSPORT, "Getting around", "transport", 0xFF2A6FB5),
        e(Cat.COMMUTE, "Daily Commute", "commute", 0xFF3B82C4, Cat.TRANSPORT),
        e(Cat.FUEL, "Fuel", "fuel", 0xFF9A6B2F, Cat.TRANSPORT),
        e(Cat.CAR_BIKE, "Car & Bike", "car", 0xFF4F8A9E, Cat.TRANSPORT),
        e(Cat.SHOPPING, "Shopping", "shopping", 0xFF7552C4),
        e(Cat.FASHION, "Fashion & Clothing", "fashion", 0xFF8B4FC9, Cat.SHOPPING),
        e(Cat.ELECTRONICS, "Phones & Electronics", "electronics", 0xFF4A5BD4, Cat.SHOPPING),
        e(Cat.FURNITURE, "Home & Furniture", "furniture", 0xFFA0673A, Cat.SHOPPING),
        e(Cat.BEAUTY, "Beauty & Personal Care", "beauty", 0xFFD2528F, Cat.SHOPPING),
        e(Cat.JEWELLERY, "Jewellery", "jewellery", 0xFFC99A1E, Cat.SHOPPING),
        e(Cat.FOOD, "Food", "food", 0xFFD9622B),
        e(Cat.FOOD_DELIVERY, "Food Delivery", "delivery", 0xFFE07A2E, Cat.FOOD),
        e(Cat.DINING, "Eating Out", "coffee", 0xFFB5452A, Cat.FOOD),
        e(Cat.GROCERIES, "Groceries", "groceries", 0xFF3F8A3A, Cat.FOOD),
        e(Cat.KIDS, "Kids", "kids", 0xFFE0569A),
        e(Cat.KIDS_EDUCATION, "Kids' Education", "school", 0xFF5866B8, Cat.KIDS),
        e(Cat.KIDS_ACTIVITIES, "Kids' Sports & Extracurricular", "kids_sports", 0xFFD35A3A, Cat.KIDS),
        e(Cat.KIDS_CARE, "Kids' Shopping & Care", "toys", 0xFFE87FB0, Cat.KIDS),
        e(Cat.BILLS, "Home & Bills", "bills", 0xFF9E740C),
        e(Cat.RENT, "Rent", "rent", 0xFF6B7D3A, Cat.BILLS),
        e(Cat.UTILITIES, "Electricity, Water & Gas", "utilities", 0xFFD49A00, Cat.BILLS),
        e(Cat.RECHARGE, "Mobile & Internet", "recharge", 0xFFC23F73, Cat.BILLS),
        e(Cat.SUBSCRIPTIONS, "OTT & Subscriptions", "subscriptions", 0xFF9B3FA8, Cat.BILLS),
        e(Cat.HOME_HELP, "Home Help & Services", "home_help", 0xFF7A6A58, Cat.BILLS),
        e(Cat.INSURANCE, "Insurance", "insurance", 0xFF3E6D8E, Cat.BILLS),
        e(Cat.HEALTH, "Health", "health", 0xFF1F8F86),
        e(Cat.MEDICINES, "Medicines", "medicines", 0xFF2A9D8F, Cat.HEALTH),
        e(Cat.DOCTOR, "Doctor & Tests", "doctor", 0xFF4C9F70, Cat.HEALTH),
        e(Cat.FUN, "Fun & Travel", "fun", 0xFF11A3B5),
        e(Cat.ENTERTAINMENT, "Movies & Events", "entertainment", 0xFFB04FA5, Cat.FUN),
        e(Cat.SPORTS, "Sports & Fitness", "sports", 0xFFC8423B, Cat.FUN),
        e(Cat.TRAVEL, "Travel", "travel", 0xFF3A86C9, Cat.FUN),
        e(Cat.HOLIDAY, "Holiday", "holiday", 0xFF0E8FA0, Cat.FUN),
        e(Cat.EDUCATION, "Courses & Books", "education", 0xFF5866B8, Cat.FUN),
        e(Cat.GIVING, "Gifts & Giving", "gift", 0xFFA3416B),
        e(Cat.TEMPLE, "Temple & Donations", "temple", 0xFFE07B00, Cat.GIVING),
        e(Cat.GIFTS, "Gifts & Functions", "gift", 0xFFC23F73, Cat.GIVING),
        e(Cat.EMI, "EMI & Loans", "emi", 0xFF8A5A44),
        e(Cat.CASH, "Cash", "cash", 0xFF5E7480),
        e(Cat.PETS, "Pets", "pet", 0xFFA0673A),
        e(Cat.OTHERS, "Others", "others", 0xFF7F8682),
        SeedCategory(Cat.INVESTMENTS, "Investments", "investments", 0xFF2E7D5B, CategoryKind.SAVING),
        SeedCategory(Cat.TRANSFERS, "Transfers", "transfer", 0xFF6F787D, CategoryKind.TRANSFER),
        SeedCategory(Cat.INCOME, "Income", "income", 0xFF0F6B4F, CategoryKind.INCOME),
    )

    /** Built-in names before 1.8 that became a group or a narrower category: renamed only if the user kept the old name. */
    private val OLD_NAMES = mapOf(
        Cat.TRANSPORT to "Transport", Cat.FOOD to "Food & Dining", Cat.BILLS to "Bills & Utilities",
        Cat.RECHARGE to "Recharge & Subscriptions", Cat.ENTERTAINMENT to "Entertainment", Cat.EDUCATION to "Education",
    )

    /**
     * Version 1 to 2, on phones that already have data. Adds the merchants table and the Sports & Fitness
     * category, placed right after Entertainment; every other category keeps its order.
     */
    fun upgradeTo2(db: SupportSQLiteDatabase) {
        db.execSQL(MERCHANTS_TABLE)
        val exists = db.query("SELECT COUNT(*) FROM categories WHERE id = ?", arrayOf<Any?>(Cat.SPORTS)).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
        if (exists) return
        insertAfter(db, Cat.SPORTS, Cat.ENTERTAINMENT)
    }

    /** Version 4 to 5: the Holiday category (trips and vacations), right after Travel. */
    fun upgradeTo5(db: SupportSQLiteDatabase) {
        val exists = db.query("SELECT COUNT(*) FROM categories WHERE id = ?", arrayOf<Any?>(Cat.HOLIDAY)).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
        if (!exists) insertAfter(db, Cat.HOLIDAY, Cat.TRAVEL)
    }

    /**
     * Version 5 to 6: categories in groups. Old categories that became a group (Shopping, Transport as Getting around,
     * Food & Dining as Food, Bills & Utilities as Home & Bills, Health) keep their payments, which are sorted into the
     * group's categories once afterwards (ImportEngine.sortIntoNewCategories). See [placeBuiltIns].
     */
    fun upgradeTo6(db: SupportSQLiteDatabase) {
        val has = db.query("PRAGMA table_info(categories)").use { c ->
            val name = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) if (c.getString(name) == "parent") found = true
            found
        }
        if (!has) db.execSQL("ALTER TABLE categories ADD COLUMN parent TEXT")
        placeBuiltIns(db)
    }

    /**
     * Puts every built-in category in place: adds the missing ones, gives each its group, renames the old ones that
     * changed meaning (only if they still have their old name), lists them in the built-in order with the user's own
     * after them, and moves budgets set on a category inside a group onto the group (budgets are per group now).
     * Also used after restoring a backup made before 1.8.
     */
    fun placeBuiltIns(db: SupportSQLiteDatabase) {
        val existing = db.query("SELECT id, name, archived FROM categories").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1) to (c.getInt(2) == 1)) }
        }
        DEFAULT_CATEGORIES.forEachIndexed { i, s ->
            val here = existing[s.id]
            if (here == null) {
                // A new category in a group the user had archived stays out of sight too
                val hidden = s.parent?.let { existing[it]?.second } == true
                db.execSQL(
                    "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived, parent) VALUES(?,?,?,?,?,?,1,?,?)",
                    arrayOf<Any?>(s.id, s.name, s.icon, s.color, s.kind.name, i, if (hidden) 1 else 0, s.parent),
                )
            } else {
                db.execSQL("UPDATE categories SET parent = ?, sort_order = ? WHERE id = ? AND is_system = 1", arrayOf<Any?>(s.parent, i, s.id))
                if (OLD_NAMES[s.id] == here.first) db.execSQL("UPDATE categories SET name = ? WHERE id = ?", arrayOf<Any?>(s.name, s.id))
            }
        }
        // The user's own categories after the built-in ones, in the order they had
        db.execSQL("UPDATE categories SET sort_order = 100 + sort_order WHERE is_system = 0 AND sort_order < 100")
        // A budget on a category inside a group moves onto the group, added to any the group had
        val budgets = db.query("SELECT category_key, limit_paise FROM budgets").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }
        val parents = db.query("SELECT id, parent FROM categories WHERE parent IS NOT NULL").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        budgets.filterKeys { it in parents }.forEach { (child, limit) ->
            val group = parents.getValue(child)
            val now = db.query("SELECT limit_paise FROM budgets WHERE category_key = ?", arrayOf<Any?>(group)).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            db.execSQL("INSERT OR REPLACE INTO budgets(category_key, limit_paise) VALUES(?, ?)", arrayOf<Any?>(group, now + limit))
            db.execSQL("DELETE FROM budgets WHERE category_key = ?", arrayOf<Any?>(child))
        }
    }

    /** Adds a built-in category just after [afterId] (at the end if that one is gone); every other keeps its order. */
    private fun insertAfter(db: SupportSQLiteDatabase, id: String, afterId: String) {
        val after = db.query("SELECT sort_order FROM categories WHERE id = ?", arrayOf<Any?>(afterId)).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
        val order = if (after != null) {
            db.execSQL("UPDATE categories SET sort_order = sort_order + 1 WHERE sort_order > ?", arrayOf<Any?>(after))
            after + 1
        } else {
            db.query("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories").use { c -> c.moveToFirst(); c.getInt(0) }
        }
        val s = DEFAULT_CATEGORIES.first { it.id == id }
        db.execSQL(
            "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived) VALUES(?,?,?,?,?,?,1,0)",
            arrayOf<Any?>(s.id, s.name, s.icon, s.color, s.kind.name, order)
        )
    }

    fun seed(db: SupportSQLiteDatabase) {
        DEFAULT_CATEGORIES.forEachIndexed { i, c ->
            db.execSQL(
                "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived, parent) VALUES(?,?,?,?,?,?,1,0,?)",
                arrayOf<Any?>(c.id, c.name, c.icon, c.color, c.kind.name, i, c.parent)
            )
        }
    }
}
