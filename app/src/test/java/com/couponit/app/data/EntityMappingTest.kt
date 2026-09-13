package com.couponit.app.data

import com.couponit.app.data.local.CouponEntity
import com.couponit.app.data.local.toDomain
import com.couponit.app.data.local.toEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponLocation
import com.couponit.app.domain.CouponType
import com.couponit.app.domain.UsageState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTest {
    @Test
    fun `coupon mapping keeps independent status axes and leading zero code`() {
        val domain = Coupon(
            id = "coupon-1",
            title = "아메리카노",
            merchantName = "가상카페",
            issuerName = "발행사",
            type = CouponType.AMOUNT,
            faceValueMinor = 10_000,
            expiryDate = LocalDate.of(2026, 12, 31),
            expiryConfirmed = true,
            usageState = UsageState.PARTIAL,
            location = CouponLocation.WALLET,
            needsReview = false,
            originalAssetPath = "originals/coupon-1.jpg",
            codeValue = "0012-3400",
            codeFormat = "CODE_128",
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `unknown enum persisted by newer app degrades to safe values`() {
        val entity = CouponEntity(
            id = "future",
            title = "미확인",
            merchantName = null,
            issuerName = null,
            type = "FUTURE_TYPE",
            faceValueMinor = null,
            currency = "KRW",
            expiryDate = null,
            expiryConfirmed = false,
            favorite = false,
            usageState = "FUTURE_STATE",
            location = "FUTURE_LOCATION",
            needsReview = true,
            originalAssetPath = null,
            codeValue = null,
            codeFormat = null,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        assertEquals(CouponType.UNKNOWN, entity.toDomain().type)
        assertEquals(UsageState.UNUSED, entity.toDomain().usageState)
        assertEquals(CouponLocation.WALLET, entity.toDomain().location)
    }
}
