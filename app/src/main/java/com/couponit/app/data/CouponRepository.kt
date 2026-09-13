package com.couponit.app.data

import com.couponit.app.data.local.CouponDao
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.data.local.ImageAssetEntity
import com.couponit.app.data.local.PresentationSessionEntity
import com.couponit.app.data.local.UsageEventEntity
import com.couponit.app.data.local.toDomain
import com.couponit.app.data.local.toEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponLocation
import com.couponit.app.domain.UsageEvent
import com.couponit.app.domain.UsageEventType
import com.couponit.app.domain.UsageState
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CouponRepository(private val dao: CouponDao) {
    val coupons: Flow<List<Coupon>> = dao.observeCoupons().map { list -> list.map { it.toDomain() } }

    fun events(couponId: String): Flow<List<UsageEvent>> =
        dao.observeEvents(couponId).map { list -> list.map { it.toDomain() } }

    suspend fun coupon(id: String): Coupon? = dao.coupon(id)?.toDomain()
    suspend fun save(coupon: Coupon) = dao.upsertCoupon(coupon.toEntity())
    suspend fun saveAsset(asset: ImageAssetEntity) = dao.insertAsset(asset)
    suspend fun saveCandidates(candidates: List<CodeCandidateEntity>) = dao.upsertCandidates(candidates)
    suspend fun preferredCode(couponId: String) = dao.preferredCode(couponId)

    suspend fun setLocation(id: String, location: CouponLocation) =
        dao.setLocation(id, location.name, System.currentTimeMillis())

    suspend fun startPresentation(couponId: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertSession(PresentationSessionEntity(id, couponId, null, null, System.currentTimeMillis(), null))
        return id
    }

    suspend fun endPresentation(sessionId: String) = dao.endSession(sessionId, System.currentTimeMillis())

    suspend fun record(
        couponId: String,
        type: UsageEventType,
        amountMinor: Long? = null,
        reversesEventId: String? = null,
        operationId: String = UUID.randomUUID().toString(),
    ): Boolean {
        val now = Instant.now()
        val event = UsageEventEntity(
            id = UUID.randomUUID().toString(), couponId = couponId, type = type.name,
            amountMinor = amountMinor, currency = "KRW", occurredAtEpochMillis = now.toEpochMilli(),
            reversesEventId = reversesEventId, operationId = operationId,
        )
        val state = when (type) {
            UsageEventType.REDEEM_MARKED -> UsageState.COMPLETED.name
            UsageEventType.SPEND_RECORDED -> UsageState.PARTIAL.name
            UsageEventType.EVENT_REVERSED -> UsageState.UNUSED.name
            else -> null
        }
        return dao.recordEventOnce(event, state)
    }
}
