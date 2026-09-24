package com.abe.bud_jet.premium

import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.entities.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Builds the savings offer from the user's last 30 days of expenses. The pitch compares savings
 * with the cheapest plan: automatic tracking alone is what makes spending visible.
 */
object SavingsOfferSource {

    val PITCH_PLAN = Plan.BASIC

    private const val WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun observe(repository: FinanceRepository, currencyCode: Flow<String>): Flow<SavingsOffer?> {
        val now = System.currentTimeMillis()
        return combine(
            repository.observeTransactionsInPeriod(now - WINDOW_MILLIS, Long.MAX_VALUE),
            repository.observeMinTimestamp(),
            currencyCode
        ) { transactions, firstTimestamp, currency ->
            SavingsOfferCalculator.calculate(
                expenses = transactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .map { it.timestamp to it.amount },
                firstTransactionAt = firstTimestamp,
                monthlyPrice = PremiumPricing.monthlyPrice(currency, PITCH_PLAN)
            )
        }
    }
}
