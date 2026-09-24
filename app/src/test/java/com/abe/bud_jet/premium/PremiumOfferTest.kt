package com.abe.bud_jet.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumOfferTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun expenses(count: Int, amount: Double, daysBack: Int = 20) =
        (0 until count).map { i -> (now - (i % daysBack) * day) to amount }

    @Test
    fun computesFivePercentOfMonthlySpend() {
        val offer = SavingsOfferCalculator.calculate(
            expenses = expenses(count = 20, amount = 2250.0),
            firstTransactionAt = now - 60 * day,
            monthlyPrice = 249.0,
            nowMillis = now
        )
        assertNotNull(offer)
        assertEquals(45_000.0, offer!!.monthlySpend, 0.001)
        assertEquals(2_250.0, offer.monthlySavings, 0.001)
        assertEquals(9, offer.paybackMultiple)
        assertEquals(2_001.0, offer.netBenefit, 0.001)
    }

    @Test
    fun extrapolatesShortHistoryToAMonth() {
        val offer = SavingsOfferCalculator.calculate(
            expenses = expenses(count = 15, amount = 1000.0, daysBack = 15),
            firstTransactionAt = now - 15 * day,
            monthlyPrice = 249.0,
            nowMillis = now
        )
        assertEquals(30_000.0, offer!!.monthlySpend, 0.001)
    }

    @Test
    fun ignoresExpensesOlderThanThirtyDays() {
        val old = (0 until 20).map { (now - 45 * day) to 10_000.0 }
        val offer = SavingsOfferCalculator.calculate(
            expenses = old + expenses(count = 10, amount = 1000.0),
            firstTransactionAt = now - 90 * day,
            monthlyPrice = 249.0,
            nowMillis = now
        )
        assertEquals(10_000.0, offer!!.monthlySpend, 0.001)
    }

    @Test
    fun noOfferWithoutEnoughData() {
        assertNull(SavingsOfferCalculator.calculate(expenses(20, 5000.0), null, 249.0, now))
        assertNull(SavingsOfferCalculator.calculate(expenses(20, 5000.0), now - 5 * day, 249.0, now))
        assertNull(SavingsOfferCalculator.calculate(expenses(5, 5000.0), now - 60 * day, 249.0, now))
    }

    @Test
    fun noOfferWhenSavingsDoNotCoverPrice() {
        // 10 x 50 = 500/month -> 25 saved, less than twice the price.
        assertNull(SavingsOfferCalculator.calculate(expenses(10, 50.0), now - 60 * day, 249.0, now))
    }

    @Test
    fun pricingFallsBackToUsd() {
        PremiumPricing.playPrices = emptyMap()
        assertEquals(899.0, PremiumPricing.monthlyPrice("rub", Plan.PRO), 0.0)
        assertEquals(9.99, PremiumPricing.monthlyPrice("XYZ", Plan.PRO), 0.0)
        assertEquals(249.0, PremiumPricing.monthlyPrice("RUB", Plan.BASIC), 0.0)
        assertEquals(2.99, PremiumPricing.monthlyPrice("XYZ", Plan.BASIC), 0.0)
    }

    @Test
    fun playPriceWinsWhenCurrencyMatches() {
        PremiumPricing.playPrices = mapOf(Plan.PRO to (9.49 to "EUR"))
        try {
            assertEquals(9.49, PremiumPricing.monthlyPrice("EUR", Plan.PRO), 0.0)
            assertEquals(899.0, PremiumPricing.monthlyPrice("RUB", Plan.PRO), 0.0)
            // Another plan's Play price is never used.
            assertEquals(2.99, PremiumPricing.monthlyPrice("EUR", Plan.BASIC), 0.0)
        } finally {
            PremiumPricing.playPrices = emptyMap()
        }
    }

    @Test
    fun promoPolicy() {
        assertTrue(PremiumPromoPolicy.shouldShow(false, true, 0, 0L, now))
        assertFalse(PremiumPromoPolicy.shouldShow(true, true, 0, 0L, now))
        assertFalse(PremiumPromoPolicy.shouldShow(false, false, 0, 0L, now))
        assertFalse(PremiumPromoPolicy.shouldShow(false, true, 0, now + day, now))
        assertFalse(PremiumPromoPolicy.shouldShow(false, true, PremiumPromoPolicy.MAX_DISMISSALS, 0L, now))
        assertEquals(now + 14 * day, PremiumPromoPolicy.snoozeUntil(1, now))
        assertEquals(now + 30 * day, PremiumPromoPolicy.snoozeUntil(2, now))
    }
}
