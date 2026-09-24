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
    suspend fun insert(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET targetAmount = targetAmount * :rate, currentAmount = currentAmount * :rate")
    suspend fun multiplyAllAmounts(rate: Double)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteById(goalId: Long): Int

    @Query("DELETE FROM goals")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM goals WHERE categoryId IS NULL LIMIT 1")
    fun observeSavingGoal(): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE categoryId IS NULL LIMIT 1")
    suspend fun getSavingGoalNow(): GoalEntity?

    @Query("SELECT * FROM goals WHERE categoryId IS NOT NULL ORDER BY id DESC")
    fun observeCategoryLimits(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getLimitByCategoryId(categoryId: Long): GoalEntity?

    @Query("SELECT * FROM goals ORDER BY id ASC")
    suspend fun getAllNow(): List<GoalEntity>
}

