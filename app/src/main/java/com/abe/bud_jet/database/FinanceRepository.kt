package com.abe.bud_jet.database

import com.abe.bud_jet.database.dao.CategoryDao
import com.abe.bud_jet.database.dao.GoalsDao
import com.abe.bud_jet.database.dao.TransactionsDao
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.database.entities.GoalEntity
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
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

    suspend fun addCustomCategory(name: String, isIncome: Boolean) = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext

        val existing = categoryDao.getByNameAndType(trimmed, isIncome)
        if (existing != null) return@withContext

        val entity = CategoryEntity(
            name = trimmed,
            isDefault = false,
            isIncome = isIncome,
            isCustom = true
        )
        categoryDao.insert(entity)
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

        val defaults = listOf(
            // Expense categories
            CategoryEntity(name = "Food", isIncome = false, isDefault = true),
            CategoryEntity(name = "Transport", isIncome = false, isDefault = true),
            CategoryEntity(name = "Entertainment", isIncome = false, isDefault = true),
            CategoryEntity(name = "Subscriptions", isIncome = false, isDefault = true),
            CategoryEntity(name = "Health", isIncome = false, isDefault = true),
            CategoryEntity(name = "Shopping", isIncome = false, isDefault = true),
            CategoryEntity(name = "Other", isIncome = false, isDefault = true),

            // Income categories
            CategoryEntity(name = "Salary", isIncome = true, isDefault = true),
            CategoryEntity(name = "Deal", isIncome = true, isDefault = true),
            CategoryEntity(name = "Gift", isIncome = true, isDefault = true),
            CategoryEntity(name = "Other income", isIncome = true, isDefault = true)
        )
        categoryDao.insertAll(defaults)
    }
}

data class DashboardSummary(
    val balance: Double,
    val totalIncome: Double,
    val totalExpense: Double
)

