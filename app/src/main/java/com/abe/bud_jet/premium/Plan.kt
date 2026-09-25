package com.abe.bud_jet.premium

/**
 * Subscription plans. Each plan is its own Google Play subscription product, because a
 * purchase tells the app which product was bought but not which base plan.
 *
 * [PRO] keeps the original "budjet_premium" product id, so people who subscribed before the
 * two plans existed keep everything they paid for.
 */
enum class Plan(val productId: String, val tier: Tier) {
    /** Automatic tracking from notifications, CSV export. No AI. */
    BASIC("bud_jet_base", Tier.BASIC),

    /** Everything in Basic plus the AI assistant. */
    PRO("bud_jet_premium", Tier.PRO);

    companion object {
        fun forProduct(productId: String): Plan? = entries.firstOrNull { it.productId == productId }
    }
}

/** What the user is entitled to. Ordered: a higher tier includes everything below it. */
enum class Tier {
    FREE, BASIC, PRO;

    /** Automatic tracking and export (any paid plan). */
    val hasPaidFeatures: Boolean get() = this != FREE

    val hasAiAssistant: Boolean get() = this == PRO

    companion object {
        /** Highest tier among the products the user has active purchases for. */
        fun fromProducts(productIds: Collection<String>): Tier =
            productIds.mapNotNull { Plan.forProduct(it)?.tier }.maxOrNull() ?: FREE

        fun parse(value: String?): Tier? = entries.firstOrNull { it.name == value }

        /** Debug builds: long-press cycles Free → Basic → Pro → Free. */
        fun next(tier: Tier): Tier = entries[(tier.ordinal + 1) % entries.size]
    }
}
