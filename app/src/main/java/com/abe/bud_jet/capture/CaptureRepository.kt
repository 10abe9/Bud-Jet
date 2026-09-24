package com.abe.bud_jet.capture

import com.abe.bud_jet.capture.NotificationParser.ParseResult
import com.abe.bud_jet.database.dao.CaptureDao
import com.abe.bud_jet.database.dao.CategoryDao
import com.abe.bud_jet.database.dao.TransactionsDao
import com.abe.bud_jet.database.entities.CaptureSourceEntity
import com.abe.bud_jet.database.entities.MerchantRuleEntity
import com.abe.bud_jet.database.entities.PendingCaptureEntity
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Turns payment notifications into transactions. Everything happens on the device:
 * raw notification text is stored only for unconfirmed payments and deleted once handled.
 */
class CaptureRepository(
    private val transactionsDao: TransactionsDao,
    private val categoryDao: CategoryDao,
    private val captureDao: CaptureDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    enum class Outcome { ADDED, NEEDS_CONFIRMATION, DUPLICATE, IGNORED, SOURCE_SUGGESTED }

    data class ProcessResult(
        val outcome: Outcome,
        val parsed: ParseResult,
        /** The notification came from an app the user tracks. */
        val trackedSource: Boolean,
        /** The app sent its first payment-like notification and is not tracked yet. */
        val newSourceDetected: Boolean = false
    )

    data class CapturedNotification(
        val packageName: String,
        val appLabel: String,
        val title: String?,
        val text: String?,
        val postedAt: Long,
        /** Stable id of this notification content, used to skip re-posts and updates. */
        val externalId: String
    )

    fun observePending(): Flow<List<PendingCaptureEntity>> = captureDao.observePending()

    fun observeSources(): Flow<List<CaptureSourceEntity>> = captureDao.observeSources()

    fun observeEnabledSourceCount(): Flow<Int> =
        captureDao.observeSources().map { sources -> sources.count { it.enabled } }

    suspend fun setSourceEnabled(packageName: String, enabled: Boolean) = withContext(ioDispatcher) {
        captureDao.setSourceEnabled(packageName, enabled)
    }

    suspend fun process(notification: CapturedNotification, appCurrency: String): ProcessResult =
        withContext(ioDispatcher) {
            val result = NotificationParser.parse(notification.title, notification.text, appCurrency)
            val source = captureDao.getSource(notification.packageName)

            if (source == null || !source.enabled) {
                // Not tracked: only remember that this app sends payment-like notifications,
                // so the user can choose to track it. Its text is not stored.
                if (result is ParseResult.Ignored) return@withContext ProcessResult(Outcome.IGNORED, result, trackedSource = false)
                captureDao.upsertSource(
                    CaptureSourceEntity(
                        packageName = notification.packageName,
                        appLabel = notification.appLabel,
                        enabled = false,
                        detectedCount = (source?.detectedCount ?: 0) + 1,
                        lastSeenAt = notification.postedAt
                    )
                )
                return@withContext ProcessResult(
                    Outcome.SOURCE_SUGGESTED,
                    result,
                    trackedSource = false,
                    newSourceDetected = source == null
                )
            }

            captureDao.upsertSource(source.copy(lastSeenAt = notification.postedAt))
            val outcome = when (result) {
                is ParseResult.Recognized -> addTransaction(notification, result)
                is ParseResult.Uncertain -> addPending(notification, result)
                ParseResult.Ignored -> Outcome.IGNORED
            }
            ProcessResult(outcome, result, trackedSource = true)
        }

    private suspend fun addTransaction(
        notification: CapturedNotification,
        result: ParseResult.Recognized
    ): Outcome {
        val inserted = transactionsDao.insertIgnoringDuplicate(
            TransactionEntity(
                amount = result.amount,
                type = if (result.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                categoryId = result.merchant?.let { suggestCategoryIdInternal(it, result.isIncome) },
                note = null,
                timestamp = notification.postedAt,
                source = TransactionEntity.SOURCE_NOTIFICATION,
                sourceApp = notification.packageName,
                merchant = result.merchant,
                externalId = notification.externalId
            )
        )
        return if (inserted == -1L) Outcome.DUPLICATE else Outcome.ADDED
    }

    private suspend fun addPending(
        notification: CapturedNotification,
        result: ParseResult.Uncertain
    ): Outcome {
        // The same notification may be re-posted after the user already confirmed it.
        if (transactionsDao.countByExternalId(notification.externalId) > 0) return Outcome.DUPLICATE
        val inserted = captureDao.insertPending(
            PendingCaptureEntity(
                externalId = notification.externalId,
                packageName = notification.packageName,
                appLabel = notification.appLabel,
                text = listOfNotNull(notification.title, notification.text).joinToString("\n"),
                postedAt = notification.postedAt,
                amount = result.amount,
                currencyCode = result.currencyCode,
                isIncome = result.isIncome,
                merchant = result.merchant
            )
        )
        return if (inserted == -1L) Outcome.DUPLICATE else Outcome.NEEDS_CONFIRMATION
    }

    suspend fun getPending(id: Long): PendingCaptureEntity? = withContext(ioDispatcher) {
        captureDao.getPending(id)
    }

    /** Turns an unconfirmed notification into a transaction with the user's corrections. */
    suspend fun confirmPending(
        pendingId: Long,
        amount: Double,
        isIncome: Boolean,
        categoryId: Long?,
        note: String?
    ) = withContext(ioDispatcher) {
        val pending = captureDao.getPending(pendingId) ?: return@withContext
        transactionsDao.insertIgnoringDuplicate(
            TransactionEntity(
                amount = amount,
                type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                categoryId = categoryId,
                note = note,
                timestamp = pending.postedAt,
                source = TransactionEntity.SOURCE_NOTIFICATION,
                sourceApp = pending.packageName,
                merchant = pending.merchant,
                externalId = pending.externalId
            )
        )
        val merchant = pending.merchant
        if (merchant != null && categoryId != null) {
            val key = MerchantCategorizer.merchantKey(merchant)
            if (key.isNotEmpty()) captureDao.upsertRule(MerchantRuleEntity(key, categoryId))
        }
        captureDao.deletePending(pendingId)
    }

    suspend fun dismissPending(pendingId: Long) = withContext(ioDispatcher) {
        captureDao.deletePending(pendingId)
    }

    suspend fun countPending(): Int = withContext(ioDispatcher) { captureDao.countPending() }

    /** Unconfirmed notifications older than this are dropped to keep raw texts short-lived. */
    suspend fun cleanupOldPending(nowMillis: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        captureDao.deletePendingOlderThan(nowMillis - PENDING_TTL_MILLIS)
    }

    suspend fun suggestCategoryId(merchant: String?, isIncome: Boolean): Long? = withContext(ioDispatcher) {
        merchant?.let { suggestCategoryIdInternal(it, isIncome) }
    }

    private suspend fun suggestCategoryIdInternal(merchant: String, isIncome: Boolean): Long? {
        val key = MerchantCategorizer.merchantKey(merchant)
        val categories = categoryDao.getAllNow()
        if (key.isNotEmpty()) {
            val ruleCategory = captureDao.getRule(key)?.categoryId
            if (ruleCategory != null && categories.any { it.id == ruleCategory && it.isIncome == isIncome }) {
                return ruleCategory
            }
        }
        if (isIncome) return null
        val defaultKey = MerchantCategorizer.guessDefaultKey(merchant) ?: return null
        return categories.firstOrNull { it.defaultKey == defaultKey && !it.isIncome }?.id
    }

    companion object {
        private const val PENDING_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
