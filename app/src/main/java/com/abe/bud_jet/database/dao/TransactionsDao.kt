package com.abe.bud_jet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions " +
                "WHERE timestamp BETWEEN :from AND :to " +
                "ORDER BY timestamp DESC"
    )
    fun observeInPeriod(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                "WHERE type = :type"
    )
    fun observeTotalByType(type: TransactionType): Flow<Double>

    @Query("SELECT MIN(timestamp) FROM transactions")
    fun observeMinTimestamp(): Flow<Long?>

    @Query("UPDATE transactions SET category_id = NULL WHERE category_id = :categoryId")
    suspend fun clearCategoryReferences(categoryId: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int

    @Query("UPDATE transactions SET amount = amount * :rate")
    suspend fun multiplyAllAmounts(rate: Double)

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE category_id = :categoryId AND type = :type AND timestamp BETWEEN :from AND :to"
    )
    fun observeCategoryTotalInPeriod(
        categoryId: Long,
        type: TransactionType,
        from: Long,
        to: Long
    ): Flow<Double>
}

