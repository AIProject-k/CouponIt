package com.couponit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CouponEntity::class, ImageAssetEntity::class, CodeCandidateEntity::class, UsageEventEntity::class, PresentationSessionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CouponDatabase : RoomDatabase() {
    abstract fun couponDao(): CouponDao
}

/**
 * 스크린샷 한 장에서 쿠폰 여러 개를 만들 수 있게 바꾼다.
 * 쿠폰이 원본 id와 잘라낼 영역을 갖고, 원본은 더 이상 쿠폰에 종속되지 않는다.
 * 기존 쿠폰은 원본 전체가 쿠폰 한 개이므로 영역은 비워 둔다.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE coupons ADD COLUMN assetId TEXT")
        db.execSQL("ALTER TABLE coupons ADD COLUMN cropLeft INTEGER")
        db.execSQL("ALTER TABLE coupons ADD COLUMN cropTop INTEGER")
        db.execSQL("ALTER TABLE coupons ADD COLUMN cropRight INTEGER")
        db.execSQL("ALTER TABLE coupons ADD COLUMN cropBottom INTEGER")
        db.execSQL(
            "UPDATE coupons SET assetId = (SELECT id FROM image_assets WHERE image_assets.couponId = coupons.id AND kind = 'ORIGINAL' LIMIT 1)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS image_assets_new (" +
                "`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `path` TEXT NOT NULL, `sha256` TEXT NOT NULL, " +
                "`mime` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `bytes` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT OR REPLACE INTO image_assets_new (id, kind, path, sha256, mime, width, height, bytes) " +
                "SELECT id, kind, path, sha256, mime, width, height, bytes FROM image_assets",
        )
        db.execSQL("DROP TABLE image_assets")
        db.execSQL("ALTER TABLE image_assets_new RENAME TO image_assets")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_assets_sha256` ON `image_assets` (`sha256`)")
    }
}
