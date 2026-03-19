package com.abe.bud_jet.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val categoryId: Long?,
    val targetAmount: Double,
    val currentAmount: Double,
    val deadline: Long?
)

