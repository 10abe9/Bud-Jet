package com.abe.bud_jet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.abe.bud_jet.database.entities.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalsDao {

    @Query("SELECT * FROM goals")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET currentAmount = :currentAmount WHERE id = :goalId")
    suspend fun updateProgress(goalId: Long, currentAmount: Double)
}

