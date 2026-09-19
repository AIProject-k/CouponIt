package com.couponit.app.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.data.local.MIGRATION_1_2
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val databaseName = "migration-1-2-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CouponDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test @Throws(IOException::class)
    fun preservesCouponAndConnectsItsOriginalAsset() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
        helper.createDatabase(databaseName, 1).apply {
            insert("coupons", 0, couponValues())
            insert("image_assets", 0, assetValues())
            close()
        }

        val migrated = helper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2)
        migrated.query("SELECT title, assetId, cropLeft FROM coupons WHERE id = 'coupon-old'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("기존 쿠폰", cursor.getString(0))
            assertEquals("asset-old", cursor.getString(1))
            assertEquals(true, cursor.isNull(2))
        }
        migrated.query("SELECT path FROM image_assets WHERE id = 'asset-old'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("/private/old.png", cursor.getString(0))
        }
        migrated.close()
    }

    private fun couponValues() = ContentValues().apply {
        put("id", "coupon-old"); put("title", "기존 쿠폰"); putNull("merchantName"); putNull("issuerName")
        put("type", "UNKNOWN"); putNull("faceValueMinor"); put("currency", "KRW"); putNull("expiryDate")
        put("expiryConfirmed", 0); put("favorite", 0); put("usageState", "UNUSED"); put("location", "WALLET")
        put("needsReview", 1); put("originalAssetPath", "/private/old.png"); putNull("codeValue"); putNull("codeFormat")
        put("createdAtEpochMillis", 1L); put("updatedAtEpochMillis", 1L)
    }

    private fun assetValues() = ContentValues().apply {
        put("id", "asset-old"); put("couponId", "coupon-old"); put("kind", "ORIGINAL"); put("path", "/private/old.png")
        put("sha256", "old-hash"); put("mime", "image/png"); put("width", 100); put("height", 200); put("bytes", 10L)
    }
}
