package com.abe.bud_jet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.DashboardSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.database.models.toUiModel
import com.abe.bud_jet.database.models.withCategoryName

data class DashboardUiState(
    val balanceFormatted: String,
    val monthDeltaFormatted: String,
    val monthDeltaRaw: Double,
    val recentTransactions: List<Transaction>,
    val recentChips: List<RecentTransactionChip>,
    val expenseCategories: List<DashboardCategoryChip>,
    val incomeCategories: List<DashboardCategoryChip>
)

data class RecentTransactionChip(
    val id: Long,
    val amount: Double,
    val isIncome: Boolean
)

data class DashboardCategoryChip(
    val id: Long,
    val name: String,
    val colorHex: String?,
    val isIncome: Boolean
)

class DashboardViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.observeDashboardSummary(),
            repository.observeRecentTransactions(limit = 3),
            repository.observeCategoriesByType(isIncome = false),
            repository.observeCategoriesByType(isIncome = true)
        ) { summary: DashboardSummary, recentEntities, expenseCategoriesRaw, incomeCategoriesRaw ->
            val balanceText = formatMoney(balance = summary.balance)
            val monthDelta = summary.totalIncome - summary.totalExpense
            val monthDeltaText = formatMoneyDelta(monthDelta)
            val categoryNamesById = (expenseCategoriesRaw + incomeCategoriesRaw)
                .associateBy({ it.id }, { it.name })
            val recent = recentEntities
                .map { it.toUiModel() }
                .map { tx -> tx.withCategoryName(categoryNamesById[tx.categoryId]) }
            val recentChips = recent
                .take(3)
                .map { RecentTransactionChip(id = it.id, amount = it.amount, isIncome = it.isIncome) }
            val expenseCategories = expenseCategoriesRaw
                .map {
                    DashboardCategoryChip(
                        id = it.id,
                        name = it.name,
                        colorHex = it.color,
                        isIncome = false
                    )
                }
                .take(FinanceRepository.MAX_EXPENSE_CATEGORIES)
            val incomeCategories = incomeCategoriesRaw
                .map {
                    DashboardCategoryChip(
                        id = it.id,
                        name = it.name,
                        colorHex = it.color,
                        isIncome = true
                    )
                }
                .take(FinanceRepository.MAX_INCOME_CATEGORIES)
            DashboardUiState(
                balanceFormatted = balanceText,
                monthDeltaFormatted = monthDeltaText,
                monthDeltaRaw = monthDelta,
                recentTransactions = recent,
                recentChips = recentChips,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = DashboardUiState(
                    balanceFormatted = "$0",
                    monthDeltaFormatted = "$0",
                    monthDeltaRaw = 0.0,
                    recentTransactions = emptyList(),
                    recentChips = emptyList(),
                    expenseCategories = emptyList(),
                    incomeCategories = emptyList()
                )
            )

    private fun formatMoney(balance: Double): String {
        val rounded = String.format("%.2f", kotlin.math.abs(balance))
        return if (balance < 0) {
            "- $$rounded"
        } else {
            "$$rounded"
        }
    }

    private fun formatMoneyDelta(value: Double): String {
        val sign = when {
            value > 0 -> "+ "
            value < 0 -> "- "
            else -> ""
        }
        val abs = kotlin.math.abs(value)
        val rounded = String.format("%.2f", abs)
        return "$sign$$rounded"
    }
}

class DashboardViewModelFactory(
    private val repository: FinanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}