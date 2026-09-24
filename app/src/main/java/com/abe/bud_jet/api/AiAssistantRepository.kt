package com.abe.bud_jet.api

import android.content.Context
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.utils.DateRanges
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entry point for AI features. Checks the user's consent and the Pro plan before anything
 * leaves the device, builds the [BudgetSummary] and calls the backend.
 */
class AiAssistantRepository(private val context: Context) {

    sealed class Outcome<out T> {
        data class Success<T>(val value: T) : Outcome<T>()
        /** The user did not turn the assistant on, or the Pro plan is not active. */
        object Disabled : Outcome<Nothing>()
        /** No server address in gradle.properties yet. */
        object NotConfigured : Outcome<Nothing>()
        /** The server did not confirm a Pro subscription (HTTP 402/403). */
        object NotEntitled : Outcome<Nothing>()
        /** Daily request limit reached (HTTP 429). */
        object RateLimited : Outcome<Nothing>()
        data class Failed(val reason: String) : Outcome<Nothing>()
    }

    private val preferences = PreferenceManager.getInstance(context)
    private val repository = FinanceRepositoryProvider.get(context)

    suspend fun insights(): Outcome<List<BudJetApi.AiInsight>> {
        if (!preferences.isAiAssistantActive()) return Outcome.Disabled
        return BudJetApi.aiInsights(caller(), buildSummary()).toOutcome()
    }

    /** [question] must already pass [AiChatPolicy.normalize]; [history] is trimmed here. */
    suspend fun ask(question: String, history: List<ChatTurn>): Outcome<String> {
        if (!preferences.isAiAssistantActive()) return Outcome.Disabled
        return BudJetApi.aiChat(caller(), buildSummary(), question, AiChatPolicy.trimHistory(history)).toOutcome()
    }

    private fun caller() = BudJetApi.Caller(
        installId = preferences.getInstallId(),
        purchaseToken = preferences.getPurchaseToken(),
        language = preferences.getAppLanguage()
    )

    /** Last three months of totals, limits and the saving goal; see [BudgetSummary]. */
    suspend fun buildSummary(): BudgetSummary {
        val now = System.currentTimeMillis()
        val from = now - 100L * 24 * 60 * 60 * 1000
        val categories = repository.observeCategories().first().associateBy { it.id }
        val transactions = repository.observeTransactionsInPeriod(from, now).first()
        val entries = transactions.map { tx ->
            BudgetSummaryBuilder.Entry(
                timestamp = tx.timestamp,
                amount = tx.amount,
                isIncome = tx.type == TransactionType.INCOME,
                category = tx.categoryId?.let { categories[it]?.name }
            )
        }

        val month = DateRanges.month(now)
        val spentByCategory = transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in month.from..month.to }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val limits = repository.observeCategoryLimitGoals().first().mapNotNull { goal ->
            val categoryId = goal.categoryId ?: return@mapNotNull null
            val name = categories[categoryId]?.name ?: return@mapNotNull null
            BudgetSummary.LimitSummary(name, goal.targetAmount, spentByCategory[categoryId] ?: 0.0)
        }
        val savingGoal = repository.observeSavingGoal().first()?.let { goal ->
            BudgetSummary.SavingGoalSummary(
                target = goal.targetAmount,
                saved = repository.observeNetSince(goal.createdAt).first().coerceAtLeast(0.0),
                deadline = goal.deadline?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) }
            )
        }

        return BudgetSummaryBuilder.build(
            currency = preferences.getCurrencyCode(),
            entries = entries,
            limits = limits,
            savingGoal = savingGoal,
            uncategorizedLabel = context.getString(R.string.analytics_uncategorized),
            nowMillis = now
        )
    }

    private fun <T> BudJetApi.ApiResult<T>.toOutcome(): Outcome<T> = when (this) {
        is BudJetApi.ApiResult.Success -> Outcome.Success(value)
        BudJetApi.ApiResult.NotConfigured -> Outcome.NotConfigured
        is BudJetApi.ApiResult.HttpError -> when (code) {
            402, 403 -> Outcome.NotEntitled
            429 -> Outcome.RateLimited
            else -> Outcome.Failed("HTTP $code: $message")
        }
        is BudJetApi.ApiResult.NetworkError -> Outcome.Failed(cause.message ?: cause.javaClass.simpleName)
    }
}
