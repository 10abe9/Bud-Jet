package com.abe.bud_jet.database.models

import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Transaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val date: String
)

fun TransactionEntity.toUiModel(): Transaction {
    val isIncome = type == TransactionType.INCOME
    val pattern = "dd MMM, HH:mm"
    val formattedDate = SimpleDateFormat(pattern, Locale.getDefault())
        .format(Date(timestamp))

    val displayTitle = when {
        !note.isNullOrBlank() -> note
        isIncome -> "Income"
        else -> "Expense"
    }

    return Transaction(
        id = id,
        title = displayTitle,
        amount = amount,
        isIncome = isIncome,
        date = formattedDate
    )
}