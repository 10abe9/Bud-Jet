package com.abe.bud_jet.api

import java.util.Calendar
import java.util.Locale
import kotlin.math.round

/**
 * What the AI assistant may send to the server, and nothing more: totals and operation counts
 * by month, week and category, limits and the saving goal. No individual transactions, notes,
 * merchants, notification texts or bank data (this is what the consent screen promises).
 */
data class BudgetSummary(
    val currency: String,
    val months: List<MonthSummary>,
    /** Expense per week (Monday start) for the last weeks, oldest first; the last is current. */
    val weeks: List<WeekSummary>,
    val limits: List<LimitSummary>,
    val savingGoal: SavingGoalSummary?
) {
    data class MonthSummary(
        /** "2026-09" */
        val month: String,
        val income: Double,
        val expense: Double,
        /** Category name -> expense total, largest first. */
        val expenseByCategory: Map<String, Double>,
        /** Category name -> number of expense operations (same keys as expenseByCategory). */
        val expenseCountByCategory: Map<String, Int>,
        /** Category name -> income total, largest first. */
        val incomeByCategory: Map<String, Double>
    )

    data class WeekSummary(
        /** Monday of the week, "2026-09-21" */
        val weekStart: String,
        val expense: Double
    )

    data class LimitSummary(val category: String, val limit: Double, val spentThisMonth: Double)

    data class SavingGoalSummary(val target: Double, val saved: Double, val deadline: String?)
}

object BudgetSummaryBuilder {

    /** One transaction reduced to what the summary needs. */
    data class Entry(val timestamp: Long, val amount: Double, val isIncome: Boolean, val category: String?)

    const val MONTHS = 6
    const val WEEKS = 6

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
            val incomes = items.filter { it.isIncome }
            val expenseGroups = expenses.groupBy { it.category ?: uncategorizedLabel }
            val expenseTotals = expenseGroups.totalsLargestFirst()
            BudgetSummary.MonthSummary(
                month = key,
                income = incomes.sumOf { it.amount }.money(),
                expense = expenses.sumOf { it.amount }.money(),
                expenseByCategory = expenseTotals,
                expenseCountByCategory = expenseTotals.keys.associateWith { expenseGroups.getValue(it).size },
                incomeByCategory = incomes.groupBy { it.category ?: uncategorizedLabel }.totalsLargestFirst()
            )
        }
        val weekKeys = lastWeekKeys(nowMillis)
        val expenseByWeek = entries.filterNot { it.isIncome }.groupBy { weekKey(it.timestamp) }
        val weeks = weekKeys.map { key ->
            BudgetSummary.WeekSummary(key, expenseByWeek[key].orEmpty().sumOf { it.amount }.money())
        }
        return BudgetSummary(currency, months, weeks, limits, savingGoal)
    }

    private fun Map<String, List<Entry>>.totalsLargestFirst(): Map<String, Double> =
        mapValues { (_, list) -> list.sumOf { it.amount }.money() }
            .entries.sortedByDescending { it.value }
            .associate { it.key to it.value }

    /** Mondays of the current week and the previous ones, oldest first. */
    private fun lastWeekKeys(nowMillis: Long): List<String> {
        val monday = mondayOf(nowMillis)
        return (WEEKS - 1 downTo 0).map { back ->
            val c = (monday.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, -back) }
            dayKey(c)
        }
    }

    fun weekKey(timestamp: Long): String = dayKey(mondayOf(timestamp))

    private fun mondayOf(timestamp: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        // Days back to Monday: Monday = 0 ... Sunday = 6, whatever the locale's first weekday.
        val daysFromMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        add(Calendar.DAY_OF_MONTH, -daysFromMonday)
    }

    private fun dayKey(c: Calendar): String = String.format(
        Locale.US, "%04d-%02d-%02d",
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
    )

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
