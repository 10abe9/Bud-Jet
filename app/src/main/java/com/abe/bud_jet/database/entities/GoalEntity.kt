package com.abe.bud_jet.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val categoryId: Long?,
    val targetAmount: Double,
    val currentAmount: Double,
    val deadline: Long?,
    /** Saving goals count income minus expenses from this moment on. */
    @ColumnInfo(name = "createdAt", defaultValue = "0")
    val createdAt: Long = 0L
)
