package com.abe.bud_jet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

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
}

