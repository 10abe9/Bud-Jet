package com.abe.bud_jet.premium

import kotlin.math.floor

/**
 * Monthly Premium price in the app currency for the savings pitch.
 *
 * The real price comes from Google Play (PremiumManager.offer) and is used when Play's currency
 * matches the app currency; this table is only a fallback (no Play connection, other currency).
 * Keep it close to the prices set in Play Console.
 */
object PremiumPricing {

    private val monthlyByCurrency = mapOf(
        "USD" to 9.99,
        "EUR" to 9.99,
        "PLN" to 39.99,
        "RUB" to 899.0,
        "KZT" to 4990.0,
        "INR" to 799.0,
        "BRL" to 49.90,
        "MXN" to 179.0
    )

    /** Price reported by Google Play: amount and ISO currency code. */
    @Volatile
    var playPrice: Pair<Double, String>? = null

    fun monthlyPrice(currencyCode: String): Double {
        playPrice?.let { (amount, currency) ->
            if (currency.equals(currencyCode, ignoreCase = true) && amount > 0.0) return amount
        }
        return monthlyByCurrency[currencyCode.uppercase()] ?: monthlyByCurrency.getValue("USD")
    }
}

/** What the user could save per month compared to the Premium price. */
data class SavingsOffer(
    val monthlySpend: Double,
    val monthlySavings: Double,
    val monthlyPrice: Double,
    /** How many times the savings cover the price (rounded down). */
    val paybackMultiple: Int
) {
    val netBenefit: Double get() = monthlySavings - monthlyPrice
}

object SavingsOfferCalculator {

    /** Conservative cut the pitch talks about ("just 5% less"). */
    const val SAVINGS_RATE = 0.05

    /** Less history than this gives an unreliable monthly estimate. */
    const val MIN_HISTORY_DAYS = 14
    const val MIN_EXPENSE_COUNT = 10

    /** Only pitch when the savings clearly exceed the price. */
    const val MIN_PAYBACK_MULTIPLE = 2

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val WINDOW_DAYS = 30

    /**
     * @param expenses expense transactions as (timestamp, amount)
     * @param firstTransactionAt timestamp of the very first recorded transaction
     * @return null when there is not enough data or the offer would not be worth it
     */
    fun calculate(
        expenses: List<Pair<Long, Double>>,
        firstTransactionAt: Long?,
        monthlyPrice: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): SavingsOffer? {
        if (firstTransactionAt == null || monthlyPrice <= 0.0) return null
        val historyDays = ((nowMillis - firstTransactionAt) / DAY_MILLIS).toInt()
        if (historyDays < MIN_HISTORY_DAYS) return null

        val windowStart = nowMillis - WINDOW_DAYS * DAY_MILLIS
        val recent = expenses.filter { (timestamp, amount) -> timestamp in windowStart..nowMillis && amount > 0.0 }
        if (recent.size < MIN_EXPENSE_COUNT) return null

        // With less than 30 days of history, extrapolate what was recorded to a month.
        val coveredDays = historyDays.coerceAtMost(WINDOW_DAYS)
        val monthlySpend = recent.sumOf { it.second } * WINDOW_DAYS / coveredDays
        val savings = monthlySpend * SAVINGS_RATE
        val multiple = floor(savings / monthlyPrice).toInt()
        if (multiple < MIN_PAYBACK_MULTIPLE) return null

        return SavingsOffer(
            monthlySpend = monthlySpend,
            monthlySavings = savings,
            monthlyPrice = monthlyPrice,
            paybackMultiple = multiple
        )
    }
}

/** Decides when the dashboard promo may appear, so it stays unobtrusive. */
object PremiumPromoPolicy {

    const val MAX_DISMISSALS = 3
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun shouldShow(
        isPremium: Boolean,
        hasOffer: Boolean,
        dismissCount: Int,
        snoozedUntil: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (isPremium || !hasOffer) return false
        if (dismissCount >= MAX_DISMISSALS) return false
        return nowMillis >= snoozedUntil
    }

    /** Each "Not now" hides the card longer: 14, then 30 days; the third one hides it for good. */
    fun snoozeUntil(dismissCountAfter: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val days = if (dismissCountAfter <= 1) 14 else 30
        return nowMillis + days * DAY_MILLIS
    }
}

/** Parses ISO-8601 billing periods used by Play ("P1M", "P30D", "P1W", "P1Y"). */
object BillingPeriod {
    fun toDays(period: String): Int? {
        val match = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""").matchEntire(period) ?: return null
        val (years, months, weeks, days) = match.destructured
        val total = (years.toIntOrNull() ?: 0) * 365 + (months.toIntOrNull() ?: 0) * 30 +
            (weeks.toIntOrNull() ?: 0) * 7 + (days.toIntOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }
}
