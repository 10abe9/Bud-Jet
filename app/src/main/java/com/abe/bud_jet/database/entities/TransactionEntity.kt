package com.abe.bud_jet.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
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
    val timestamp: Long
)

enum class TransactionType {
    INCOME,
    EXPENSE
}
