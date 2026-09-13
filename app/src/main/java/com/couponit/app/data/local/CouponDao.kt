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

    @Upsert
    suspend fun upsertCoupon(coupon: CouponEntity)

    @Update
    suspend fun updateCoupon(coupon: CouponEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(asset: ImageAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandidates(candidates: List<CodeCandidateEntity>)

    @Query("SELECT * FROM code_candidates WHERE couponId = :couponId ORDER BY selected DESC, id ASC LIMIT 1")
    suspend fun preferredCode(couponId: String): CodeCandidateEntity?

    @Query("SELECT * FROM usage_events WHERE couponId = :couponId ORDER BY occurredAtEpochMillis")
    fun observeEvents(couponId: String): Flow<List<UsageEventEntity>>

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
