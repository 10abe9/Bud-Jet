package com.abe.bud_jet.database.models

data class CategoryStat(
    val category: String,
    val total: Float,
    val colorHex: String = "#94A3B8"
)