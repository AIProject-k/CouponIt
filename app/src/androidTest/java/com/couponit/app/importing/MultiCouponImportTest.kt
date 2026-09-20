package com.couponit.app.importing

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.recognition.BarcodeRecognizer
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * 스크린샷 여러 장에 쿠폰이 여러 개 있는 실제 사례.
 * `-e multiCouponPaths <앱 캐시 경로1>,<앱 캐시 경로2>` 를 줄 때만 실행한다.
 * 실제 이미지와 쿠폰 번호는 저장소에 넣지 않고 로그로도 남기지 않는다.
 */
class MultiCouponImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun splitsEachScreenshotIntoOneCouponPerBarcode(): Unit = runBlocking {
        val paths = InstrumentationRegistry.getArguments().getString("multiCouponPaths")
        assumeTrue("Local private fixtures are optional", paths != null)
        val files = paths!!.split(",").map { File(it.trim()) }
        assumeTrue("Fixtures must exist", files.all { it.isFile })

        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val importer = CouponImporter(context, repository, BarcodeRecognizer())
        val uris = mutableListOf<Uri>()
        try {
            val first = files.map { file -> importer.import(publish(file).also(uris::add)) }
            assertTrue("모든 이미지가 저장되어야 한다", first.all { it.error == null })
            assertEquals("두 장에서 쿠폰 5개", 5, first.sumOf { it.saved })
            assertEquals(0, first.sumOf { it.duplicates })

            val coupons = repository.coupons.first()
            assertEquals(5, coupons.size)
            assertEquals("번호는 모두 달라야 한다", 5, coupons.mapNotNull { it.codeValue }.distinct().size)
            assertTrue("쿠폰마다 원본 안의 제 영역을 가리킨다", coupons.all { it.crop != null })
            assertEquals("원본은 장수만큼만", files.size, coupons.mapNotNull { it.assetId }.distinct().size)
            var exactTitles = 0
            coupons.forEachIndexed { index, coupon ->
                assertEquals(LocalDate.of(2026, 12, 21), coupon.expiryDate)
                assertEquals("slice=$index crop=${coupon.crop}", "메가MGC커피", coupon.merchantName)
                val exactTitle = coupon.title.replace(Regex("\\s+"), "") == "메가MGC커피저당골든애플블랙티"
                if (exactTitle) exactTitles++
                assertTrue("잘못 읽은 상품명을 확정하지 않는다", exactTitle || coupon.needsReview)
                val recognizedAgain = importer.recognizeText(coupon.originalAssetPath!!, coupon.crop)
                assertEquals("재인식은 slice=$index 영역 밖의 날짜를 읽으면 안 된다", coupon.expiryDate, recognizedAgain.expiryDate)
            }
            android.util.Log.i("CouponCorpus", "multi total=5 exactTitles=$exactTitles mismatchesRoutedToReview=${5 - exactTitles}")

            // 같은 화면을 다시 담아도 쿠폰이 늘지 않는다.
            val again = files.map { file -> importer.import(publish(file).also(uris::add)) }
            assertEquals(0, again.sumOf { it.saved })
            assertEquals(5, again.sumOf { it.duplicates })
            assertEquals(5, repository.coupons.first().size)
        } finally {
            uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
            runBlocking { repository.coupons.first() }.mapNotNull { it.originalAssetPath }.distinct()
                .forEach { File(it).delete() }
            database.close()
        }
    }

    private fun publish(file: File): Uri {
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "multi-${System.nanoTime()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        )!!
        resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
}
