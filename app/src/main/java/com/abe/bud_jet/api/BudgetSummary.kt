package com.abe.bud_jet.api

import java.util.Calendar
import java.util.Locale
import kotlin.math.round

/**
 * What the AI assistant may send to the server, and nothing more: totals by month and
 * category, limits and the saving goal. No notes, merchants, notification texts or bank data
 * (this is what the consent screen promises).
 */
data class BudgetSummary(
    val currency: String,
    val months: List<MonthSummary>,
    val limits: List<LimitSummary>,
    val savingGoal: SavingGoalSummary?
) {
    data class MonthSummary(
        /** "2026-09" */
        val month: String,
        val income: Double,
        val expense: Double,
        /** Category name -> expense total, largest first. */
        val expenseByCategory: Map<String, Double>
    )

    data class LimitSummary(val category: String, val limit: Double, val spentThisMonth: Double)

    data class SavingGoalSummary(val target: Double, val saved: Double, val deadline: String?)
}

object BudgetSummaryBuilder {

    /** One transaction reduced to what the summary needs. */
    data class Entry(val timestamp: Long, val amount: Double, val isIncome: Boolean, val category: String?)

    const val MONTHS = 3

    fun build(
        currency: String,
        entries: List<Entry>,
        limits: List<BudgetSummary.LimitSummary>,
        savingGoal: BudgetSummary.SavingGoalSummary?,
        uncategorizedLabel: String,
        nowMillis: Long = System.currentTimeMillis()
    ): BudgetSummary {
        val monthKeys = lastMonthKeys(nowMillis)
        val byMonth = entries.groupBy { monthKey(it.timestamp) }
        val months = monthKeys.map { key ->
            val items = byMonth[key].orEmpty()
            val expenses = items.filterNot { it.isIncome }
            BudgetSummary.MonthSummary(
                month = key,
                income = items.filter { it.isIncome }.sumOf { it.amount }.money(),
                expense = expenses.sumOf { it.amount }.money(),
                expenseByCategory = expenses
                    .groupBy { it.category ?: uncategorizedLabel }
                    .mapValues { (_, list) -> list.sumOf { it.amount }.money() }
                    .entries.sortedByDescending { it.value }
                    .associate { it.key to it.value }
            )
        }
        return BudgetSummary(currency, months, limits, savingGoal)
    }

    /** Current month and the previous ones, oldest first. */
    private fun lastMonthKeys(nowMillis: Long): List<String> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return (MONTHS - 1 downTo 0).map { back ->
            val c = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -back)
            }
            String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }
    }

    fun monthKey(timestamp: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private fun Double.money(): Double = round(this * 100) / 100
}
