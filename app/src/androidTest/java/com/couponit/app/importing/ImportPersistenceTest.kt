package com.couponit.app.importing

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.ImportCoupon
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.data.local.ImageAssetEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun candidateFailureRollsBackCouponAndOriginalAsset(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val asset = asset("asset-failed")
        val coupon = coupon("coupon-failed", "code-failed", asset.id)
        try {
            val failure = runCatching {
                repository.saveImport(
                    asset,
                    listOf(
                        ImportCoupon(
                            coupon,
                            listOf(
                                candidate("candidate-failed", coupon.id, coupon.codeValue!!),
                                candidate("candidate-failed", coupon.id, coupon.codeValue!!),
                            ),
                        ),
                    ),
                )
            }

            assertTrue("candidate failure must escape the transaction", failure.isFailure)
            assertEquals(emptyList<Coupon>(), repository.coupons.first())
            assertNull(database.couponDao().asset(asset.id))
        } finally {
            database.close()
        }
    }

    @Test fun duplicateCodesAreSkippedInsideTheSameAtomicImport(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val first = coupon("coupon-first", "same-code", "asset-duplicates")
        val second = coupon("coupon-second", "same-code", "asset-duplicates")
        try {
            val result = repository.saveImport(
                asset("asset-duplicates"),
                listOf(
                    ImportCoupon(first, listOf(candidate("candidate-first", first.id, first.codeValue!!))),
                    ImportCoupon(second, listOf(candidate("candidate-second", second.id, second.codeValue!!))),
                ),
            )

            assertEquals(listOf(first.id), result.savedCouponIds)
            assertEquals(1, result.duplicates)
            assertEquals(listOf(first.id), repository.coupons.first().map { it.id })
        } finally {
            database.close()
        }
    }

    private fun asset(id: String) = ImageAssetEntity(id, "ORIGINAL", "/private/$id.png", "hash-$id", "image/png", 100, 200, 10)

    private fun coupon(id: String, code: String, assetId: String) = Coupon(
        id = id,
        title = id,
        merchantName = "test",
        type = CouponType.EXCHANGE,
        codeValue = code,
        codeFormat = "CODE_128",
        originalAssetPath = "/private/$assetId.png",
        assetId = assetId,
    )

    private fun candidate(id: String, couponId: String, code: String) = CodeCandidateEntity(
        id, couponId, "CODE_128", code, 1, 2, 30, 40, selected = true, confirmed = false,
    )
}
