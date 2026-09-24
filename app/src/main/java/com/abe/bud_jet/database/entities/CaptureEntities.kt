package com.abe.bud_jet.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A payment notification the parser was not sure about. Kept on the device until the user
 * confirms it as a transaction or dismisses it.
 */
@Entity(
    tableName = "pending_captures",
    indices = [Index(value = ["externalId"], unique = true)]
)
data class PendingCaptureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val externalId: String,
    val packageName: String,
    val appLabel: String?,
    val text: String,
    val postedAt: Long,
    val amount: Double?,
    val currencyCode: String?,
    val isIncome: Boolean?,
    val merchant: String?
)

/** App whose payment notifications are (or could be) tracked. Only enabled ones are read. */
@Entity(tableName = "capture_sources")
data class CaptureSourceEntity(
    @PrimaryKey
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean,
    /** How many money-like notifications it posted (for suggesting it to the user). */
    val detectedCount: Int,
    val lastSeenAt: Long
)

/** Category the user chose for a merchant, reused for its next captured payments. */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey
    val merchantKey: String,
    val categoryId: Long
)
