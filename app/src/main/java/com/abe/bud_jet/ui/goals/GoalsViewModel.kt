package com.abe.bud_jet.ui.goals

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.entities.GoalEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.R
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.utils.DateRanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

data class SavingGoalCardUi(
    val goalId: Long? = null,
    val hasGoal: Boolean = false,
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val progressPercent: Int = 0,
    val planHint: String = "",
    val deadline: Long? = null
)

data class CategoryLimitUi(
    val goalId: Long,
    val categoryId: Long,
    val categoryName: String,
    val spent: Double,
    val limit: Double,
    val hint: String
)

data class GoalsUiState(
    val isLoading: Boolean = false,
    val savingCard: SavingGoalCardUi = SavingGoalCardUi(),
    val limits: List<CategoryLimitUi> = emptyList(),
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModel(
    private val repository: FinanceRepository,
    private val resources: Resources,
    currencyCode: Flow<String>
) : ViewModel() {

    private data class SavingProgress(val goal: GoalEntity, val saved: Double)

    /** Saving goals accumulate income minus expenses since the goal was created. */
    private val savingProgress: Flow<SavingProgress?> =
        repository.observeSavingGoal().flatMapLatest { goal ->
            if (goal == null) {
                flowOf(null)
            } else {
                repository.observeNetSince(goal.createdAt).map { net -> SavingProgress(goal, net) }
            }
        }

    val uiState: StateFlow<GoalsUiState> =
        combine(
            savingProgress,
            repository.observeCategoryLimitGoals(),
            repository.observeCategories(),
            repository.observeCurrentMonthTransactions(),
            currencyCode
        ) { saving, limitGoals, categories, monthTransactions, currency ->
            val spentByCategory = monthTransactions
                .asSequence()
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
            val categoryNames = categories.associateBy({ it.id }, { it.name })

            val limits = limitGoals.mapNotNull { goal ->
                val categoryId = goal.categoryId ?: return@mapNotNull null
                val spent = spentByCategory[categoryId] ?: 0.0
                val hint = if (goal.targetAmount <= 0.0) {
                    resources.getString(R.string.goals_limit_set_positive)
                } else if (spent <= goal.targetAmount) {
                    resources.getString(
                        R.string.goals_on_track_left_this_month,
                        CurrencyFormatter.format(goal.targetAmount - spent, currency)
                    )
                } else {
                    resources.getString(
                        R.string.goals_at_risk_over_by,
                        CurrencyFormatter.format(spent - goal.targetAmount, currency)
                    )
                }
                CategoryLimitUi(
                    goalId = goal.id,
                    categoryId = categoryId,
                    categoryName = categoryNames[categoryId]
                        ?: resources.getString(
                            R.string.goals_category_placeholder,
                            categoryId.toString()
                        ),
                    spent = spent,
                    limit = goal.targetAmount,
                    hint = hint
                )
            }

            GoalsUiState(
                isLoading = false,
                savingCard = buildSavingUi(saving, currency),
                limits = limits
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = GoalsUiState(isLoading = true)
        )

    fun saveSavingGoal(targetAmount: Double, deadline: Long?) {
        viewModelScope.launch {
            repository.upsertSavingGoal(targetAmount = targetAmount, deadline = deadline)
        }
    }

    fun saveCategoryLimit(categoryId: Long, limitAmount: Double) {
        viewModelScope.launch {
            repository.upsertCategoryLimitGoal(categoryId = categoryId, limitAmount = limitAmount)
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            repository.deleteGoal(goalId)
        }
    }

    private fun buildSavingUi(saving: SavingProgress?, currency: String): SavingGoalCardUi {
        if (saving == null) {
            return SavingGoalCardUi(
                planHint = resources.getString(R.string.goals_create_saving_goal_to_get_started)
            )
        }
        val goal = saving.goal
        val target = goal.targetAmount
        val current = max(saving.saved, 0.0)
        val progress = if (target > 0) ((current / target) * 100).toInt().coerceIn(0, 100) else 0
        val hint = buildSavingHint(target, current, goal.deadline, currency)
        return SavingGoalCardUi(
            goalId = goal.id,
            hasGoal = true,
            targetAmount = target,
            currentAmount = current,
            progressPercent = progress,
            planHint = hint,
            deadline = goal.deadline
        )
    }

    private fun buildSavingHint(target: Double, current: Double, deadline: Long?, currency: String): String {
        val remaining = (target - current).coerceAtLeast(0.0)
        if (remaining <= 0.0) return resources.getString(R.string.goals_goal_reached_great_work)
        val remainingFormatted = CurrencyFormatter.format(remaining, currency)
        if (deadline == null) {
            return resources.getString(R.string.goals_left_to_save, remainingFormatted)
        }

        val dayMillis = 24 * 60 * 60 * 1000L
        // The deadline day itself still counts as a day to save.
        val daysLeft = ((deadline - DateRanges.startOfDay()) / dayMillis).toInt() + 1
        if (daysLeft <= 0) {
            return resources.getString(R.string.goals_deadline_passed, remainingFormatted)
        }
        return resources.getString(
            R.string.goals_need_per_day,
            CurrencyFormatter.format(remaining / daysLeft, currency),
            daysLeft
        )
    }
}

class GoalsViewModelFactory(
    private val repository: FinanceRepository,
    private val resources: Resources,
    private val currencyCode: Flow<String>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoalsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GoalsViewModel(repository, resources, currencyCode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}