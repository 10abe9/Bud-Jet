package com.abe.bud_jet.ui.goals

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.entities.GoalEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.R
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
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

class GoalsViewModel(
    private val repository: FinanceRepository,
    private val resources: Resources
) : ViewModel() {

    private val monthRange = currentMonthRange()

    val uiState: StateFlow<GoalsUiState> =
        combine(
            repository.observeSavingGoal(),
            repository.observeCategoryLimitGoals(),
            repository.observeCategories(),
            repository.observeTransactionsInPeriod(monthRange.first, monthRange.second)
        ) { savingGoal, limitGoals, categories, monthTransactions ->
            val spentByCategory = monthTransactions
                .asSequence()
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
            val categoryNames = categories.associateBy({ it.id }, { it.name })

            val totalIncome = monthTransactions
                .asSequence()
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }
            val totalExpense = monthTransactions
                .asSequence()
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            val monthSavingBalance = totalIncome - totalExpense

            val limits = limitGoals.mapNotNull { goal ->
                val categoryId = goal.categoryId ?: return@mapNotNull null
                val spent = spentByCategory[categoryId] ?: 0.0
                val hint = if (goal.targetAmount <= 0.0) {
                    resources.getString(R.string.goals_limit_set_positive)
                } else if (spent <= goal.targetAmount) {
                    val left = goal.targetAmount - spent
                    val leftFormatted = "%.2f".format(left)
                    resources.getString(
                        R.string.goals_on_track_left_this_month,
                        leftFormatted
                    )
                } else {
                    val over = spent - goal.targetAmount
                    val overFormatted = "%.2f".format(over)
                    resources.getString(
                        R.string.goals_at_risk_over_by,
                        overFormatted
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
                savingCard = buildSavingUi(savingGoal, monthSavingBalance),
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

    private fun buildSavingUi(goal: GoalEntity?, currentBalance: Double): SavingGoalCardUi {
        if (goal == null) {
            return SavingGoalCardUi(
                planHint = resources.getString(R.string.goals_create_saving_goal_to_get_started)
            )
        }
        val target = goal.targetAmount
        val current = max(currentBalance, 0.0)
        val progress = if (target > 0) ((current / target) * 100).toInt().coerceIn(0, 100) else 0
        val hint = buildSavingHint(target, current, goal.deadline)
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

    private fun buildSavingHint(target: Double, current: Double, deadline: Long?): String {
        val remaining = (target - current).coerceAtLeast(0.0)
        if (remaining <= 0.0) return resources.getString(R.string.goals_goal_reached_great_work)
        if (deadline == null) {
            val remainingFormatted = "%.2f".format(remaining)
            return resources.getString(R.string.goals_left_to_save, remainingFormatted)
        }

        val now = System.currentTimeMillis()
        val daysLeft = ((deadline - now) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
        val dailyNeed = remaining / daysLeft
        val dailyNeedFormatted = "%.2f".format(dailyNeed)
        return resources.getString(
            R.string.goals_need_per_day,
            dailyNeedFormatted,
            daysLeft
        )
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis to end.timeInMillis
    }
}

class GoalsViewModelFactory(
    private val repository: FinanceRepository,
    private val resources: Resources
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoalsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GoalsViewModel(repository, resources) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}