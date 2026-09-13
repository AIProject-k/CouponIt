package com.couponit.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponLocation
import com.couponit.app.domain.CouponType
import com.couponit.app.domain.UsageEvent
import com.couponit.app.domain.UsageEventType
import com.couponit.app.domain.UsageState
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "coupons")
data class CouponEntity(
    @PrimaryKey val id: String,
    val title: String,
    val merchantName: String?,
    val issuerName: String?,
    val type: String,
    val faceValueMinor: Long?,
    val currency: String,
    val expiryDate: String?,
    val expiryConfirmed: Boolean,
    val favorite: Boolean,
    val usageState: String,
    val location: String,
    val needsReview: Boolean,
    val originalAssetPath: String?,
    val codeValue: String?,
    val codeFormat: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "image_assets",
    foreignKeys = [ForeignKey(entity = CouponEntity::class, parentColumns = ["id"], childColumns = ["couponId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("couponId"), Index(value = ["sha256"])],
)
data class ImageAssetEntity(
    @PrimaryKey val id: String,
    val couponId: String,
    val kind: String,
    val path: String,
    val sha256: String,
    val mime: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
)

@Entity(
    tableName = "code_candidates",
    foreignKeys = [ForeignKey(entity = CouponEntity::class, parentColumns = ["id"], childColumns = ["couponId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("couponId")],
)
data class CodeCandidateEntity(
    @PrimaryKey val id: String,
    val couponId: String,
    val format: String,
    val rawValue: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val selected: Boolean,
    val confirmed: Boolean,
)

@Entity(
    tableName = "usage_events",
    foreignKeys = [ForeignKey(entity = CouponEntity::class, parentColumns = ["id"], childColumns = ["couponId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("couponId"), Index(value = ["operationId"], unique = true)],
)
data class UsageEventEntity(
    @PrimaryKey val id: String,
    val couponId: String,
    val type: String,
    val amountMinor: Long?,
    val currency: String,
    val occurredAtEpochMillis: Long,
    val reversesEventId: String?,
    val operationId: String,
)

@Entity(
    tableName = "presentation_sessions",
    foreignKeys = [ForeignKey(entity = CouponEntity::class, parentColumns = ["id"], childColumns = ["couponId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("couponId")],
)
data class PresentationSessionEntity(
    @PrimaryKey val id: String,
    val couponId: String,
    val assetId: String?,
    val codeCandidateId: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)

fun Coupon.toEntity(now: Long = System.currentTimeMillis()) = CouponEntity(
    id, title, merchantName, issuerName, type.name, faceValueMinor, currency,
    expiryDate?.toString(), expiryConfirmed, favorite, usageState.name, location.name,
    needsReview, originalAssetPath, codeValue, codeFormat, now, now,
)

fun CouponEntity.toDomain() = Coupon(
    id = id,
    title = title,
    merchantName = merchantName,
    issuerName = issuerName,
    type = enumValueOr(type, CouponType.UNKNOWN),
    faceValueMinor = faceValueMinor,
    currency = currency,
    expiryDate = expiryDate?.let(LocalDate::parse),
    expiryConfirmed = expiryConfirmed,
    favorite = favorite,
    usageState = enumValueOr(usageState, UsageState.UNUSED),
    location = enumValueOr(location, CouponLocation.WALLET),
    needsReview = needsReview,
    originalAssetPath = originalAssetPath,
    codeValue = codeValue,
    codeFormat = codeFormat,
)

fun UsageEventEntity.toDomain() = UsageEvent(
    id, couponId, enumValueOr(type, UsageEventType.NOTE_ADDED), amountMinor, currency,
    Instant.ofEpochMilli(occurredAtEpochMillis), reversesEventId, operationId,
)

private inline fun <reified T : Enum<T>> enumValueOr(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
