package com.couponit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons ORDER BY favorite DESC, expiryConfirmed DESC, expiryDate ASC")
    fun observeCoupons(): Flow<List<CouponEntity>>

    @Query("SELECT * FROM coupons WHERE id = :id")
    suspend fun coupon(id: String): CouponEntity?

    @Query("SELECT * FROM image_assets WHERE id = :id")
    suspend fun asset(id: String): ImageAssetEntity?

    @Upsert
    suspend fun upsertCoupon(coupon: CouponEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCoupon(coupon: CouponEntity)

    @Update
    suspend fun updateCoupon(coupon: CouponEntity)

    @Query("SELECT id FROM coupons WHERE codeValue = :codeValue LIMIT 1")
    suspend fun couponIdByCode(codeValue: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(asset: ImageAssetEntity)

    @Query("DELETE FROM image_assets WHERE id = :id")
    suspend fun deleteAsset(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandidates(candidates: List<CodeCandidateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCandidates(candidates: List<CodeCandidateEntity>)

    /** 원본과 그 원본에서 새로 만든 쿠폰들을 한 트랜잭션으로 저장한다. */
    @Transaction
    suspend fun insertImport(
        asset: ImageAssetEntity,
        coupons: List<CouponEntity>,
        candidates: List<CodeCandidateEntity>,
    ): ImportWriteResult {
        insertAsset(asset)
        val savedIds = mutableListOf<String>()
        var duplicates = 0
        for (coupon in coupons) {
            if (coupon.codeValue != null && couponIdByCode(coupon.codeValue) != null) {
                duplicates++
                continue
            }
            insertCoupon(coupon)
            insertCandidates(candidates.filter { it.couponId == coupon.id })
            savedIds += coupon.id
        }
        if (savedIds.isEmpty()) deleteAsset(asset.id)
        return ImportWriteResult(savedIds, duplicates)
    }

    @Query("SELECT * FROM code_candidates WHERE couponId = :couponId ORDER BY selected DESC, id ASC LIMIT 1")
    suspend fun preferredCode(couponId: String): CodeCandidateEntity?

    @Query("SELECT * FROM usage_events WHERE couponId = :couponId ORDER BY occurredAtEpochMillis")
    fun observeEvents(couponId: String): Flow<List<UsageEventEntity>>

    @Query("SELECT id FROM usage_events WHERE couponId = :couponId AND type = :type ORDER BY occurredAtEpochMillis DESC LIMIT 1")
    suspend fun latestEventId(couponId: String, type: String): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: UsageEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: PresentationSessionEntity)

    @Query("UPDATE presentation_sessions SET endedAtEpochMillis = :endedAt WHERE id = :sessionId")
    suspend fun endSession(sessionId: String, endedAt: Long)

    @Query("UPDATE coupons SET usageState = :state, updatedAtEpochMillis = :updatedAt WHERE id = :couponId")
    suspend fun setUsageState(couponId: String, state: String, updatedAt: Long)

    @Query("UPDATE coupons SET location = :location, updatedAtEpochMillis = :updatedAt WHERE id = :couponId")
    suspend fun setLocation(couponId: String, location: String, updatedAt: Long)

    @Transaction
    suspend fun recordEventOnce(event: UsageEventEntity, resultingState: String?): Boolean {
        if (insertEvent(event) == -1L) return false
        if (resultingState != null) setUsageState(event.couponId, resultingState, event.occurredAtEpochMillis)
        return true
    }
}

data class ImportWriteResult(val savedCouponIds: List<String>, val duplicates: Int)
