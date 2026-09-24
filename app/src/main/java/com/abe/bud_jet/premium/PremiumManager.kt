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
 * Subscriptions via Google Play Billing: two plans, each its own product (see [Plan]).
 *
 * Play Console setup: subscriptions [Plan.BASIC] and [Plan.PRO] with a monthly auto-renewing
 * base plan each and a free-trial offer (Play shows the trial only to eligible users).
 *
 * The entitlement comes from Google Play's purchase cache and is mirrored into preferences,
 * so the notification service and offline starts see the last known state.
 * For production, verify purchase tokens on a server as well.
 */
object PremiumManager : PurchasesUpdatedListener {

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

    private val _tier = MutableStateFlow(Tier.FREE)
    /** Current plan level; a higher tier includes everything below it. */
    val tier: StateFlow<Tier> = _tier.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    /** Any paid plan: automatic tracking and export. The AI assistant needs [Tier.PRO]. */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _offers = MutableStateFlow<Map<Plan, SubscriptionOffer>>(emptyMap())
    val offers: StateFlow<Map<Plan, SubscriptionOffer>> = _offers.asStateFlow()

    private lateinit var appContext: Context
    private var client: BillingClient? = null
    private var productDetails: Map<Plan, ProductDetails> = emptyMap()
    private var billingTier = Tier.FREE
    /** Active purchase per plan, needed to switch plans (Play replaces the old subscription). */
    private var activePurchases: Map<Plan, Purchase> = emptyMap()

    private val preferences get() = PreferenceManager.getInstance(appContext)

    fun init(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        // Last known state first, so gated screens do not flicker while Play answers.
        billingTier = preferences.getPlanTier()
        publish()
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

    /**
     * Plans on sale. Pro is sold only while the AI backend is configured: selling an assistant
     * that cannot answer would mislead buyers. Existing Pro subscribers keep their tier.
     */
    val availablePlans: List<Plan>
        get() = Plan.entries.filter { it != Plan.PRO || BudJetApi.isConfigured }

    /**
     * Opens Google Play's purchase sheet for [plan]. When the user already has the other plan,
     * Play replaces it: an upgrade charges the prorated difference now, a downgrade gives the
     * unused time as credit. Returns false when the subscription is unavailable.
     */
    fun launchPurchase(activity: Activity, plan: Plan): Boolean {
        val billing = client ?: return false
        val details = productDetails[plan] ?: return false
        val offer = _offers.value[plan] ?: return false
        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
        val current = activePurchases.entries.firstOrNull { it.key != plan }
        if (current != null) {
            val mode = if (plan.tier > current.key.tier) {
                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
            } else {
                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
            }
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(current.value.purchaseToken)
                    .setSubscriptionReplacementMode(mode)
                    .build()
            )
        }
        return billing.launchBillingFlow(activity, builder.build()).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Google Play page where the user manages or cancels the current subscription. */
    fun manageSubscriptionUrl(packageName: String): String {
        val plan = activePurchases.keys.maxByOrNull { it.tier }
            ?: Plan.entries.firstOrNull { it.tier == _tier.value }
        return if (plan == null) {
            "https://play.google.com/store/account/subscriptions?package=$packageName"
        } else {
            "https://play.google.com/store/account/subscriptions?sku=${plan.productId}&package=$packageName"
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases, isFullList = false)
        }
    }

    // ---- Debug builds only ----

    fun isDebugBuild(): Boolean =
        ::appContext.isInitialized && appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Lets a developer test plans without a Play purchase (debuggable builds only):
     * each call moves to the next tier, Free → Basic → Pro → Free. Returns the new tier.
     */
    fun cycleDebugTier(): Tier {
        if (!isDebugBuild()) return _tier.value
        val next = Tier.next(_tier.value)
        // Free with no real purchase = no override needed.
        preferences.setDebugTier(if (next == Tier.FREE && billingTier == Tier.FREE) null else next)
        publish()
        return _tier.value
    }

    private fun debugOverride(): Tier? = if (isDebugBuild()) preferences.getDebugTier() else null

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
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val byPlan = active.flatMap { purchase ->
            purchase.products.mapNotNull { id -> Plan.forProduct(id)?.let { it to purchase } }
        }.toMap()
        byPlan.values.distinct().filterNot { it.isAcknowledged }.forEach { acknowledge(it) }

        // A partial update (new purchase) can only grant; the full list decides revocation.
        activePurchases = if (isFullList) byPlan else activePurchases + byPlan
        val purchasedTier = activePurchases.keys.maxOfOrNull { it.tier } ?: Tier.FREE
        billingTier = if (isFullList) purchasedTier else maxOf(billingTier, purchasedTier)

        val main = activePurchases.entries.maxByOrNull { it.key.tier }
        if (main != null) {
            preferences.setPurchaseToken(main.value.purchaseToken)
            if (main.key in byPlan) reportToServer(main.key, main.value)
        } else if (isFullList) {
            preferences.setPurchaseToken(null)
        }
        publish()
    }

    /**
     * Lets the backend (when configured in gradle.properties) record and verify the purchase
     * with Google. For now the answer is only logged: the entitlement still comes from Play.
     * Once the server is live, a confirmed "active=false" can be used to revoke the plan here.
     */
    private fun reportToServer(plan: Plan, purchase: Purchase) {
        if (!BudJetApi.isConfigured) return
        val caller = BudJetApi.Caller(
            installId = preferences.getInstallId(),
            purchaseToken = purchase.purchaseToken,
            language = preferences.getAppLanguage()
        )
        serverScope.launch {
            val result = BudJetApi.verifySubscription(caller, appContext.packageName, plan.productId, purchase.purchaseToken)
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
        val tier = debugOverride() ?: billingTier
        preferences.setPlanTier(tier)
        _tier.value = tier
        _isPremium.value = tier.hasPaidFeatures
    }

    private fun queryProductDetails(billing: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Plan.entries.map { plan ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        billing.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = detailsResult.productDetailsList.mapNotNull { d ->
                Plan.forProduct(d.productId)?.let { it to d }
            }.toMap()
            productDetails = details
            val offers = details.mapNotNull { (plan, d) -> pickOffer(d)?.let { plan to it } }.toMap()
            _offers.value = offers
            PremiumPricing.playPrices = offers.mapValues { (_, offer) -> offer.monthlyPrice to offer.currencyCode }
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
