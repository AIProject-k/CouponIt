package com.couponit.app.recognition

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.importing.CouponImporter
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CouponRecognitionCorpusTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun compact(text: String?) = text?.replace(Regex("\\s+"), "")

    /** Explicit corpus gate: a missing private fixture FAILS instead of silently skipping. */
    @Test fun requiredGmarketImageImportsCompleteProduct(): Unit = runBlocking {
        val path = requireNotNull(InstrumentationRegistry.getArguments().getString("gmarketPath")) { "gmarketPath fixture is required for the corpus gate" }
        val file = File(path)
        assertTrue("Required corpus fixture missing", file.isFile)
        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "corpus-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        try {
            resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            val importer = CouponImporter(context, repository, BarcodeRecognizer())
            val imported = importer.import(uri)
            assertNull(imported.error)
            assertEquals(1, imported.saved)
            val coupon = repository.coupon(imported.couponId!!)!!
            assertEquals("버거킹", coupon.merchantName)
            assertEquals(LocalDate.of(2026, 9, 29), coupon.expiryDate)
            assertEquals("와퍼주니어+콜라R+쉐이킹프라이스윗어니언+21치즈스틱", compact(coupon.title))
            assertNotNull(coupon.codeValue)
            val again = importer.recognizeText(coupon.originalAssetPath!!, coupon.crop)
            assertEquals(compact(coupon.title), compact(again.title))
            assertTrue(again.evidence["title"].orEmpty().size >= 3)
            assertEquals(1, importer.import(uri).duplicates)
        } finally {
            repository.coupons.first().mapNotNull { it.originalAssetPath }.distinct().forEach { File(it).delete() }
            resolver.delete(uri, null, null)
            database.close()
        }
    }

    @Test fun generatedUnlistedBrandsAndLayouts(): Unit = runBlocking {
        val brands = listOf("구름식당", "달빛상점", "별숲카페")
        var exact = 0
        var flagged = 0
        for ((index, brand) in brands.withIndex()) for (scale in listOf(1f, .65f)) {
            val bitmap = Bitmap.createBitmap((1100 * scale).toInt(), (1000 * scale).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(if (index == 1) Color.rgb(238, 246, 234) else Color.WHITE)
            canvas.scale(scale, scale)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 46f }
            val lines = if (index == 1) listOf("사용처: $brand", "상품명: 샌드위치 세트", "+음료 1개", "유효기간: 2027.01.15")
                else listOf(brand, "($brand) 샌드위치 세트", "+음료 1개", "2027.01.15", "까지 사용 가능")
            lines.forEachIndexed { n, line -> canvas.drawText(line, 90f + index * 20, 120f + n * 65, paint) }
            val file = File.createTempFile("generated-layout-", ".jpg", context.cacheDir)
            try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, if (scale < 1) 60 else 95, it) }
                val fields = CouponTextRecognizer(context).recognize(file)
                assertEquals("layout=$index scale=$scale", brand, fields.merchantName)
                // Measure exact extraction separately from review routing. Never call a flagged mismatch accurate.
                if (compact(fields.title) == "샌드위치세트+음료1개") exact++ else flagged++
                assertTrue("layout=$index scale=$scale title=${fields.title} reasons=${fields.reviewReasons}",
                    compact(fields.title) == "샌드위치세트+음료1개" ||
                        (fields.needsReview && fields.reviewReasons.any { it.contains("상품명") }))
                assertEquals(LocalDate.of(2027, 1, 15), fields.expiryDate)
            } finally { bitmap.recycle(); file.delete() }
        }
        android.util.Log.i("CouponCorpus", "generated total=6 exact=$exact mismatchesRoutedToReview=$flagged")
    }
}
