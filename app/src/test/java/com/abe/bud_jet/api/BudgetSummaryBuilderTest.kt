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
    fun groupsLastThreeMonthsOldestFirst() {
        val summary = BudgetSummaryBuilder.build(
            currency = "USD",
            entries = listOf(
                Entry(at(2026, Calendar.SEPTEMBER, 3), 45.0, false, "Food"),
                Entry(at(2026, Calendar.SEPTEMBER, 5), 20.5, false, "Food"),
                Entry(at(2026, Calendar.SEPTEMBER, 6), 80.0, false, "Transport"),
                Entry(at(2026, Calendar.SEPTEMBER, 1), 3000.0, true, "Salary"),
                Entry(at(2026, Calendar.JULY, 10), 12.0, false, null),
                // Older than three months: not included.
                Entry(at(2026, Calendar.MAY, 10), 999.0, false, "Food")
            ),
            limits = emptyList(),
            savingGoal = null,
            uncategorizedLabel = "Other",
            nowMillis = now
        )
        assertEquals(listOf("2026-07", "2026-08", "2026-09"), summary.months.map { it.month })
        val september = summary.months.last()
        assertEquals(3000.0, september.income, 0.0)
        assertEquals(145.5, september.expense, 0.0)
        assertEquals(listOf("Transport", "Food"), september.expenseByCategory.keys.toList())
        assertEquals(65.5, september.expenseByCategory.getValue("Food"), 0.0)
        assertEquals(mapOf("Other" to 12.0), summary.months.first().expenseByCategory)
        assertEquals(0.0, summary.months[1].expense, 0.0)
    }

    @Test
    fun yearBoundary() {
        val summary = BudgetSummaryBuilder.build("USD", emptyList(), emptyList(), null, "Other",
            nowMillis = at(2027, Calendar.JANUARY, 15))
        assertEquals(listOf("2026-11", "2026-12", "2027-01"), summary.months.map { it.month })
    }
}
