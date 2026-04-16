package com.abe.bud_jet.database.models

import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Transaction(
    val id: Long,
    val timestamp: Long,
    val categoryId: Long?,
    val note: String?,
    val title: String,
    val categoryColorHex: String? = null,
    val amount: Double,
    val isIncome: Boolean,
    val date: String
)

fun TransactionEntity.toUiModel(): Transaction {
    val isIncome = type == TransactionType.INCOME
    val pattern = "dd MMM, HH:mm"
    val formattedDate = SimpleDateFormat(pattern, Locale.getDefault())
        .format(Date(timestamp))

    return Transaction(
        id = id,
        timestamp = timestamp,
        categoryId = categoryId,
        note = note,
        title = if (isIncome) "Income" else "Expense",
        categoryColorHex = null,
        amount = amount,
        isIncome = isIncome,
        date = formattedDate
    )
}

fun Transaction.withCategoryMeta(
    categoryName: String?,
    categoryColorHex: String?
): Transaction {
    val displayTitle = when {
        !categoryName.isNullOrBlank() -> categoryName
        !note.isNullOrBlank() -> note
        else -> title
    }
    return copy(
        title = displayTitle,
        categoryColorHex = categoryColorHex
    )
}