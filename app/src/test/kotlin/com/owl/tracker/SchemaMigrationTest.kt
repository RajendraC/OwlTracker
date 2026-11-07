package com.owl.tracker

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.core.Cat
import com.owl.tracker.data.Db
import com.owl.tracker.data.Repository
import com.owl.tracker.data.Schema
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
    private val v1Categories = Schema.DEFAULT_CATEGORIES.filterNot { it.id == Cat.SPORTS }

    /** Builds a version 1 database exactly as the first release left it, with one custom category and one payment. */
    private fun createVersion1() {
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    Schema.CREATE.filterNot { it.contains(" merchants(") }.forEach { db.execSQL(it) }
                    v1Categories.forEachIndexed { i, c ->
                        db.execSQL(
                            "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived) VALUES(?,?,?,?,?,?,1,0)",
                            arrayOf<Any?>(c.id, c.name, c.icon, c.color, c.kind.name, i),
                        )
                    }
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
    fun version1GainsSportsAndMerchantNamesAndKeepsEverythingElse() = runBlocking {
        createVersion1()
        val repo = Repository(Db(context, { FrameworkSQLiteOpenHelperFactory() }, name), TEST_CLOCK)

        val ids = repo.categories().map { it.id }
        val expected = v1Categories.map { it.id }.toMutableList()
        expected.add(expected.indexOf(Cat.ENTERTAINMENT) + 1, Cat.SPORTS)
        assertEquals("Sports & Fitness sits after Entertainment; everything else keeps its order", expected + "c_1", ids)

        val tx = repo.transactions(0, Long.MAX_VALUE).single()
        assertEquals("KAMAL STORES", tx.merchantName)
        assertEquals(1, repo.renameMerchant(tx.id, "Kamal Stores (milk)", all = true))
        assertEquals("Kamal Stores (milk)", repo.merchantOverrides().getValue("kamal_stores").name)
        assertTrue(repo.categories().first { it.id == Cat.SPORTS }.isSystem)
        context.deleteDatabase(name)
    }
}
