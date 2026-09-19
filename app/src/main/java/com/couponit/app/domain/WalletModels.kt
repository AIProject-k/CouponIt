package com.couponit.app.domain

import java.time.Instant
import java.time.LocalDate

enum class CouponType { EXCHANGE, AMOUNT, DISCOUNT, NUMBER, UNKNOWN }

/** 원본 이미지 좌표계의 사각형. 스크린샷 한 장에 쿠폰이 여러 개일 때 각 쿠폰의 영역을 가리킨다. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
enum class UsageState { UNUSED, PARTIAL, COMPLETED }
enum class CouponLocation { WALLET, ARCHIVED, TRASH }
enum class UsageEventType { REDEEM_MARKED, SPEND_RECORDED, BALANCE_SET, EVENT_REVERSED, NOTE_ADDED }

data class Coupon(
    val id: String,
    val title: String,
    val merchantName: String?,
    val issuerName: String? = null,
    val type: CouponType,
    val faceValueMinor: Long? = null,
    val currency: String = "KRW",
    val expiryDate: LocalDate? = null,
    val expiryConfirmed: Boolean = false,
    val favorite: Boolean = false,
    val usageState: UsageState = UsageState.UNUSED,
    val location: CouponLocation = CouponLocation.WALLET,
    val needsReview: Boolean = false,
    val originalAssetPath: String? = null,
    val codeValue: String? = null,
    val codeFormat: String? = null,
    /** 원본 이미지 id. 한 스크린샷에서 나온 쿠폰들이 같은 값을 가진다. */
    val assetId: String? = null,
    /** 원본 안에서 이 쿠폰이 차지하는 영역. null이면 이미지 전체가 이 쿠폰이다. */
    val crop: PixelRect? = null,
)

data class UsageEvent(
    val id: String,
    val couponId: String,
    val type: UsageEventType,
    val amountMinor: Long?,
    val currency: String,
    val occurredAt: Instant,
    val reversesEventId: String?,
    val operationId: String,
)

data class WalletSummary(
    val unused: Int,
    val expiringInSevenDays: Int,
    val needsReview: Int,
)
