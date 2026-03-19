package com.abe.bud_jet.ui.operations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.database.models.toUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OperationsPeriod {
    TODAY, WEEK, MONTH
}

class OperationsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _period = MutableStateFlow(OperationsPeriod.TODAY)

    val searchQuery: StateFlow<String> = _searchQuery
    val period: StateFlow<OperationsPeriod> = _period

    val transactions: StateFlow<List<Transaction>> =
        combine(
            _period,
            repository.observeRecentTransactions(limit = 200)
        ) { period, list ->
            // На первом шаге просто используем последние операции, фильтрация по периоду может быть доработана позже
            list
        }.combine(_searchQuery) { entities: List<TransactionEntity>, query: String ->
            val uiList = entities.map { it.toUiModel() }
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