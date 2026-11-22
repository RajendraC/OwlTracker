package com.owl.tracker

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.core.Cat
import com.owl.tracker.core.CategoryGroups
import com.owl.tracker.core.MerchantPicture
import com.owl.tracker.data.Db
import com.owl.tracker.data.Repository
import com.owl.tracker.data.Schema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A phone that had the first release (database version 1) keeps everything when it updates. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SchemaMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "owl-migration-test.db"
    /** The first release's built-in categories, as it listed them. */
    private val v1Categories = listOf(
        Triple(Cat.FOOD, "Food & Dining", "food"), Triple(Cat.GROCERIES, "Groceries", "groceries"), Triple(Cat.SHOPPING, "Shopping", "shopping"),
        Triple(Cat.TRANSPORT, "Transport", "transport"), Triple(Cat.FUEL, "Fuel", "fuel"), Triple(Cat.BILLS, "Bills & Utilities", "bills"),
        Triple(Cat.RECHARGE, "Recharge & Subscriptions", "recharge"), Triple(Cat.ENTERTAINMENT, "Entertainment", "entertainment"),
        Triple(Cat.HEALTH, "Health", "health"), Triple(Cat.TRAVEL, "Travel", "travel"), Triple(Cat.EDUCATION, "Education", "education"),
        Triple(Cat.RENT, "Rent", "rent"), Triple(Cat.EMI, "EMI & Loans", "emi"), Triple(Cat.INVESTMENTS, "Investments", "investments"),
        Triple(Cat.CASH, "Cash", "cash"), Triple(Cat.TRANSFERS, "Transfers", "transfer"), Triple(Cat.OTHERS, "Others", "others"),
        Triple(Cat.INCOME, "Income", "income"),
    )
    private fun kindOf(id: String) = when (id) {
        Cat.INVESTMENTS -> "SAVING"
        Cat.TRANSFERS -> "TRANSFER"
        Cat.INCOME -> "INCOME"
        else -> "EXPENSE"
    }

    /** Builds a version 1 database exactly as the first release left it, with one custom category and one payment. */
    private fun createVersion1() {
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Version 1 had no merchants or pictures tables, payments had no parts column, categories no groups
                    Schema.CREATE.filterNot { it.contains(" merchants(") || it.contains(" pictures(") }
                        .map { it.replace(Regex(""",\s*parts TEXT\)"""), ")").replace(Regex(""",\s*parent TEXT\)"""), ")") }
                        .forEach { db.execSQL(it) }
                    v1Categories.forEachIndexed { i, (id, label, icon) ->
                        db.execSQL(
                            "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived) VALUES(?,?,?,?,?,?,1,0)",
                            arrayOf<Any?>(id, label, icon, 4280000000L, kindOf(id), i),
                        )
                    }
                    // Budgets on Food & Dining and on Groceries: both end up on the Food group
                    db.execSQL("INSERT INTO budgets(category_key, limit_paise) VALUES('food', 200000), ('groceries', 500000), ('transport', 300000)")
                    db.execSQL(
                        "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived) VALUES('c_1','Pets','pet',4280000000,'EXPENSE',18,0,0)",
                    )
                    db.execSQL(
                        "INSERT INTO transactions(amount_paise, direction, occurred_at, merchant_name, merchant_key, category_id, source) " +
                            "VALUES(25000, 'DEBIT', ?, 'KAMAL STORES', 'kamal_stores', 'groceries', 'SMS')",
                        arrayOf<Any?>(TEST_CLOCK.millis()),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase
        helper.close()
    }

    @Test
    fun version1GainsGroupsSportsMerchantNamesAndPicturesAndKeepsEverythingElse() = runBlocking {
        createVersion1()
        val repo = Repository(Db(context, { FrameworkSQLiteOpenHelperFactory() }, name), TEST_CLOCK)

        // Every built-in category, in groups, in the built-in order; the user's own after them
        val cats = repo.categories()
        assertEquals(Schema.DEFAULT_CATEGORIES.map { it.id } + "c_1", cats.map { it.id })
        assertEquals(Schema.DEFAULT_CATEGORIES.map { it.parent } + null, cats.map { it.parent })
        assertTrue(cats.first { it.id == Cat.HOLIDAY }.isSystem)
        assertTrue(cats.first { it.id == Cat.FASHION }.isSystem)
        // The old categories that became groups or narrower ones are renamed; the rest keep their names
        val names = cats.associate { it.id to it.name }
        assertEquals("Food", names[Cat.FOOD])
        assertEquals("Getting around", names[Cat.TRANSPORT])
        assertEquals("Home & Bills", names[Cat.BILLS])
        assertEquals("Mobile & Internet", names[Cat.RECHARGE])
        assertEquals("Movies & Events", names[Cat.ENTERTAINMENT])
        assertEquals("Courses & Books", names[Cat.EDUCATION])
        assertEquals("Groceries", names[Cat.GROCERIES])
        assertEquals("Pets", names["c_1"])
        // Budgets are per group: Groceries' moved onto Food, added to Food & Dining's
        val budgets = repo.observeBudgets().first()
        assertEquals(mapOf(Cat.FOOD to 7_000_00L, Cat.TRANSPORT to 3_000_00L), budgets)

        val tx = repo.transactions(0, Long.MAX_VALUE).single()
        assertEquals("KAMAL STORES", tx.merchantName)
        assertEquals(1, repo.renameMerchant(tx.id, "Kamal Stores (milk)", all = true))
        assertEquals("Kamal Stores (milk)", repo.merchantOverrides().getValue("kamal_stores").name)
        assertTrue(repo.categories().first { it.id == Cat.SPORTS }.isSystem)

        // Version 3: pictures as icons
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64) { 7 }
        assertEquals(1, repo.setMerchantPicture(tx.id, png))
        val id = MerchantPicture.idFor(png)
        assertEquals(id, repo.transactions(0, Long.MAX_VALUE).single().merchantPackage)
        assertTrue(repo.pictureBytes(id)!!.contentEquals(png))
        // Version 4: a payment can be split across categories
        val amount = repo.transactions(0, Long.MAX_VALUE).single().amountPaise
        val parts = listOf(com.owl.tracker.data.TxnPart(Cat.GROCERIES, amount - 100), com.owl.tracker.data.TxnPart(Cat.SHOPPING, 100))
        assertTrue(repo.setParts(tx.id, parts))
        assertEquals(parts, repo.transactions(0, Long.MAX_VALUE).single().parts)
        assertEquals(Cat.GROCERIES, repo.transactions(0, Long.MAX_VALUE).single().categoryId)
        assertTrue(context.deleteDatabase(name))
    }

    /** A version 6 database as 1.8.0 left it: Sports & Fitness in Fun & Travel (or where the user put it), a Fun & Travel budget. */
    private fun createVersion6(sportsParent: String?, healthArchived: Boolean = false, kidsArchived: Boolean = false) {
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    Schema.CREATE.forEach { db.execSQL(it) }
                    // 1.8.0's order: Sports & Fitness after Movies & Events, in Fun & Travel
                    val old = Schema.DEFAULT_CATEGORIES.map { it.id }.toMutableList().apply {
                        remove(Cat.SPORTS)
                        add(indexOf(Cat.ENTERTAINMENT) + 1, Cat.SPORTS)
                        // Before 1.9.12: no Baby Essentials, Daycare & Nanny, Kids' Health & Vaccines or Toys
                        removeAll(CategoryGroups.NEW_KIDS)
                        // nor Metro (1.9.13)
                        remove(Cat.METRO)
                    }
                    old.forEachIndexed { i, id ->
                        val c = Schema.DEFAULT_CATEGORIES.first { it.id == id }
                        val parent = if (id == Cat.SPORTS) sportsParent else c.parent
                        val archived = healthArchived && (id == Cat.HEALTH || parent == Cat.HEALTH) ||
                            kidsArchived && (id == Cat.KIDS || parent == Cat.KIDS)
                        val (cname, icon) = if (id == Cat.KIDS_CARE) "Kids' Shopping & Care" to "toys" else c.name to c.icon
                        db.execSQL(
                            "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived, parent) VALUES(?,?,?,?,?,?,1,?,?)",
                            arrayOf<Any?>(c.id, cname, icon, c.color, c.kind.name, i, if (archived) 1 else 0, parent),
                        )
                    }
                    db.execSQL("INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived, parent) VALUES('c_1','Badminton club','sports',4280000000,'EXPENSE',101,0,0,'fun')")
                    db.execSQL("INSERT INTO budgets(category_key, limit_paise) VALUES('fun', 600000), ('health', 300000)")
                    db.execSQL(
                        "INSERT INTO transactions(amount_paise, direction, occurred_at, merchant_name, merchant_key, category_id, source) " +
                            "VALUES(149900, 'DEBIT', ?, 'CULT.FIT', 'cultfit', 'sports', 'SMS')",
                        arrayOf<Any?>(TEST_CLOCK.millis()),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase
        helper.close()
    }

    @Test
    fun version6MovesSportsAndFitnessIntoHealth() = runBlocking {
        createVersion6(sportsParent = Cat.FUN)
        val repo = Repository(Db(context, { FrameworkSQLiteOpenHelperFactory() }, name), TEST_CLOCK)
        val cats = repo.categories()
        assertEquals(Schema.DEFAULT_CATEGORIES.map { it.id } + "c_1", cats.map { it.id })
        assertEquals(Cat.HEALTH, cats.first { it.id == Cat.SPORTS }.parent)
        assertEquals(listOf(Cat.MEDICINES, Cat.DOCTOR, Cat.SPORTS), cats.filter { it.parent == Cat.HEALTH }.map { it.id })
        assertEquals(listOf(Cat.ENTERTAINMENT, Cat.TRAVEL, Cat.HOLIDAY, Cat.EDUCATION, "c_1"), cats.filter { it.parent == Cat.FUN }.map { it.id })
        // The payment stays in Sports & Fitness; budgets stay where they were
        assertEquals(Cat.SPORTS, repo.transactions(0, Long.MAX_VALUE).single().categoryId)
        assertEquals(mapOf(Cat.FUN to 6_000_00L, Cat.HEALTH to 3_000_00L), repo.observeBudgets().first())
        assertTrue(context.deleteDatabase(name))
    }

    @Test
    fun version7GetsTheNewKidsCategories() = runBlocking {
        createVersion6(sportsParent = null)
        Repository(Db(context, { FrameworkSQLiteOpenHelperFactory() }, name), TEST_CLOCK).let { repo ->
            val cats = repo.categories().associateBy { it.id }
            for (id in CategoryGroups.NEW_KIDS) {
                assertEquals(Cat.KIDS, cats.getValue(id).parent)
                assertEquals(false, cats.getValue(id).archived)
            }
            assertEquals("Kids' Clothes", cats.getValue(Cat.KIDS_CARE).name)
            assertEquals("kids_clothes", cats.getValue(Cat.KIDS_CARE).icon)
            // In the built-in order: Baby Essentials first in Kids, Kids' Clothes last
            val kids = repo.categories().filter { it.parent == Cat.KIDS }.map { it.id }
            assertEquals(CategoryGroups.CHILDREN.getValue(Cat.KIDS), kids)
            // Metro too, in Getting around
            assertEquals(Cat.TRANSPORT, cats.getValue(Cat.METRO).parent)
            assertEquals("metro", cats.getValue(Cat.METRO).icon)
            // and nothing else moved
            assertEquals(null, cats.getValue(Cat.SPORTS).parent)
        }
        assertTrue(context.deleteDatabase(name))

        // Kids hidden (someone without children): the new ones are hidden too
        createVersion6(sportsParent = Cat.HEALTH, kidsArchived = true)
        Repository(Db(context, { FrameworkSQLiteOpenHelperFactory() }, name), TEST_CLOCK).let { repo ->
            val cats = repo.categories().associateBy { it.id }
            for (id in CategoryGroups.NEW_KIDS) assertEquals(true, cats.getValue(id).archived)
        }
        assertTrue(context.deleteDatabase(name))
    }

    @Test
    fun version6LeavesSportsWhereTheUserPutIt() = runBlocking {
        createVersion6(sportsParent = null)
        Repository(Db(context, { FrameworkSQLiteOpenHelperFactory() }, name), TEST_CLOCK).let { repo ->
            assertEquals(null, repo.categories().first { it.id == Cat.SPORTS }.parent)
        }
        assertTrue(context.deleteDatabase(name))

        // Health archived: Sports & Fitness stays in sight, in Fun & Travel
        createVersion6(sportsParent = Cat.FUN, healthArchived = true)
        Repository(Db(context, { FrameworkSQLiteOpenHelperFactory() }, name), TEST_CLOCK).let { repo ->
            val sports = repo.categories().first { it.id == Cat.SPORTS }
            assertEquals(Cat.FUN, sports.parent)
            assertEquals(false, sports.archived)
        }
        assertTrue(context.deleteDatabase(name))
    }
}
