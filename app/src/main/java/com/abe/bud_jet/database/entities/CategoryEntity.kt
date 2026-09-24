package com.abe.bud_jet.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val isDefault: Boolean = true,
    val isIncome: Boolean = false,
    val isCustom: Boolean = false,
    /** Stable id of a built-in category (see DefaultCategories); null for user categories. */
    @ColumnInfo(name = "defaultKey")
    val defaultKey: String? = null
)

