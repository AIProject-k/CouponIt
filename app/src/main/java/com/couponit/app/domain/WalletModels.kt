package com.couponit.app.domain

import java.time.Instant
import java.time.LocalDate

enum class CouponType { EXCHANGE, AMOUNT, DISCOUNT, NUMBER, UNKNOWN }
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
