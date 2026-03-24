package com.abe.bud_jet.ui.analytics

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.models.CategoryStat
import androidx.lifecycle.ViewModel
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val monthOptions: List<AnalyticsMonthOption> = buildMonthOptions(lastMonths = 12)
    private val selectedMonthIndex = MutableStateFlow(0)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        selectedMonthIndex,
        repository.observeCategories(),
        repository.observeRecentTransactions(limit = 5000),
        repository.observeTotalExpense()
    ) { index, categories, allTransactions, totalExpenseEver ->
        val safeIndex = index.coerceIn(0, monthOptions.lastIndex)
        val month = monthOptions[safeIndex]
        val monthExpenses = allTransactions.filterExpensesInPeriod(month)
        val stats = monthExpenses.toCategoryStats(categories)
        val total = stats.sumOf { it.total.toDouble() }.toFloat()

        val emptyMessage = when {
            totalExpenseEver <= 0.0 -> "No expenses yet. Add your first expense to unlock analytics."
            stats.isEmpty() -> "No expenses in ${month.label}. Try another month."
            else -> null
        }

        AnalyticsUiState(
            monthOptions = monthOptions.map { it.label },
            selectedMonthIndex = safeIndex,
            stats = stats,
            totalExpense = total,
            emptyMessage = emptyMessage,
            isLoading = false,
            errorMessage = null
        )
    }
        .catch { throwable ->
            emit(
                AnalyticsUiState(
                    monthOptions = monthOptions.map { it.label },
                    stats = emptyList(),
                    totalExpense = 0f,
                    isLoading = false,
                    errorMessage = throwable.message ?: "Unable to load analytics"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalyticsUiState(isLoading = true)
        )

    fun onMonthSelected(position: Int) {
        if (position !in monthOptions.indices) return
        selectedMonthIndex.value = position
    }

    private fun List<TransactionEntity>.filterExpensesInPeriod(
        month: AnalyticsMonthOption
    ): List<TransactionEntity> {
        return filter { tx ->
            tx.type == TransactionType.EXPENSE &&
                tx.amount > 0.0 &&
                tx.timestamp in month.startMillis..month.endMillis
        }
    }

    private fun List<TransactionEntity>.toCategoryStats(
        categories: List<com.abe.bud_jet.database.entities.CategoryEntity>
    ): List<CategoryStat> {
        val namesById = categories.associateBy({ it.id }, { it.name })
        return groupBy { it.categoryId ?: -1L }
            .map { (categoryId, items) ->
                val amount = items.sumOf { it.amount }.toFloat()
                val name = namesById[categoryId]
                    ?: if (categoryId == -1L) "Uncategorized" else "Other"
                CategoryStat(category = name, total = amount)
            }
            .sortedByDescending { it.total }
    }

    private fun buildMonthOptions(lastMonths: Int): List<AnalyticsMonthOption> {
        val now = Calendar.getInstance()
        val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return (0 until lastMonths).map { offset ->
            val monthCal = (now.clone() as Calendar).apply {
                add(Calendar.MONTH, -offset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = monthCal.timeInMillis
            val end = (monthCal.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }.timeInMillis

            AnalyticsMonthOption(
                label = formatter.format(monthCal.time).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                },
                startMillis = start,
                endMillis = end
            )
        }
    }
}

data class AnalyticsUiState(
    val monthOptions: List<String> = emptyList(),
    val selectedMonthIndex: Int = 0,
    val stats: List<CategoryStat> = emptyList(),
    val totalExpense: Float = 0f,
    val emptyMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class AnalyticsMonthOption(
    val label: String,
    val startMillis: Long,
    val endMillis: Long
)

class AnalyticsViewModelFactory(
    private val repository: FinanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}