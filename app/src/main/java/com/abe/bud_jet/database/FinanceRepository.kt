package com.abe.bud_jet.database

import com.abe.bud_jet.capture.MerchantCategorizer
import com.abe.bud_jet.capture.RecurringDetector
import com.abe.bud_jet.database.dao.CaptureDao
import com.abe.bud_jet.database.dao.CategoryDao
import com.abe.bud_jet.database.entities.MerchantRuleEntity
import com.abe.bud_jet.database.dao.GoalsDao
import com.abe.bud_jet.database.dao.TransactionsDao
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.database.entities.GoalEntity
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.utils.CategoryPalette
import com.abe.bud_jet.utils.DateRanges
import com.abe.bud_jet.utils.MillisRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    private val captureDao: CaptureDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Runs the block atomically (Room transaction in production). */
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() }
) {
    companion object {
        const val MAX_CATEGORY_NAME_LENGTH = 18
        const val MAX_EXPENSE_CATEGORIES = 5
        const val MAX_INCOME_CATEGORIES = 3
        private val CATEGORY_COLOR_PALETTE = CategoryPalette.colors
        private const val RECURRING_LOOKBACK_MILLIS = 400L * 24 * 60 * 60 * 1000
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

    /** Emits the current calendar month and re-emits when a new month starts. */
    fun observeCurrentMonthRange(): Flow<MillisRange> = flow {
        while (true) {
            val range = DateRanges.month()
            emit(range)
            delay((range.to + 1 - System.currentTimeMillis()).coerceAtLeast(1_000L))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCurrentMonthTransactions(): Flow<List<TransactionEntity>> =
        observeCurrentMonthRange().flatMapLatest { range ->
            transactionsDao.observeInPeriod(range.from, range.to)
        }

    /**
     * Regular payments (subscriptions) found in the last ~13 months of expenses that have a
     * merchant (captured) or a note (manual entries). Derived on the fly, so the result always
     * reflects edits and deletions.
     */
    fun observeRecurringPayments(): Flow<List<RecurringDetector.RecurringPayment>> {
        val from = System.currentTimeMillis() - RECURRING_LOOKBACK_MILLIS
        return transactionsDao.observeExpensesWithPayeeSince(from).map { expenses ->
            RecurringDetector.detect(
                expenses.mapNotNull { tx ->
                    val payee = tx.merchant ?: tx.note ?: return@mapNotNull null
                    RecurringDetector.Payment(tx.timestamp, tx.amount, payee)
                }
            )
        }
    }

    /** Income minus expenses recorded at or after [from]. */
    fun observeNetSince(from: Long): Flow<Double> =
        transactionsDao.observeNetSince(from)

    suspend fun countTransactionsInPeriod(from: Long, to: Long): Int = withContext(ioDispatcher) {
        transactionsDao.countInPeriod(from, to)
    }

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
        var deleted = 0
        runInTransaction {
            deleted = categoryDao.deleteById(categoryId)
            if (deleted > 0) {
                transactionsDao.clearCategoryReferences(categoryId)
                captureDao.deleteRulesForCategory(categoryId)
                // A limit for a deleted category can no longer be tracked.
                goalsDao.getLimitByCategoryId(categoryId)?.let { goalsDao.deleteById(it.id) }
            }
        }
        if (deleted > 0) DeleteCategoryResult.SUCCESS else DeleteCategoryResult.NOT_FOUND
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
        val existing = transactionsDao.getById(id) ?: return@withContext
        // copy() keeps the capture fields (source, app, merchant) of auto-added transactions.
        transactionsDao.update(
            existing.copy(
                amount = amount,
                type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                categoryId = categoryId,
                note = note,
                timestamp = timestamp
            )
        )
        // Remember the choice so the next payment at this merchant gets the same category.
        val merchant = existing.merchant
        if (merchant != null && categoryId != null) {
            val key = MerchantCategorizer.merchantKey(merchant)
            if (key.isNotEmpty()) captureDao.upsertRule(MerchantRuleEntity(key, categoryId))
        }
    }

    suspend fun deleteTransaction(id: Long): Boolean = withContext(ioDispatcher) {
        transactionsDao.deleteById(id) > 0
    }

    suspend fun getTransactionsCount(): Int = withContext(ioDispatcher) {
        transactionsDao.getCount()
    }

    /** Converts every stored amount (transactions, saving goal, limits) with one rate, atomically. */
    suspend fun convertAllAmounts(rate: Double) = withContext(ioDispatcher) {
        runInTransaction {
            transactionsDao.multiplyAllAmounts(rate)
            goalsDao.multiplyAllAmounts(rate)
        }
    }

    suspend fun createBackupSnapshot(): BackupSnapshot = withContext(ioDispatcher) {
        BackupSnapshot(
            transactions = transactionsDao.getAllNow(),
            categories = categoryDao.getAllNow(),
            goals = goalsDao.getAllNow()
        )
    }

    suspend fun restoreBackupSnapshot(snapshot: BackupSnapshot) = withContext(ioDispatcher) {
        runInTransaction { restoreBackupSnapshotInternal(snapshot) }
    }

    private suspend fun restoreBackupSnapshotInternal(snapshot: BackupSnapshot) {
        // Rules point at category ids, which the backup may reuse differently.
        captureDao.deleteAllRules()
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
        // Built-in categories are re-seeded and renamed by syncDefaultCategories on restart.
    }

    suspend fun upsertSavingGoal(targetAmount: Double, deadline: Long?) = withContext(ioDispatcher) {
        val existing = goalsDao.getSavingGoalNow()
        if (existing != null) {
            goalsDao.update(
                existing.copy(
                    targetAmount = targetAmount,
                    deadline = deadline
                )
            )
        } else {
            goalsDao.insert(
                GoalEntity(
                    categoryId = null,
                    targetAmount = targetAmount,
                    currentAmount = 0.0,
                    deadline = deadline,
                    createdAt = System.currentTimeMillis()
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
                    deadline = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteGoal(goalId: Long): Boolean = withContext(ioDispatcher) {
        goalsDao.deleteById(goalId) > 0
    }

    /** Deletes all user-generated data stored locally (transactions, goals, categories). */
    suspend fun resetAllUserData() = withContext(ioDispatcher) {
        runInTransaction {
            transactionsDao.deleteAll()
            goalsDao.deleteAll()
            categoryDao.deleteAll()
            captureDao.deleteAllPending()
            captureDao.deleteAllSources()
            captureDao.deleteAllRules()
        }
        // Starter categories are re-seeded by syncDefaultCategories when the app restarts.
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

    /**
     * Keeps built-in categories in the current app language: recognizes old ones by name,
     * seeds them on a fresh install and renames them to [localizedNames] (key -> name).
     * Call with names resolved from an Activity context, which carries the app locale.
     */
    suspend fun syncDefaultCategories(localizedNames: Map<String, String>) = withContext(ioDispatcher) {
        runInTransaction {
            assignMissingDefaultKeys()
            seedDefaultCategoriesIfEmpty(localizedNames)
            renameDefaultCategories(localizedNames)
            removeRetiredDefaultCategories()
        }
    }

    /** Categories from older versions and backups have no key yet; match them by name. */
    private suspend fun assignMissingDefaultKeys() {
        val all = categoryDao.getAllNow()
        val usedKeys = all.mapNotNull { it.defaultKey }.toMutableSet()
        all.filter { it.isDefault && it.defaultKey == null }.forEach { category ->
            val key = DefaultCategories.keyForName(category.name, category.isIncome) ?: return@forEach
            if (usedKeys.add(key)) categoryDao.updateDefaultKey(category.id, key)
        }
    }

    private suspend fun seedDefaultCategoriesIfEmpty(localizedNames: Map<String, String>) {
        if (categoryDao.getCount() > 0) return

        val palette = CATEGORY_COLOR_PALETTE.shuffled()
        val defaults = DefaultCategories.all.mapIndexed { index, definition ->
            CategoryEntity(
                name = localizedNames[definition.key] ?: definition.key,
                color = palette[index % palette.size],
                isIncome = definition.isIncome,
                isDefault = true,
                defaultKey = definition.key
            )
        }
        categoryDao.insertAll(defaults)
    }

    private suspend fun renameDefaultCategories(localizedNames: Map<String, String>) {
        val all = categoryDao.getAllNow()
        all.filter { it.defaultKey != null }.forEach { category ->
            val target = localizedNames[category.defaultKey] ?: return@forEach
            if (target == category.name) return@forEach
            // Keep the old name rather than duplicate a user category with the same name.
            val clash = all.any {
                it.id != category.id && it.isIncome == category.isIncome && it.name.equals(target, ignoreCase = true)
            }
            if (!clash) categoryDao.updateName(category.id, target)
        }
    }

    /**
     * Older versions seeded more built-in expense categories; those without a known key are
     * removed. Unlike the previous name-based check, this no longer depends on the app
     * language, so switching language cannot delete current built-in categories.
     */
    private suspend fun removeRetiredDefaultCategories() {
        categoryDao.getAllNow()
            .filter { !it.isIncome && it.isDefault && it.defaultKey == null }
            .forEach { category ->
                categoryDao.deleteById(category.id)
                transactionsDao.clearCategoryReferences(category.id)
                captureDao.deleteRulesForCategory(category.id)
                goalsDao.getLimitByCategoryId(category.id)?.let { goalsDao.deleteById(it.id) }
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

