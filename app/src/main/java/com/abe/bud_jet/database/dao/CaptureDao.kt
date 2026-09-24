package com.abe.bud_jet.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abe.bud_jet.database.entities.CaptureSourceEntity
import com.abe.bud_jet.database.entities.MerchantRuleEntity
import com.abe.bud_jet.database.entities.PendingCaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(pending: PendingCaptureEntity): Long

    @Query("SELECT * FROM pending_captures ORDER BY postedAt DESC")
    fun observePending(): Flow<List<PendingCaptureEntity>>

    @Query("SELECT * FROM pending_captures WHERE id = :id LIMIT 1")
    suspend fun getPending(id: Long): PendingCaptureEntity?

    @Query("SELECT COUNT(*) FROM pending_captures")
    suspend fun countPending(): Int

    @Query("DELETE FROM pending_captures WHERE id = :id")
    suspend fun deletePending(id: Long)

    @Query("DELETE FROM pending_captures WHERE postedAt < :before")
    suspend fun deletePendingOlderThan(before: Long)

    @Query("DELETE FROM pending_captures")
    suspend fun deleteAllPending()

    @Query("SELECT * FROM capture_sources ORDER BY enabled DESC, detectedCount DESC")
    fun observeSources(): Flow<List<CaptureSourceEntity>>

    @Query("SELECT * FROM capture_sources WHERE packageName = :packageName LIMIT 1")
    suspend fun getSource(packageName: String): CaptureSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: CaptureSourceEntity)

    @Query("UPDATE capture_sources SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setSourceEnabled(packageName: String, enabled: Boolean)

    @Query("DELETE FROM capture_sources")
    suspend fun deleteAllSources()

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun getRule(merchantKey: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE categoryId = :categoryId")
    suspend fun deleteRulesForCategory(categoryId: Long)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAllRules()
}
