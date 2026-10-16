package com.calocare.app.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One way to pay for Pro, as Google Play prices it for this user's country. */
data class ProPlan(
    val basePlan: String, // "monthly" or "yearly"
    val price: String, // formatted by Play in the user's currency
    val priceMicros: Long,
    val freeTrialDays: Int,
    internal val offerToken: String,
)

/**
 * CaloCare Pro on Google Play: one subscription ("calocare_pro") with a monthly and a yearly base plan.
 * Play takes the payment; every purchase is then sent to the CaloCare server, which checks it with Google
 * and turns on the larger AI allowance for this device.
 */
class ProBilling(context: Context, private val scope: CoroutineScope, private val onPurchase: suspend (token: String) -> Boolean) {
    var plans by mutableStateOf<List<ProPlan>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    /** A message for the user after a purchase attempt, or null. */
    var note by mutableStateOf<String?>(null)

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.let { handle(it) } else purchaseFailed(result) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private suspend fun connected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { done ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (done.isActive) done.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (done.isActive) done.resume(false)
                }
            })
        }
    }

    /** Loads the prices, and re-sends active purchases so the server knows about renewals and reinstalls. */
    fun refresh() = scope.launch {
        if (!connected()) return@launch
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(SUBSCRIPTION).setProductType(BillingClient.ProductType.SUBS).build()
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build())
        details = result.productDetailsList?.firstOrNull()
        plans = details?.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .mapNotNull { (basePlan, offers) ->
                // A free-trial offer when Play has one for this user, otherwise the plain base plan.
                val offer = offers.maxByOrNull { o -> o.pricingPhases.pricingPhaseList.count { it.priceAmountMicros == 0L } } ?: return@mapNotNull null
                val phases = offer.pricingPhases.pricingPhaseList
                val paid = phases.lastOrNull { it.priceAmountMicros > 0 } ?: return@mapNotNull null
                val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
                ProPlan(basePlan, paid.formattedPrice, paid.priceAmountMicros, trial?.let { days(it.billingPeriod) } ?: 0, offer.offerToken)
            }
            .sortedBy { if (it.basePlan == "yearly") 0 else 1 }
        val owned = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        handle(owned.purchasesList, quiet = true)
    }

    fun buy(activity: Activity, plan: ProPlan) {
        val product = details ?: return
        note = null
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(plan.offerToken).build())
        ).build()
        client.launchBillingFlow(activity, params)
    }

    private fun handle(purchases: List<Purchase>, quiet: Boolean = false) = scope.launch {
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                if (!quiet && purchase.purchaseState == Purchase.PurchaseState.PENDING) note = com.calocare.app.L("Giao dịch đang chờ xử lý. Pro sẽ bật khi thanh toán xong.", "Payment pending. Pro turns on once it completes.")
                continue
            }
            busy = true
            val ok = onPurchase(purchase.purchaseToken)
            // The server acknowledges too; doing it here as well keeps Play from refunding if the server was unreachable.
            if (!purchase.isAcknowledged) {
                runCatching { client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) }
            }
            busy = false
            if (!quiet) note = if (ok) com.calocare.app.L("🎉 Đã bật CaloCare Pro. Cảm ơn bạn!", "🎉 CaloCare Pro is on. Thank you!")
                else com.calocare.app.L("Đã thanh toán, nhưng chưa kết nối được máy chủ. Pro sẽ tự bật khi có mạng.", "Payment done, but the server couldn't be reached. Pro turns on automatically once you're online.")
        }
    }

    private fun purchaseFailed(result: BillingResult) {
        note = when (result.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> null
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { refresh(); com.calocare.app.L("Bạn đã có Pro. Đang khôi phục…", "You already have Pro. Restoring…") }
            else -> com.calocare.app.L("Không mua được (${result.responseCode}). Hãy thử lại.", "The purchase didn't go through (${result.responseCode}). Please try again.")
        }
    }

    companion object {
        const val SUBSCRIPTION = "calocare_pro"

        /** "P7D", "P1W", "P1M" → days. */
        private fun days(period: String): Int {
            val m = Regex("P(\\d+)([DWMY])").matchEntire(period) ?: return 0
            val n = m.groupValues[1].toInt()
            return when (m.groupValues[2]) { "D" -> n; "W" -> n * 7; "M" -> n * 30; else -> n * 365 }
        }
    }
}
