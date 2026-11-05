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
    initial: StoreState = StoreState(status = StoreState.Status.READY, price = "₹50.00", owned = false),
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

    override fun buy(activity: Activity) {
        buys++
        mutable.update {
            if (nextBuyPending) it.copy(pending = true, busy = false)
            else it.copy(owned = true, pending = false, busy = false)
        }
    }

    /** Google Play confirms a pending payment. */
    fun completePending() = mutable.update { it.copy(owned = true, pending = false) }
}
