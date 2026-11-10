package com.owl.tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.owl.tracker.AppIcons
import com.owl.tracker.TestApp
import com.owl.tracker.core.Cat
import com.owl.tracker.core.ShopKind
import com.owl.tracker.data.Category
import com.owl.tracker.data.CategoryKind
import com.owl.tracker.ui.components.MerchantAvatar
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.OwlTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Shops with no app get an icon for their kind of shop, from words in their name. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ShopIconTest {

    @get:Rule
    val compose = createComposeRule()

    private val shopping = Category(Cat.SHOPPING, "Shopping", "shopping", 0xFFC23F73, CategoryKind.EXPENSE, 3, true, false)
    private val income = Category(Cat.INCOME, "Income", "income", 0xFF3F8A3A, CategoryKind.INCOME, 18, true, false)

    @Test
    fun shopsWithNoAppGetAShopIcon() {
        val icons = AppIcons(ApplicationProvider.getApplicationContext<Context>())
        compose.setContent {
            OwlTrackerTheme(dark = false, dynamic = false) {
                Column {
                    MerchantAvatar(icons, null, "SRI LAKSHMI JEWELLERS", shopping)
                    MerchantAvatar(icons, null, "Vision Opticals", null)
                    // A salary from a pharma company is money in, not a pharmacy
                    MerchantAvatar(icons, null, "SUN PHARMACEUTICAL INDUSTRIES", income)
                    // A person keeps their initials
                    MerchantAvatar(icons, null, "Ramesh Kumar", null)
                }
            }
        }
        compose.onNodeWithTag("shop_icon_jewellery").assertExists()
        compose.onNodeWithTag("shop_icon_opticals").assertExists()
        compose.onNodeWithTag("shop_icon_pharmacy").assertDoesNotExist()
        compose.onNodeWithText("RK").assertExists()
    }

    @Test
    fun everyKindOfShopHasAnIcon() {
        ShopKind.entries.forEach { kind -> Ic.forShop(kind) }
    }
}
