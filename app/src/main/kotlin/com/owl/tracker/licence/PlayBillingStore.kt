package com.owl.tracker.licence

import com.owl.tracker.i18n.tr
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.owl.tracker.licence.StoreState.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The ₹99-a-year subscription and the ₹999 one-time lifetime unlock, through Google Play Billing.
 *
 * Everything goes through the Play Store app on the phone (Android IPC); Owl Tracker itself still
 * has no internet permission. The Play Store app does the network part, takes the payment and
 * remembers the purchase, so it is also how a purchase is restored on a new phone.
 */
class PlayBillingStore(context: Context, private val licenceKey: String) : UnlockStore, PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(StoreState())
    override val state: StateFlow<StoreState> = _state.asStateFlow()

    @Volatile private var product: ProductDetails? = null
    @Volatile private var lifetimeProduct: ProductDetails? = null

    // Each kind of purchase as Play last listed it (null: not answered yet), so neither answer hides the other
    @Volatile private var subs: List<Purchase>? = null
    @Volatile private var once: List<Purchase>? = null
    @Volatile private var connecting = false

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun refresh() {
        if (client.isReady) {
            loadProduct()
            loadPurchases()
            return
        }
        if (connecting) return
        if (!playStoreInstalled()) {
            _state.update { it.copy(status = Status.UNAVAILABLE, busy = false, message = NO_PLAY_STORE) }
            return
        }
        connecting = true
        _state.update { it.copy(status = Status.CONNECTING, message = null) }
        // If Google Play never answers (a disabled or broken Play Store), say so instead of waiting forever.
        main.removeCallbacksAndMessages(null)
        main.postDelayed({
            if (connecting || _state.value.status == Status.CONNECTING) {
                connecting = false
                _state.update { it.copy(status = Status.UNAVAILABLE, busy = false, message = NOT_RESPONDING) }
            }
        }, CONNECT_TIMEOUT_MS)
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    main.removeCallbacksAndMessages(null)
                    if (result.responseCode == BillingResponseCode.OK) {
                        _state.update { it.copy(status = Status.READY, message = null) }
                        loadProduct()
                        loadPurchases()
                    } else {
                        _state.update { it.copy(status = Status.UNAVAILABLE, busy = false, message = unavailableText(result)) }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Automatic reconnection is on; the next refresh() or purchase reconnects.
                    connecting = false
                }
            })
        } catch (e: Exception) {
            connecting = false
            main.removeCallbacksAndMessages(null)
            _state.update { it.copy(status = Status.UNAVAILABLE, message = NO_PLAY_STORE) }
        }
    }

    private fun playStoreInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(PLAY_STORE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun loadProduct() {
        fun query(id: String, type: String, done: (BillingResult, ProductDetails?) -> Unit) {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()))
                .build()
            client.queryProductDetailsAsync(params) { result, details -> done(result, details.productDetailsList.firstOrNull { it.productId == id }) }
        }
        query(UnlockStore.PRODUCT_ID, BillingClient.ProductType.SUBS) { result, found ->
            if (result.responseCode == BillingResponseCode.OK && found != null) {
                product = found
                _state.update { it.copy(price = priceOf(found)) }
            } else if (result.responseCode == BillingResponseCode.OK) {
                // Play answered but has no such product: it isn't set up (or active) in Play Console yet.
                _state.update { it.copy(message = tr("The subscription isn't on sale in Google Play yet. Please try again later.")) }
            } else {
                _state.update { it.copy(message = errorText(result)) }
            }
        }
        query(UnlockStore.LIFETIME_ID, BillingClient.ProductType.INAPP) { result, found ->
            // Not on sale (yet): the lifetime choice just shows the expected price, and says so if tapped
            if (result.responseCode == BillingResponseCode.OK && found != null) {
                lifetimeProduct = found
                _state.update { it.copy(lifetimePrice = found.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice) }
            }
        }
    }

    private fun loadPurchases() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) { subs = purchases; apply() } else _state.update { it.copy(busy = false) }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) { once = purchases; apply() } else _state.update { it.copy(busy = false) }
        }
    }

    override fun buy(activity: Activity, plan: UnlockStore.Plan) {
        val details = if (plan == UnlockStore.Plan.LIFETIME) lifetimeProduct else product
        if (!client.isReady || (details == null && _state.value.status != Status.READY)) {
            // Keep a real problem (e.g. "not on sale yet") rather than hiding it behind "loading".
            _state.update { it.copy(message = it.message ?: tr("Google Play is still loading the price. Try again in a moment.")) }
            refresh()
            return
        }
        val offerToken = when {
            details == null -> null
            plan == UnlockStore.Plan.LIFETIME -> details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken ?: ""
            else -> yearlyOffer(details)?.offerToken
        }
        if (details == null || offerToken == null) {
            _state.update { it.copy(message = if (plan == UnlockStore.Plan.LIFETIME) tr("The lifetime unlock isn't on sale in Google Play yet. Please try again later.") else tr("The subscription isn't on sale in Google Play yet. Please try again later.")) }
            refresh()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        if (offerToken.isNotEmpty()) productParams.setOfferToken(offerToken)
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build()
        val result = client.launchBillingFlow(activity, flow)
        _state.update {
            if (result.responseCode == BillingResponseCode.OK) it.copy(busy = true, message = null)
            else it.copy(busy = false, message = errorText(result))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                // Only the new purchase is listed: add it to what Play listed before, then ask Play for the full list
                val fresh = purchases.orEmpty()
                subs = (subs.orEmpty().filter { p -> fresh.none { it.purchaseToken == p.purchaseToken } } + fresh.filter { UnlockStore.PRODUCT_ID in it.products }).takeIf { subs != null || it.isNotEmpty() }
                once = (once.orEmpty().filter { p -> fresh.none { it.purchaseToken == p.purchaseToken } } + fresh.filter { UnlockStore.LIFETIME_ID in it.products }).takeIf { once != null || it.isNotEmpty() }
                apply()
                loadPurchases()
            }
            BillingResponseCode.USER_CANCELED -> _state.update { it.copy(busy = false) }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(busy = false, message = null) }
                loadPurchases()
            }
            else -> _state.update { it.copy(busy = false, message = errorText(result)) }
        }
    }

    /**
     * Works out the state from the purchases Play listed for each kind. A kind Play hasn't answered for yet (null)
     * doesn't count as "not bought", so a slow answer never locks the app for a moment.
     */
    private fun apply() {
        val all = subs.orEmpty().filter { UnlockStore.PRODUCT_ID in it.products } + once.orEmpty().filter { UnlockStore.LIFETIME_ID in it.products }
        val genuine = all.filter { PurchaseVerifier.verify(licenceKey, it.originalJson, it.signature) }
        val bought = genuine.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = genuine.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        // Google refunds purchases that aren't acknowledged within 3 days. Retried on every refresh.
        bought.filter { !it.isAcknowledged }.forEach { acknowledge(it) }
        val boughtSub = bought.filter { UnlockStore.PRODUCT_ID in it.products }
        val boughtLife = bought.any { UnlockStore.LIFETIME_ID in it.products }
        _state.update {
            it.copy(
                owned = when {
                    bought.isNotEmpty() -> true
                    subs != null && once != null -> false
                    else -> it.owned
                },
                lifetime = when {
                    boughtLife -> true
                    once != null -> false
                    else -> it.lifetime
                },
                renewing = when {
                    boughtSub.isNotEmpty() -> boughtSub.any { p -> p.isAutoRenewing }
                    subs != null -> null
                    else -> it.renewing
                },
                pending = bought.isEmpty() && pending,
                busy = false,
                message = when {
                    all.isNotEmpty() && genuine.isEmpty() -> tr("Google Play reported a purchase that Owl Tracker couldn't verify. Please contact support.")
                    bought.isNotEmpty() || pending -> null
                    else -> it.message // e.g. "not on sale yet" from the product lookup, which may answer first
                },
            )
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.acknowledgePurchase(params) { /* retried on the next refresh if it didn't go through */ }
    }

    private companion object {
        const val PLAY_STORE = "com.android.vending"
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val NO_PLAY_STORE = "Google Play isn't available on this phone, so it can't take the payment here."
        const val NOT_RESPONDING = "Google Play isn't responding. Check the Play Store app is installed, signed in and up to date, then try again."
    }

    /** The yearly base plan itself (no special offer). */
    private fun yearlyOffer(details: ProductDetails): ProductDetails.SubscriptionOfferDetails? =
        details.subscriptionOfferDetails?.let { offers ->
            offers.firstOrNull { it.basePlanId == UnlockStore.BASE_PLAN && it.offerId == null } ?: offers.firstOrNull { it.basePlanId == UnlockStore.BASE_PLAN }
        }

    private fun priceOf(details: ProductDetails): String? =
        yearlyOffer(details)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    private fun unavailableText(result: BillingResult): String = when (result.responseCode) {
        BillingResponseCode.BILLING_UNAVAILABLE ->
            tr("Google Play can't take payments on this phone right now. Check that the Play Store app is installed and you're signed in.")
        BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.NETWORK_ERROR ->
            tr("Google Play can't be reached. Check your phone is online, then try again.")
        else -> tr("Google Play isn't available right now (code {0}).", result.responseCode)
    }

    private fun errorText(result: BillingResult): String = when (result.responseCode) {
        BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.NETWORK_ERROR ->
            tr("Google Play can't be reached. Check your phone is online, then try again.")
        BillingResponseCode.ITEM_UNAVAILABLE -> tr("That isn't on sale in Google Play yet. Please try again later.")
        BillingResponseCode.BILLING_UNAVAILABLE -> tr("Google Play can't take payments with this account. Check your payment methods in the Play Store.")
        else -> tr("Google Play couldn't finish the payment (code {0}). Please try again.", result.responseCode)
    }
}
