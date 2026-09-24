package com.abe.bud_jet.premium

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import com.abe.bud_jet.api.BudJetApi
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Premium subscription via Google Play Billing.
 *
 * Play Console setup: subscription product [PRODUCT_ID] with a monthly auto-renewing base plan
 * and a 30-day free-trial offer (Play shows the trial only to eligible users).
 *
 * The entitlement comes from Google Play's purchase cache and is mirrored into preferences,
 * so the notification service and offline starts see the last known state.
 * For production, verify purchase tokens on a server as well.
 */
object PremiumManager : PurchasesUpdatedListener {

    const val PRODUCT_ID = "budjet_premium"

    /** What the paywall shows: Play-formatted price and trial length for this user. */
    data class SubscriptionOffer(
        val offerToken: String,
        val formattedPrice: String,
        val priceMicros: Long,
        val currencyCode: String,
        /** Free trial length in days, or null when this user is not eligible for a trial. */
        val trialDays: Int?
    ) {
        val monthlyPrice: Double get() = priceMicros / 1_000_000.0
    }

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _offer = MutableStateFlow<SubscriptionOffer?>(null)
    val offer: StateFlow<SubscriptionOffer?> = _offer.asStateFlow()

    private lateinit var appContext: Context
    private var client: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var billingPremium = false

    private val preferences get() = PreferenceManager.getInstance(appContext)

    fun init(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        // Last known state first, so gated screens do not flicker while Play answers.
        _isPremium.value = preferences.isPremiumEnabled()
        billingPremium = _isPremium.value && !debugOverride()
        client = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        refresh()
    }

    /** Re-reads purchases and product details (call on app start and resume). */
    fun refresh() {
        val billing = client ?: return
        if (billing.isReady) {
            queryPurchases(billing)
            queryProductDetails(billing)
            return
        }
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases(billing)
                    queryProductDetails(billing)
                }
            }

            override fun onBillingServiceDisconnected() {
                // The next refresh() reconnects.
            }
        })
    }

    /** Opens Google Play's purchase sheet. Returns false when the subscription is unavailable. */
    fun launchPurchase(activity: Activity): Boolean {
        val billing = client ?: return false
        val details = productDetails ?: return false
        val offer = _offer.value ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            .build()
        return billing.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Google Play page where the user manages or cancels the subscription. */
    fun manageSubscriptionUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=$packageName"

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases, isFullList = false)
        }
    }

    // ---- Debug builds only ----

    fun isDebugBuild(): Boolean =
        ::appContext.isInitialized && appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Lets a developer test Premium features without a Play purchase (debuggable builds only). */
    fun setDebugPremium(enabled: Boolean) {
        if (!isDebugBuild()) return
        preferences.setDebugPremium(enabled)
        publish()
    }

    private fun debugOverride(): Boolean = isDebugBuild() && preferences.isDebugPremium()

    // ---- Internals ----

    private fun queryPurchases(billing: BillingClient) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billing.queryPurchasesAsync(params) { result, purchases ->
            // Keep the cached state on errors (e.g. offline) instead of revoking Premium.
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases, isFullList = true)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, isFullList: Boolean) {
        val active = purchases.filter {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        active.filterNot { it.isAcknowledged }.forEach { acknowledge(it) }
        if (active.isNotEmpty()) {
            preferences.setPurchaseToken(active.first().purchaseToken)
            reportToServer(active.first())
        } else if (isFullList) {
            preferences.setPurchaseToken(null)
        }
        // A partial update (new purchase) can only grant; the full list decides revocation.
        billingPremium = if (isFullList) active.isNotEmpty() else billingPremium || active.isNotEmpty()
        publish()
    }

    /**
     * Lets the backend (when configured in gradle.properties) record and verify the purchase
     * with Google. For now the answer is only logged: the entitlement still comes from Play.
     * Once the server is live, a confirmed "active=false" can be used to revoke Premium here.
     */
    private fun reportToServer(purchase: Purchase) {
        if (!BudJetApi.isConfigured) return
        val caller = BudJetApi.Caller(
            installId = preferences.getInstallId(),
            purchaseToken = purchase.purchaseToken,
            language = preferences.getAppLanguage()
        )
        serverScope.launch {
            val result = BudJetApi.verifySubscription(caller, appContext.packageName, PRODUCT_ID, purchase.purchaseToken)
            android.util.Log.d("BudJetPremium", "Server verification: $result")
        }
    }

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Unacknowledged subscriptions are refunded by Google after three days. */
    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client?.acknowledgePurchase(params) { }
    }

    private fun publish() {
        val premium = billingPremium || debugOverride()
        preferences.setPremiumEnabled(premium)
        _isPremium.value = premium
    }

    private fun queryProductDetails(billing: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        billing.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = detailsResult.productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync
            productDetails = details
            val offer = pickOffer(details)
            _offer.value = offer
            PremiumPricing.playPrice = offer?.let { it.monthlyPrice to it.currencyCode }
        }
    }

    /** Prefers an offer with a free phase (trial); Play lists only offers this user may take. */
    private fun pickOffer(details: ProductDetails): SubscriptionOffer? {
        val offers = details.subscriptionOfferDetails.orEmpty()
        val chosen = offers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        } ?: offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() ?: return null

        val phases = chosen.pricingPhases.pricingPhaseList
        val regular = phases.lastOrNull() ?: return null
        val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
        return SubscriptionOffer(
            offerToken = chosen.offerToken,
            formattedPrice = regular.formattedPrice,
            priceMicros = regular.priceAmountMicros,
            currencyCode = regular.priceCurrencyCode,
            trialDays = trial?.let { BillingPeriod.toDays(it.billingPeriod) }
        )
    }
}
