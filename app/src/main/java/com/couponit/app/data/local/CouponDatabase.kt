package com.couponit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CouponEntity::class, ImageAssetEntity::class, CodeCandidateEntity::class, UsageEventEntity::class, PresentationSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CouponDatabase : RoomDatabase() {
    abstract fun couponDao(): CouponDao
}
