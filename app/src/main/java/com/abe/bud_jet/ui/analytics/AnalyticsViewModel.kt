package com.abe.bud_jet.ui.analytics

import android.content.res.Resources
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.models.CategoryStat
import androidx.lifecycle.ViewModel
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.R
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AnalyticsMonthChartUi(
    val label: String,
    val stats: List<CategoryStat>,
    val totalExpense: Float,
    val emptyMessage: String?
)

class AnalyticsViewModel(
    private val repository: FinanceRepository,
    private val resources: Resources
) : ViewModel() {

    private val selectedMonthIndex = MutableStateFlow(0)

    private fun buildMonthOptionsFromTimestamp(firstTimestamp: Long): List<AnalyticsMonthOption> {
        val now = Calendar.getInstance()
        val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        val firstCal = (now.clone() as Calendar).apply {
            timeInMillis = firstTimestamp
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentMonthStart = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val options = mutableListOf<AnalyticsMonthOption>()
        var cursor = firstCal
        while (cursor.timeInMillis <= currentMonthStart) {
            val monthStart = cursor.timeInMillis
            val monthEnd = (cursor.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }.timeInMillis

            options.add(
                AnalyticsMonthOption(
                    label = formatter.format(cursor.time).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                    },
                    startMillis = monthStart,
                    endMillis = monthEnd
                )
            )

            cursor = (cursor.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        }

        return options
    }

    private val monthOptions: StateFlow<List<AnalyticsMonthOption>> =
        repository.observeMinTimestamp()
            .map { minTs ->
                if (minTs == null) emptyList() else buildMonthOptionsFromTimestamp(minTs)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    init {
        // On first load select the current month (last in the generated list).
        viewModelScope.launch {
            val options = monthOptions.filter { it.isNotEmpty() }.first()
            selectedMonthIndex.value = (options.lastIndex).coerceAtLeast(0)
        }
    }

    private val transactionsInRange: StateFlow<List<TransactionEntity>> =
        monthOptions.flatMapLatest { options ->
            if (options.isEmpty()) {
                flowOf(emptyList())
            } else {
                val from = options.first().startMillis
                val to = options.last().endMillis
                repository.observeTransactionsInPeriod(from, to)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val monthCharts: StateFlow<List<AnalyticsMonthChartUi>> =
        combine(
            monthOptions,
            repository.observeCategories(),
            repository.observeTotalExpense(),
            transactionsInRange
        ) { options, categories, totalExpenseEver, allTransactions ->
            options.map { month ->
                val monthExpenses = allTransactions.filterExpensesInPeriod(month)
                val stats = monthExpenses.toCategoryStats(categories)
                val total = stats.sumOf { it.total.toDouble() }.toFloat()

                val emptyMessage = when {
                    totalExpenseEver <= 0.0 -> resources.getString(R.string.analytics_empty_no_expenses)
                    stats.isEmpty() -> resources.getString(
                        R.string.analytics_empty_no_expenses_in_month,
                        month.label
                    )
                    else -> null
                }

                AnalyticsMonthChartUi(
                    label = month.label,
                    stats = stats,
                    totalExpense = total,
                    emptyMessage = emptyMessage
                )
            }
        }
            .catch { throwable ->
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val uiState: StateFlow<AnalyticsUiState> =
        combine(selectedMonthIndex, monthOptions, monthCharts) { index, options, charts ->
            val safeIndex = index.coerceIn(0, (options.lastIndex).coerceAtLeast(0))
            val selectedChart = charts.getOrNull(safeIndex)

            AnalyticsUiState(
                monthOptions = options.map { it.label },
                monthCharts = charts,
                selectedMonthIndex = safeIndex,
                stats = selectedChart?.stats ?: emptyList(),
                totalExpense = selectedChart?.totalExpense ?: 0f,
                emptyMessage = selectedChart?.emptyMessage,
                isLoading = false,
                errorMessage = null
            )
        }.catch { throwable ->
            emit(
                AnalyticsUiState(
                    monthOptions = emptyList(),
                    monthCharts = emptyList(),
                    selectedMonthIndex = 0,
                    stats = emptyList(),
                    totalExpense = 0f,
                    emptyMessage = null,
                    isLoading = false,
                    errorMessage = resources.getString(R.string.analytics_load_error)
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalyticsUiState(isLoading = true)
        )

    fun onMonthSelected(position: Int) {
        val maxIndex = monthOptions.value.lastIndex
        if (position < 0 || position > maxIndex) return
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
                    ?: if (categoryId == -1L) resources.getString(R.string.analytics_uncategorized)
                    else resources.getString(R.string.analytics_other)
                CategoryStat(category = name, total = amount)
            }
            .sortedByDescending { it.total }
    }

}

data class AnalyticsUiState(
    val monthOptions: List<String> = emptyList(),
    val monthCharts: List<AnalyticsMonthChartUi> = emptyList(),
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
    private val repository: FinanceRepository,
    private val resources: Resources
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository, resources) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}