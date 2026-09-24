package com.abe.bud_jet.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["external_id"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "amount")
    val amount: Double,
    @ColumnInfo(name = "type")
    val type: TransactionType,
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    /** [SOURCE_MANUAL] or [SOURCE_NOTIFICATION]. */
    @ColumnInfo(name = "source", defaultValue = "manual")
    val source: String = SOURCE_MANUAL,
    /** Package of the app whose notification produced this transaction. */
    @ColumnInfo(name = "source_app")
    val sourceApp: String? = null,
    @ColumnInfo(name = "merchant")
    val merchant: String? = null,
    /** Deduplication key of the captured notification; null for manual entries. */
    @ColumnInfo(name = "external_id")
    val externalId: String? = null
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_NOTIFICATION = "notification"
    }
}

enum class TransactionType {
    INCOME,
    EXPENSE
}
