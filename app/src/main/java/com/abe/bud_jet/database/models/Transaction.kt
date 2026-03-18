package com.abe.bud_jet.database.models

data class Transaction(
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val date: String
)