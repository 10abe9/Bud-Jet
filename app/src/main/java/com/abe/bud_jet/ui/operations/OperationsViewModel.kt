package com.abe.bud_jet.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.database.models.toUiModel
import com.abe.bud_jet.database.models.withCategoryName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class OperationsPeriod {
    WEEK, MONTH, YEAR, CUSTOM
}

data class DateRange(
    val from: Long,
    val to: Long
)

data class OperationsUiState(
    val transactions: List<Transaction> = emptyList(),
    val expensesFormatted: String = "$0.00",
    val periodLabel: String = "Current week",
    val selectedPeriod: OperationsPeriod = OperationsPeriod.WEEK
)

class OperationsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _period = MutableStateFlow(OperationsPeriod.WEEK)
    private val _customRange = MutableStateFlow<DateRange?>(null)

    val searchQuery: StateFlow<String> = _searchQuery
    val period: StateFlow<OperationsPeriod> = _period

    private val activeRange: StateFlow<DateRange> =
        combine(_period, _customRange) { period, custom ->
            when (period) {
                OperationsPeriod.WEEK -> currentWeekRange()
                OperationsPeriod.MONTH -> currentMonthRange()
                OperationsPeriod.YEAR -> currentYearRange()
                OperationsPeriod.CUSTOM -> custom ?: currentMonthRange()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = currentWeekRange()
        )

    private val allTransactionsInPeriod: StateFlow<List<Transaction>> =
        combine(
            activeRange.flatMapLatest { range ->
                repository.observeTransactionsInPeriod(range.from, range.to)
            },
            repository.observeCategories()
        ) { list, categories ->
            val categoryNamesById = categories.associateBy({ it.id }, { it.name })
            list.map { entity ->
                entity.toUiModel().withCategoryName(categoryNamesById[entity.categoryId])
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val filteredTransactions: StateFlow<List<Transaction>> =
        allTransactionsInPeriod.combine(_searchQuery) { uiList: List<Transaction>, query: String ->
            if (query.isBlank()) {
                uiList
            } else {
                uiList.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.date.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val expenseTotal: StateFlow<Double> =
        activeRange
            .flatMapLatest { range -> repository.observeTransactionsInPeriod(range.from, range.to) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
            .let { entitiesFlow ->
                entitiesFlow
                    .combine(_period) { entities, _ ->
                        entities
                            .asSequence()
                            .filter { it.type == TransactionType.EXPENSE }
                            .sumOf { it.amount }
                    }
                    .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
            }

    val uiState: StateFlow<OperationsUiState> =
        combine(filteredTransactions, expenseTotal, _period, activeRange) { tx, expense, period, range ->
            OperationsUiState(
                transactions = tx,
                expensesFormatted = formatMoney(expense),
                periodLabel = buildPeriodLabel(period, range),
                selectedPeriod = period
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = OperationsUiState()
        )

    fun setSearchQuery(query: String) {
        viewModelScope.launch {
            _searchQuery.emit(query)
        }
    }

    fun setPeriod(period: OperationsPeriod) {
        viewModelScope.launch {
            _period.emit(period)
        }
    }

    fun setCustomRange(from: Long, to: Long) {
        viewModelScope.launch {
            _customRange.emit(DateRange(from = from, to = to))
            _period.emit(OperationsPeriod.CUSTOM)
        }
    }

    private fun currentWeekRange(): DateRange {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return DateRange(from = cal.timeInMillis, to = System.currentTimeMillis())
    }

    private fun currentMonthRange(): DateRange {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return DateRange(from = cal.timeInMillis, to = System.currentTimeMillis())
    }

    private fun currentYearRange(): DateRange {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return DateRange(from = cal.timeInMillis, to = System.currentTimeMillis())
    }

    private fun buildPeriodLabel(period: OperationsPeriod, range: DateRange): String {
        return when (period) {
            OperationsPeriod.WEEK -> "Current week"
            OperationsPeriod.MONTH -> "Current month"
            OperationsPeriod.YEAR -> "Current year"
            OperationsPeriod.CUSTOM -> {
                val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                "${formatter.format(Date(range.from))} - ${formatter.format(Date(range.to))}"
            }
        }
    }

    private fun formatMoney(value: Double): String {
        val rounded = String.format("%.2f", kotlin.math.abs(value))
        return "$$rounded"
    }
}

class OperationsViewModelFactory(
    private val repository: FinanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OperationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OperationsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}