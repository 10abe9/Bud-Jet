package com.abe.bud_jet.api

import com.abe.bud_jet.api.BudgetSummaryBuilder.Entry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BudgetSummaryBuilderTest {

    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, 12, 0) }.timeInMillis

    private val now = at(2026, Calendar.SEPTEMBER, 24)

    @Test
    fun groupsLastSixMonthsOldestFirst() {
        val summary = BudgetSummaryBuilder.build(
            currency = "USD",
            entries = listOf(
                Entry(at(2026, Calendar.SEPTEMBER, 3), 45.0, false, "Food"),
                Entry(at(2026, Calendar.SEPTEMBER, 5), 20.5, false, "Food"),
                Entry(at(2026, Calendar.SEPTEMBER, 6), 80.0, false, "Transport"),
                Entry(at(2026, Calendar.SEPTEMBER, 1), 3000.0, true, "Salary"),
                Entry(at(2026, Calendar.SEPTEMBER, 2), 150.0, true, null),
                Entry(at(2026, Calendar.JULY, 10), 12.0, false, null),
                Entry(at(2026, Calendar.APRIL, 10), 7.0, false, "Food"),
                // Older than six months: not included.
                Entry(at(2026, Calendar.MARCH, 10), 999.0, false, "Food")
            ),
            limits = emptyList(),
            savingGoal = null,
            uncategorizedLabel = "Other",
            nowMillis = now
        )
        assertEquals(
            listOf("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09"),
            summary.months.map { it.month }
        )
        assertEquals(7.0, summary.months.first().expense, 0.0)
        val september = summary.months.last()
        assertEquals(3150.0, september.income, 0.0)
        assertEquals(mapOf("Salary" to 3000.0, "Other" to 150.0), september.incomeByCategory)
        assertEquals(listOf("Salary", "Other"), september.incomeByCategory.keys.toList())
        assertEquals(mapOf("Transport" to 1, "Food" to 2), september.expenseCountByCategory)
        assertEquals(145.5, september.expense, 0.0)
        assertEquals(listOf("Transport", "Food"), september.expenseByCategory.keys.toList())
        assertEquals(65.5, september.expenseByCategory.getValue("Food"), 0.0)
        assertEquals(mapOf("Other" to 12.0), summary.months[3].expenseByCategory)
        assertEquals(0.0, summary.months[4].expense, 0.0)
    }

    @Test
    fun yearBoundary() {
        val summary = BudgetSummaryBuilder.build("USD", emptyList(), emptyList(), null, "Other",
            nowMillis = at(2027, Calendar.JANUARY, 15))
        assertEquals(
            listOf("2026-08", "2026-09", "2026-10", "2026-11", "2026-12", "2027-01"),
            summary.months.map { it.month }
        )
    }

    @Test
    fun weeksStartOnMondayOldestFirst() {
        // 2026-09-24 is a Thursday: the current week starts on Monday 2026-09-21.
        val summary = BudgetSummaryBuilder.build(
            currency = "USD",
            entries = listOf(
                Entry(at(2026, Calendar.SEPTEMBER, 21), 10.0, false, "Food"),
                Entry(at(2026, Calendar.SEPTEMBER, 24), 5.0, false, "Food"),
                Entry(at(2026, Calendar.SEPTEMBER, 20), 7.0, false, "Food"), // Sunday: previous week
                Entry(at(2026, Calendar.SEPTEMBER, 22), 999.0, true, "Salary"), // income: not counted
                Entry(at(2026, Calendar.AUGUST, 1), 50.0, false, "Food") // older than six weeks
            ),
            limits = emptyList(),
            savingGoal = null,
            uncategorizedLabel = "Other",
            nowMillis = now
        )
        assertEquals(
            listOf("2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21"),
            summary.weeks.map { it.weekStart }
        )
        assertEquals(15.0, summary.weeks.last().expense, 0.0)
        assertEquals(7.0, summary.weeks[4].expense, 0.0)
        assertEquals(0.0, summary.weeks.first().expense, 0.0)
    }

    @Test
    fun sundayBelongsToWeekStartingTheMondayBefore() {
        assertEquals("2026-09-14", BudgetSummaryBuilder.weekKey(at(2026, Calendar.SEPTEMBER, 20)))
        assertEquals("2026-09-21", BudgetSummaryBuilder.weekKey(at(2026, Calendar.SEPTEMBER, 21)))
        // Across a year boundary: Friday 2027-01-01 -> Monday 2026-12-28.
        assertEquals("2026-12-28", BudgetSummaryBuilder.weekKey(at(2027, Calendar.JANUARY, 1)))
    }
}
