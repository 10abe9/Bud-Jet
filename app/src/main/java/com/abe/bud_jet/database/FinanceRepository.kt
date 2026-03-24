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
import kotlinx.coroutines.withContext

class FinanceRepository(
    private val transactionsDao: TransactionsDao,
    private val categoryDao: CategoryDao,
    private val goalsDao: GoalsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val MAX_CATEGORY_NAME_LENGTH = 18
        const val MAX_EXPENSE_CATEGORIES = 5
        const val MAX_INCOME_CATEGORIES = 3
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

    fun observeCategories(): Flow<List<CategoryEntity>> =
        categoryDao.observeAll()

    fun observeCategoriesByType(isIncome: Boolean): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(isIncome)

    fun observeGoals(): Flow<List<GoalEntity>> =
        goalsDao.observeAll()

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

        val expenseDefaults = DEFAULT_EXPENSE_CATEGORIES
        val incomeDefaults = DEFAULT_INCOME_CATEGORIES
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
        val disallowedExpenseDefaults = all.filter {
            !it.isIncome && it.isDefault && it.name !in DEFAULT_EXPENSE_CATEGORIES
        }
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

