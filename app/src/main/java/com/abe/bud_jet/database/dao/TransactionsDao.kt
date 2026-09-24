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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /** Returns -1 when a transaction with the same external_id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(transaction: TransactionEntity): Long

    @Query("SELECT COUNT(*) FROM transactions WHERE external_id = :externalId")
    suspend fun countByExternalId(externalId: String): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE type = 'EXPENSE' AND timestamp >= :from " +
            "AND (merchant IS NOT NULL OR note IS NOT NULL) ORDER BY timestamp ASC"
    )
    fun observeExpensesWithPayeeSince(from: Long): Flow<List<TransactionEntity>>

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

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllNow(): List<TransactionEntity>

    @Query("UPDATE transactions SET amount = amount * :rate")
    suspend fun multiplyAllAmounts(rate: Double)

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp BETWEEN :from AND :to")
    suspend fun countInPeriod(from: Long, to: Long): Int

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) " +
            "FROM transactions WHERE timestamp >= :from"
    )
    fun observeNetSince(from: Long): Flow<Double>
}
