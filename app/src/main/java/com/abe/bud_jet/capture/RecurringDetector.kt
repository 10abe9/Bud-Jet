package com.abe.bud_jet.capture

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Finds regular payments (subscriptions) in expense history: the same merchant charged at a
 * stable interval (weekly, monthly or yearly), and predicts the next charge.
 */
object RecurringDetector {

    data class Payment(val timestamp: Long, val amount: Double, val merchant: String)

    data class RecurringPayment(
        val merchant: String,
        val lastAmount: Double,
        val previousAmount: Double,
        val lastChargedAt: Long,
        val intervalDays: Int,
        val nextChargeAt: Long,
        val occurrences: Int
    ) {
        /** The latest charge is higher than the previous one (price increase). */
        val priceIncreased: Boolean get() = lastAmount > previousAmount * 1.01
    }

    private const val DAY = 24L * 60 * 60 * 1000

    private data class Cadence(val days: Int, val toleranceDays: Int)

    private val cadences = listOf(
        Cadence(days = 7, toleranceDays = 1),
        Cadence(days = 30, toleranceDays = 4),
        Cadence(days = 365, toleranceDays = 10)
    )

    /** Amounts of one subscription may change (price increase), but not wildly. */
    private const val MAX_AMOUNT_CHANGE = 0.35

    /** Two charges are enough when the amounts are practically identical. */
    private const val SAME_AMOUNT_TOLERANCE = 0.03

    fun detect(payments: List<Payment>, nowMillis: Long = System.currentTimeMillis()): List<RecurringPayment> {
        return payments
            .filter { it.amount > 0.0 && it.merchant.isNotBlank() }
            .groupBy { MerchantCategorizer.merchantKey(it.merchant) }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (_, group) -> detectInGroup(group.sortedBy { it.timestamp }, nowMillis) }
            .sortedBy { it.nextChargeAt }
    }

    private fun detectInGroup(sorted: List<Payment>, nowMillis: Long): RecurringPayment? {
        if (sorted.size < 2) return null
        // Several charges on the same day (e.g. split payments) count as one.
        val charges = sorted.fold(mutableListOf<Payment>()) { acc, payment ->
            val last = acc.lastOrNull()
            if (last != null && payment.timestamp - last.timestamp < DAY) {
                acc[acc.lastIndex] = payment
            } else {
                acc.add(payment)
            }
            acc
        }
        if (charges.size < 2) return null

        for (cadence in cadences) {
            val chain = trailingChain(charges, cadence)
            if (chain.size < 2) continue
            val confirmed = chain.size >= 3 || chain.let {
                val a = it[it.lastIndex - 1].amount
                val b = it.last().amount
                abs(a - b) <= maxOf(a, b) * SAME_AMOUNT_TOLERANCE
            }
            if (!confirmed) continue

            val intervals = chain.zipWithNext { a, b -> b.timestamp - a.timestamp }
            val averageInterval = intervals.average().roundToLong()
            val last = chain.last()
            val next = last.timestamp + averageInterval
            // No charge long after the expected date: probably cancelled.
            if (nowMillis > next + (cadence.toleranceDays + 7) * DAY) return null

            return RecurringPayment(
                merchant = last.merchant,
                lastAmount = last.amount,
                previousAmount = chain[chain.lastIndex - 1].amount,
                lastChargedAt = last.timestamp,
                intervalDays = (averageInterval / DAY).toInt(),
                nextChargeAt = next,
                occurrences = chain.size
            )
        }
        return null
    }

    /** Longest run of charges ending with the latest one whose gaps match the cadence. */
    private fun trailingChain(charges: List<Payment>, cadence: Cadence): List<Payment> {
        val chain = mutableListOf(charges.last())
        for (i in charges.lastIndex - 1 downTo 0) {
            val candidate = charges[i]
            val gapDays = (chain.first().timestamp - candidate.timestamp).toDouble() / DAY
            val amountChange = abs(chain.first().amount - candidate.amount) /
                maxOf(chain.first().amount, candidate.amount)
            if (abs(gapDays - cadence.days) <= cadence.toleranceDays && amountChange <= MAX_AMOUNT_CHANGE) {
                chain.add(0, candidate)
            } else if (gapDays > cadence.days + cadence.toleranceDays) {
                break
            }
            // Charges closer than the cadence (other purchases at the same merchant) are skipped.
        }
        return chain
    }
}
