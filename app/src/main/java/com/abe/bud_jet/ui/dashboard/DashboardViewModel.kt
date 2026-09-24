package com.abe.bud_jet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.DashboardSummary
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.utils.CategoryPalette
import com.abe.bud_jet.capture.RecurringDetector
import com.abe.bud_jet.premium.SavingsOffer
import com.abe.bud_jet.premium.SavingsOfferSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.database.models.toUiModel
import com.abe.bud_jet.database.models.withCategoryMeta

data class DashboardUiState(
    val balance: Double,
    val monthDelta: Double,
    val monthDeltaRaw: Double,
    val hasIncomeTransactions: Boolean,
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
    private val repository: FinanceRepository,
    currencyCode: Flow<String>
) : ViewModel() {

    /** Personal savings pitch for the Premium card; null when there is not enough data. */
    val premiumOffer: StateFlow<SavingsOffer?> =
        SavingsOfferSource.observe(repository, currencyCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recurringPayments: StateFlow<List<RecurringDetector.RecurringPayment>> =
        repository.observeRecurringPayments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.observeDashboardSummary(),
            repository.observeRecentTransactions(limit = 3),
            repository.observeCategoriesByType(isIncome = false),
            repository.observeCategoriesByType(isIncome = true),
            repository.observeCurrentMonthTransactions()
        ) { summary: DashboardSummary, recentEntities, expenseCategoriesRaw, incomeCategoriesRaw, monthTransactions ->
            val monthDelta = monthTransactions.sumOf { tx ->
                if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
            }
            val categoriesById = (expenseCategoriesRaw + incomeCategoriesRaw)
                .associateBy { it.id }
            val recent = recentEntities
                .map { it.toUiModel() }
                .map { tx ->
                    val category = tx.categoryId?.let { categoriesById[it] }
                    tx.withCategoryMeta(
                        categoryName = category?.name,
                        categoryColorHex = category?.color
                    )
                }
            val recentChips = recent
                .take(3)
                .map { RecentTransactionChip(id = it.id, amount = it.amount, isIncome = it.isIncome) }
            val expenseCategories = expenseCategoriesRaw
                .map {
                    DashboardCategoryChip(
                        id = it.id,
                        name = it.name,
                        colorHex = CategoryPalette.colorFor(it.id, it.color),
                        isIncome = false
                    )
                }
                .take(FinanceRepository.MAX_EXPENSE_CATEGORIES)
            val incomeCategories = incomeCategoriesRaw
                .map {
                    DashboardCategoryChip(
                        id = it.id,
                        name = it.name,
                        colorHex = CategoryPalette.colorFor(it.id, it.color),
                        isIncome = true
                    )
                }
                .take(FinanceRepository.MAX_INCOME_CATEGORIES)
            DashboardUiState(
                balance = summary.balance,
                monthDelta = monthDelta,
                monthDeltaRaw = monthDelta,
                hasIncomeTransactions = summary.totalIncome > 0.0,
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
                    balance = 0.0,
                    monthDelta = 0.0,
                    monthDeltaRaw = 0.0,
                    hasIncomeTransactions = false,
                    recentTransactions = emptyList(),
                    recentChips = emptyList(),
                    expenseCategories = emptyList(),
                    incomeCategories = emptyList()
                )
            )
}

class DashboardViewModelFactory(
    private val repository: FinanceRepository,
    private val currencyCode: Flow<String>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository, currencyCode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}