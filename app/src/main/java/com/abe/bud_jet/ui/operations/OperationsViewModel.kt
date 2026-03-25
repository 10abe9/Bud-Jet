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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class OperationsPeriod {
    WEEK, MONTH, YEAR, CUSTOM
}

enum class OperationsTypeFilter {
    ALL, INCOME, EXPENSE
}

enum class OperationsTotalsMode {
    EXPENSES, INCOME
}

data class DateRange(
    val from: Long,
    val to: Long
)

data class OperationsUiState(
    val transactions: List<Transaction> = emptyList(),
    val totalsFormatted: String = "$0.00",
    val periodLabel: String = "Current week",
    val selectedPeriod: OperationsPeriod = OperationsPeriod.WEEK,
    val totalsMode: OperationsTotalsMode = OperationsTotalsMode.EXPENSES,
    val activeFilterText: String? = null,
    val activeSearchText: String? = null
)

class OperationsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _typeFilter = MutableStateFlow(OperationsTypeFilter.ALL)
    private val _categoryIdFilter = MutableStateFlow<Long?>(null)
    private val _period = MutableStateFlow(OperationsPeriod.WEEK)
    private val _customRange = MutableStateFlow<DateRange?>(null)
    private val _totalsMode = MutableStateFlow(OperationsTotalsMode.EXPENSES)

    val searchQuery: StateFlow<String> = _searchQuery
    val typeFilter: StateFlow<OperationsTypeFilter> = _typeFilter
    val categoryIdFilter: StateFlow<Long?> = _categoryIdFilter
    val period: StateFlow<OperationsPeriod> = _period
    val totalsMode: StateFlow<OperationsTotalsMode> = _totalsMode

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

    private val filteredByTypeAndCategory: StateFlow<List<Transaction>> =
        combine(allTransactionsInPeriod, _typeFilter, _categoryIdFilter) { list, type, categoryId ->
            list.filter { tx ->
                val typeOk = when (type) {
                    OperationsTypeFilter.ALL -> true
                    OperationsTypeFilter.INCOME -> tx.isIncome
                    OperationsTypeFilter.EXPENSE -> !tx.isIncome
                }
                val categoryOk = categoryId?.let { tx.categoryId == it } ?: true
                typeOk && categoryOk
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val filteredTransactions: StateFlow<List<Transaction>> =
        filteredByTypeAndCategory.combine(_searchQuery) { uiList: List<Transaction>, query: String ->
            val q = query.trim()
            if (q.isBlank()) return@combine uiList

            val lower = q.lowercase(Locale.getDefault())
            val maybeAmount = q.toDoubleOrNull()
            uiList.filter { tx ->
                tx.title.contains(lower, ignoreCase = true) ||
                    tx.note.orEmpty().contains(lower, ignoreCase = true) ||
                    tx.date.contains(lower, ignoreCase = true) ||
                    tx.amountFormattedContains(lower, maybeAmount)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val totalsTotal: StateFlow<Double> =
        activeRange
            .flatMapLatest { range -> repository.observeTransactionsInPeriod(range.from, range.to) }
            .combine(_totalsMode) { entities, mode ->
                entities
                    .asSequence()
                    .filter { entity ->
                        when (mode) {
                            OperationsTotalsMode.EXPENSES -> entity.type == TransactionType.EXPENSE
                            OperationsTotalsMode.INCOME -> entity.type == TransactionType.INCOME
                        }
                    }
                    .sumOf { it.amount }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    data class ActiveQuery(
        val activeFilterText: String?,
        val activeSearchText: String?
    )

    private val categoriesById: StateFlow<Map<Long, String>> =
        repository.observeCategories()
            .map { categories -> categories.associateBy({ it.id }, { it.name }) }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyMap()
            )

    private val activeSearchText =
        _searchQuery
            .map { q ->
                val normalized = q.trim()
                if (normalized.isBlank()) null else normalized
            }
            .distinctUntilChanged()

    private val activeFilterText =
        combine(_typeFilter, _categoryIdFilter, categoriesById) { type, categoryId, map ->
            val typeText = when (type) {
                OperationsTypeFilter.ALL -> null
                OperationsTypeFilter.INCOME -> "Income"
                OperationsTypeFilter.EXPENSE -> "Expense"
            }
            val categoryText = categoryId?.let { map[it] }.takeIf { !it.isNullOrBlank() }

            if (typeText == null && categoryText == null) return@combine null
            when {
                typeText != null && categoryText != null -> "$typeText · $categoryText"
                typeText != null -> typeText
                else -> categoryText
            }
        }.distinctUntilChanged()

    private val activeQuery: StateFlow<ActiveQuery> =
        combine(activeFilterText, activeSearchText) { filterText, searchText ->
            ActiveQuery(
                activeFilterText = filterText,
                activeSearchText = searchText
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = ActiveQuery(activeFilterText = null, activeSearchText = null)
        )

    val uiState: StateFlow<OperationsUiState> =
        combine(filteredTransactions, totalsTotal, _period, activeRange, activeQuery) {
                tx,
                total,
                period,
                range,
                query ->
            // totalsMode участвует в вычислении totalsTotal, поэтому UI пересчитается корректно,
            // даже если мы берём текущее значение из StateFlow напрямую.
            OperationsUiState(
                transactions = tx,
                totalsFormatted = formatMoney(total),
                periodLabel = buildPeriodLabel(period, range),
                selectedPeriod = period,
                totalsMode = _totalsMode.value,
                activeFilterText = query.activeFilterText,
                activeSearchText = query.activeSearchText
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

    fun clearSearch() = setSearchQuery("")

    fun setPeriod(period: OperationsPeriod) {
        viewModelScope.launch {
            _period.emit(period)
        }
    }

    fun setTypeFilter(type: OperationsTypeFilter) {
        viewModelScope.launch {
            _typeFilter.emit(type)
            if (type == OperationsTypeFilter.ALL) {
                _categoryIdFilter.emit(null)
            }
        }
    }

    fun setCategoryFilter(categoryId: Long?) {
        viewModelScope.launch {
            _categoryIdFilter.emit(categoryId)
            // If category is picked, type should no longer be ALL.
            if (categoryId != null && _typeFilter.value == OperationsTypeFilter.ALL) {
                // We don't know income/expense from id alone here,
                // the UI will set it, but keep safe: just keep ALL and filter by categoryId.
            }
        }
    }

    fun clearFilters() {
        viewModelScope.launch {
            _typeFilter.emit(OperationsTypeFilter.ALL)
            _categoryIdFilter.emit(null)
        }
    }

    fun setCustomRange(from: Long, to: Long) {
        viewModelScope.launch {
            _customRange.emit(DateRange(from = from, to = to))
            _period.emit(OperationsPeriod.CUSTOM)
        }
    }

    fun setTotalsMode(mode: OperationsTotalsMode) {
        viewModelScope.launch {
            _totalsMode.emit(mode)
        }
    }

    private fun currentWeekRange(): DateRange {
        val startCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 7)
            add(Calendar.MILLISECOND, -1)
        }
        return DateRange(from = startCal.timeInMillis, to = endCal.timeInMillis)
    }

    private fun currentMonthRange(): DateRange {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return DateRange(from = startCal.timeInMillis, to = endCal.timeInMillis)
    }

    private fun currentYearRange(): DateRange {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.YEAR, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return DateRange(from = startCal.timeInMillis, to = endCal.timeInMillis)
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

private fun Transaction.amountFormattedContains(
    lowerQuery: String,
    maybeAmount: Double?
): Boolean {
    if (maybeAmount != null && kotlin.math.abs(maybeAmount - this.amount) < 0.0001) return true
    val formatted = String.format("%.2f", kotlin.math.abs(this.amount)).lowercase(Locale.getDefault())
    return formatted.contains(lowerQuery)
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