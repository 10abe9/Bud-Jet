package com.abe.bud_jet.capture

import com.abe.bud_jet.capture.RecurringDetector.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringDetectorTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_800_000_000_000L

    @Test
    fun detectsMonthlySubscriptionAndPredictsNextCharge() {
        val payments = listOf(
            Payment(start, 299.0, "NETFLIX.COM"),
            Payment(start + 30 * day, 299.0, "Netflix.com"),
            Payment(start + 61 * day, 299.0, "NETFLIX.COM")
        )
        val result = RecurringDetector.detect(payments, nowMillis = start + 62 * day)
        assertEquals(1, result.size)
        val sub = result.single()
        assertEquals(30, sub.intervalDays)
        assertEquals(start + 61 * day + (61 * day / 2), sub.nextChargeAt)
        assertEquals(3, sub.occurrences)
        assertFalse(sub.priceIncreased)
    }

    @Test
    fun twoIdenticalMonthlyChargesAreEnough() {
        val payments = listOf(
            Payment(start, 9.99, "Spotify"),
            Payment(start + 31 * day, 9.99, "Spotify")
        )
        assertEquals(1, RecurringDetector.detect(payments, start + 32 * day).size)
    }

    @Test
    fun twoDifferentChargesAreNotEnough() {
        val payments = listOf(
            Payment(start, 1200.0, "Pyaterochka"),
            Payment(start + 30 * day, 870.0, "Pyaterochka")
        )
        assertTrue(RecurringDetector.detect(payments, start + 31 * day).isEmpty())
    }

    @Test
    fun detectsPriceIncrease() {
        val payments = listOf(
            Payment(start, 299.0, "Yandex Plus"),
            Payment(start + 30 * day, 299.0, "Yandex Plus"),
            Payment(start + 60 * day, 349.0, "Yandex Plus")
        )
        val sub = RecurringDetector.detect(payments, start + 61 * day).single()
        assertTrue(sub.priceIncreased)
        assertEquals(299.0, sub.previousAmount, 0.0)
    }

    @Test
    fun irregularShoppingIsNotRecurring() {
        val payments = listOf(0, 3, 11, 12, 25, 40, 44).map { Payment(start + it * day, 500.0 + it, "Lidl") }
        assertTrue(RecurringDetector.detect(payments, start + 45 * day).isEmpty())
    }

    @Test
    fun otherPurchasesAtSameMerchantDoNotBreakTheChain() {
        val payments = listOf(
            Payment(start, 199.0, "Apple.com/bill"),
            Payment(start + 12 * day, 59.0, "Apple.com/bill"),
            Payment(start + 30 * day, 199.0, "Apple.com/bill"),
            Payment(start + 60 * day, 199.0, "Apple.com/bill")
        )
        val sub = RecurringDetector.detect(payments, start + 61 * day).single()
        assertEquals(199.0, sub.lastAmount, 0.0)
    }

    @Test
    fun cancelledSubscriptionDisappears() {
        val payments = listOf(
            Payment(start, 299.0, "Netflix"),
            Payment(start + 30 * day, 299.0, "Netflix"),
            Payment(start + 60 * day, 299.0, "Netflix")
        )
        assertTrue(RecurringDetector.detect(payments, start + 120 * day).isEmpty())
    }
}
