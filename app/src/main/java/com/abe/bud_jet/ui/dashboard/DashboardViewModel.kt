package com.abe.bud_jet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.DashboardSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.combine
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.database.models.toUiModel

data class DashboardUiState(
    val balanceFormatted: String,
    val monthDeltaFormatted: String,
    val monthDeltaRaw: Double,
    val recentTransactions: List<Transaction>
)

class DashboardViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.observeDashboardSummary(),
            repository.observeRecentTransactions(limit = 3)
        ) { summary: DashboardSummary, recentEntities ->
            val balanceText = formatMoney(balance = summary.balance)
            val monthDelta = summary.totalIncome - summary.totalExpense
            val monthDeltaText = formatMoneyDelta(monthDelta)
            val recent = recentEntities.map { it.toUiModel() }
            DashboardUiState(
                balanceFormatted = balanceText,
                monthDeltaFormatted = monthDeltaText,
                monthDeltaRaw = monthDelta,
                recentTransactions = recent
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = DashboardUiState(
                    balanceFormatted = "$0",
                    monthDeltaFormatted = "$0",
                    monthDeltaRaw = 0.0,
                    recentTransactions = emptyList()
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