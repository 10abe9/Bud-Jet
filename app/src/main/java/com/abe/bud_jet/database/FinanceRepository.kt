package com.abe.bud_jet.database

import com.abe.bud_jet.database.dao.CategoryDao
import com.abe.bud_jet.database.dao.GoalsDao
import com.abe.bud_jet.database.dao.TransactionsDao
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.database.entities.GoalEntity
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.database.models.CategoryStat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import kotlinx.coroutines.withContext

/**
 * Financial data access. Empty-state onboarding visibility in the UI is derived from
 * flows here (e.g. recent transactions, total expenses) rather than separate prefs,
 * so hints disappear as soon as the user records relevant data.
 */
class FinanceRepository(
    private val transactionsDao: TransactionsDao,
    private val categoryDao: CategoryDao,
    private val goalsDao: GoalsDao,
    private val defaultExpenseCategories: List<String>,
    private val defaultIncomeCategories: List<String>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val MAX_CATEGORY_NAME_LENGTH = 18
        const val MAX_EXPENSE_CATEGORIES = 5
        const val MAX_INCOME_CATEGORIES = 3
        // English fallback set to keep existing DB entries (from older installs) intact.
        // Localization happens via injected defaults (see constructor args).
        private val DEFAULT_EXPENSE_CATEGORIES = listOf("Food", "Health", "Transport")
        private val DEFAULT_INCOME_CATEGORIES = listOf("Salary", "Gift", "Freelance")
        private val CATEGORY_COLOR_PALETTE = listOf(
            "#F59E0B",
            "#3B82F6",
            "#10B981",
            "#8B5CF6",
            "#EF4444",
            "#06B6D4",
            "#F97316",
            "#84CC16",
            "#EC4899",
            "#6366F1"
        )
    }

    enum class AddCategoryResult {
        SUCCESS,
        EMPTY_NAME,
        DUPLICATE,
        LIMIT_REACHED
    }

    enum class DeleteCategoryResult {
        SUCCESS,
        NOT_FOUND
    }

    fun observeRecentTransactions(limit: Int): Flow<List<TransactionEntity>> =
        transactionsDao.observeRecent(limit)

    fun observeTransactionsInPeriod(from: Long, to: Long): Flow<List<TransactionEntity>> =
        transactionsDao.observeInPeriod(from, to)

    fun observeTotalIncome(): Flow<Double> =
        transactionsDao.observeTotalByType(TransactionType.INCOME)

    fun observeTotalExpense(): Flow<Double> =
        transactionsDao.observeTotalByType(TransactionType.EXPENSE)

    fun observeMinTimestamp(): Flow<Long?> =
        transactionsDao.observeMinTimestamp()

    fun observeCategories(): Flow<List<CategoryEntity>> =
        categoryDao.observeAll()

    fun observeCategoriesByType(isIncome: Boolean): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(isIncome)

    fun observeGoals(): Flow<List<GoalEntity>> =
        goalsDao.observeAll()

    fun observeSavingGoal(): Flow<GoalEntity?> =
        goalsDao.observeSavingGoal()

    fun observeCategoryLimitGoals(): Flow<List<GoalEntity>> =
        goalsDao.observeCategoryLimits()

    fun observeExpenseCategoryStats(limit: Int = 5): Flow<List<CategoryStat>> =
        combine(
            transactionsDao.observeRecent(limit = 1000),
            categoryDao.observeAll()
        ) { transactions, categories ->
            val categoryNames = categories.associateBy({ it.id }, { it.name })

            transactions
                .asSequence()
                .filter { it.type == TransactionType.EXPENSE && it.amount > 0.0 }
                .groupBy { tx -> tx.categoryId ?: -1L }
                .map { (categoryId, items) ->
                    val total = items.sumOf { it.amount }.toFloat()
                    val categoryName = categoryNames[categoryId]
                        ?: if (categoryId == -1L) "Uncategorized" else "Other"
                    CategoryStat(category = categoryName, total = total)
                }
                .sortedByDescending { it.total }
                .take(limit)
        }

    suspend fun addCustomCategory(name: String, isIncome: Boolean): AddCategoryResult = withContext(ioDispatcher) {
        val normalized = name
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_CATEGORY_NAME_LENGTH)
        if (normalized.isEmpty()) return@withContext AddCategoryResult.EMPTY_NAME

        val existing = categoryDao.getByNameAndType(normalized, isIncome)
        if (existing != null) return@withContext AddCategoryResult.DUPLICATE

        val countByType = categoryDao.getCountByType(isIncome)
        val maxAllowed = if (isIncome) MAX_INCOME_CATEGORIES else MAX_EXPENSE_CATEGORIES
        if (countByType >= maxAllowed) return@withContext AddCategoryResult.LIMIT_REACHED

        val entity = CategoryEntity(
            name = normalized,
            color = pickDistinctCategoryColor(),
            isDefault = false,
            isIncome = isIncome,
            isCustom = true
        )
        categoryDao.insert(entity)
        AddCategoryResult.SUCCESS
    }

    suspend fun deleteCategory(categoryId: Long): DeleteCategoryResult = withContext(ioDispatcher) {
        val deleted = categoryDao.deleteById(categoryId)
        if (deleted <= 0) return@withContext DeleteCategoryResult.NOT_FOUND
        transactionsDao.clearCategoryReferences(categoryId)
        DeleteCategoryResult.SUCCESS
    }

    fun observeDashboardSummary(): Flow<DashboardSummary> =
        combine(observeTotalIncome(), observeTotalExpense()) { income, expense ->
            DashboardSummary(
                balance = income - expense,
                totalIncome = income,
                totalExpense = expense
            )
        }

    suspend fun addIncome(
        amount: Double,
        categoryId: Long?,
        note: String?,
        timestamp: Long
    ) = addTransactionInternal(
        amount = amount,
        type = TransactionType.INCOME,
        categoryId = categoryId,
        note = note,
        timestamp = timestamp
    )

    suspend fun addExpense(
        amount: Double,
        categoryId: Long?,
        note: String?,
        timestamp: Long
    ) = addTransactionInternal(
        amount = amount,
        type = TransactionType.EXPENSE,
        categoryId = categoryId,
        note = note,
        timestamp = timestamp
    )

    suspend fun updateTransaction(
        id: Long,
        amount: Double,
        isIncome: Boolean,
        categoryId: Long?,
        note: String?,
        timestamp: Long
    ) = withContext(ioDispatcher) {
        val entity = TransactionEntity(
            id = id,
            amount = amount,
            type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
            categoryId = categoryId,
            note = note,
            timestamp = timestamp
        )
        transactionsDao.update(entity)
    }

    suspend fun deleteTransaction(id: Long): Boolean = withContext(ioDispatcher) {
        transactionsDao.deleteById(id) > 0
    }

    suspend fun getTransactionsCount(): Int = withContext(ioDispatcher) {
        transactionsDao.getCount()
    }

    suspend fun convertAllTransactions(rate: Double) = withContext(ioDispatcher) {
        transactionsDao.multiplyAllAmounts(rate)
    }

    suspend fun createBackupSnapshot(): BackupSnapshot = withContext(ioDispatcher) {
        BackupSnapshot(
            transactions = transactionsDao.getAllNow(),
            categories = categoryDao.getAllNow(),
            goals = goalsDao.getAllNow()
        )
    }

    suspend fun restoreBackupSnapshot(snapshot: BackupSnapshot) = withContext(ioDispatcher) {
        transactionsDao.deleteAll()
        goalsDao.deleteAll()
        categoryDao.deleteAll()

        if (snapshot.categories.isNotEmpty()) {
            categoryDao.insertAll(snapshot.categories)
        }
        if (snapshot.goals.isNotEmpty()) {
            goalsDao.insertAll(snapshot.goals)
        }
        if (snapshot.transactions.isNotEmpty()) {
            transactionsDao.insertAll(snapshot.transactions)
        }

        if (snapshot.categories.isEmpty()) {
            seedDefaultCategoriesIfEmpty()
            enforceCategoryPolicy()
        }
    }

    suspend fun upsertSavingGoal(targetAmount: Double, deadline: Long?) = withContext(ioDispatcher) {
        val existing = goalsDao.getSavingGoalNow()
        if (existing != null) {
            goalsDao.update(
                GoalEntity(
                    id = existing.id,
                    categoryId = null,
                    targetAmount = targetAmount,
                    currentAmount = 0.0,
                    deadline = deadline
                )
            )
        } else {
            goalsDao.insert(
                GoalEntity(
                    categoryId = null,
                    targetAmount = targetAmount,
                    currentAmount = 0.0,
                    deadline = deadline
                )
            )
        }
    }

    suspend fun upsertCategoryLimitGoal(categoryId: Long, limitAmount: Double) = withContext(ioDispatcher) {
        val existing = goalsDao.getLimitByCategoryId(categoryId)
        if (existing != null) {
            goalsDao.update(
                existing.copy(
                    targetAmount = limitAmount,
                    deadline = null
                )
            )
        } else {
            goalsDao.insert(
                GoalEntity(
                    categoryId = categoryId,
                    targetAmount = limitAmount,
                    currentAmount = 0.0,
                    deadline = null
                )
            )
        }
    }

    suspend fun deleteGoal(goalId: Long): Boolean = withContext(ioDispatcher) {
        goalsDao.deleteById(goalId) > 0
    }

    /**
     * Deletes all user-generated data stored locally (transactions, goals, categories)
     * and then reseeds starter categories if needed.
     */
    suspend fun resetAllUserDataAndReseedDefaults() = withContext(ioDispatcher) {
        transactionsDao.deleteAll()
        goalsDao.deleteAll()
        categoryDao.deleteAll()

        // After wiping categories, we can seed localized starter categories again.
        seedDefaultCategoriesIfEmpty()
        enforceCategoryPolicy()
    }

    fun observeExpenseTotalByCategoryCurrentMonth(categoryId: Long): Flow<Double> {
        val (from, to) = currentMonthRange()
        return transactionsDao.observeCategoryTotalInPeriod(
            categoryId = categoryId,
            type = TransactionType.EXPENSE,
            from = from,
            to = to
        )
    }

    private fun currentMonthRange(): Pair<Long, Long> {
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
        return startCal.timeInMillis to endCal.timeInMillis
    }

    private suspend fun addTransactionInternal(
        amount: Double,
        type: TransactionType,
        categoryId: Long?,
        note: String?,
        timestamp: Long
    ) = withContext(ioDispatcher) {
        val entity = TransactionEntity(
            amount = amount,
            type = type,
            categoryId = categoryId,
            note = note,
            timestamp = timestamp
        )
        transactionsDao.insert(entity)
    }

    suspend fun seedDefaultCategoriesIfEmpty() = withContext(ioDispatcher) {
        val count = categoryDao.getCount()
        if (count > 0) return@withContext

        val expenseDefaults = defaultExpenseCategories
        val incomeDefaults = defaultIncomeCategories
        val palette = CATEGORY_COLOR_PALETTE.shuffled()
        val defaults = buildList {
            addAll(expenseDefaults.mapIndexed { index, name ->
                CategoryEntity(
                    name = name,
                    color = palette[index % palette.size],
                    isIncome = false,
                    isDefault = true
                )
            })
            addAll(incomeDefaults.mapIndexed { index, name ->
                CategoryEntity(
                    name = name,
                    color = palette[(expenseDefaults.size + index) % palette.size],
                    isIncome = true,
                    isDefault = true
                )
            })
        }
        categoryDao.insertAll(defaults)
    }

    suspend fun enforceCategoryPolicy() = withContext(ioDispatcher) {
        val all = categoryDao.getAllNow()
        val allowedExpenseDefaultNames = (DEFAULT_EXPENSE_CATEGORIES + defaultExpenseCategories).toSet()
        val disallowedExpenseDefaults =
            all.filter { !it.isIncome && it.isDefault && it.name !in allowedExpenseDefaultNames }
        disallowedExpenseDefaults.forEach { category ->
            categoryDao.deleteById(category.id)
            transactionsDao.clearCategoryReferences(category.id)
        }
    }

    private suspend fun pickDistinctCategoryColor(): String {
        val usedColors = categoryDao.getAllNow()
            .mapNotNull { it.color }
            .toSet()
        val available = CATEGORY_COLOR_PALETTE.filterNot { it in usedColors }
        return if (available.isNotEmpty()) available.random() else CATEGORY_COLOR_PALETTE.random()
    }
}

data class DashboardSummary(
    val balance: Double,
    val totalIncome: Double,
    val totalExpense: Double
)

data class BackupSnapshot(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val goals: List<GoalEntity>
)

