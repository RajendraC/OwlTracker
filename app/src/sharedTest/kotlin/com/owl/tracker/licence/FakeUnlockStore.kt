package com.owl.tracker.licence

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stands in for Google Play in tests. Buying succeeds at once unless [nextBuyPending] is set,
 * which leaves the purchase waiting the way a slow UPI payment does.
 */
class FakeUnlockStore(
    initial: StoreState = StoreState(status = StoreState.Status.READY, price = "₹99.00", lifetimePrice = "₹999.00", owned = false, lifetime = false),
) : UnlockStore {
    val mutable = MutableStateFlow(initial)
    override val state: StateFlow<StoreState> = mutable.asStateFlow()

    var buys = 0
        private set
    var refreshes = 0
        private set
    var nextBuyPending = false

    override fun refresh() {
        refreshes++
    }

    var lastPlan: UnlockStore.Plan? = null
        private set

    override fun buy(activity: Activity, plan: UnlockStore.Plan) {
        buys++
        lastPlan = plan
        mutable.update {
            if (nextBuyPending) it.copy(pending = true, busy = false)
            else it.copy(owned = true, lifetime = if (plan == UnlockStore.Plan.LIFETIME) true else it.lifetime, pending = false, busy = false)
        }
    }

    /** Google Play confirms a pending payment. */
    fun completePending() = mutable.update { it.copy(owned = true, pending = false) }
}
