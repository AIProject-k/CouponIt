package com.couponit.app.recognition

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.importing.CouponImporter
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 쿠폰 이미지가 왜 그렇게 인식되는지 기기에서 단계별로 확인하는 진단용 테스트.
 * `-e diagCouponPath <앱 캐시 이미지 경로>`를 줄 때만 실행된다. 쿠폰 번호는 로그에서 가린다.
 */
class CouponImportDiagnosticTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(message: String) = Log.i(TAG, message)
    private fun maskCode(value: String) = value.replace(Regex("\\d"), "#")
    private fun maskLine(line: String) =
        line.replace(Regex("\\d{4}\\s*[-–]?\\s*\\d{4}\\s*[-–]?\\s*\\d{4}"), "####-####-####")

    @Test fun diagnoseRealCoupon(): Unit = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("diagCouponPath")
        assumeTrue("Local private fixture is optional", path != null)
        val file = File(path!!)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        log("image ${bounds.outWidth}x${bounds.outHeight} mime=${bounds.outMimeType} bytes=${file.length()}")

        val codes = runCatching { BarcodeRecognizer().recognize(file) }
        log("barcode " + codes.fold(
            { list -> "count=${list.size} " + list.joinToString { "format=${it.format} rect=(${it.left},${it.top},${it.right},${it.bottom}) value=${maskCode(it.rawValue)}" } },
            { "EXCEPTION ${it::class.java.simpleName}: ${it.message}" },
        ))

        val lines = CouponTextRecognizer(context).recognizeLines(file)
        lines.forEach { log("ocr| y=${it.top}..${it.bottom} ${maskLine(it.text)}") }

        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val importer = CouponImporter(context, repository, BarcodeRecognizer())
        try {
            val slices = CouponSplitter.split(bounds.outWidth, bounds.outHeight, codes.getOrDefault(emptyList()), lines)
            log("slices=${slices.size}")
            slices.forEachIndexed { index, slice ->
                log("slice $index region=${slice.region} uncertain=${slice.uncertain}")
                slice.lines.forEach { log("  keep| ${maskLine(it.text)}") }
                log("  parsed(import) ${CouponTextParser.parse(slice.text)}")
                // 상세 화면 재인식과 같은 경로: 영역만 잘라 다시 읽는다.
                val again = importer.recognizeText(file.path, slice.region)
                log("  parsed(recrop) $again")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "diag-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, bounds.outMimeType ?: "image/png")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            })!!
            try {
                resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                val result = importer.import(uri)
                log("import error=${result.error} textRecognitionFailed=${result.textRecognitionFailed} found=${result.found} saved=${result.saved} duplicates=${result.duplicates} needsReview=${result.needsReview}")
                repository.coupons.first().forEach { coupon ->
                    log("coupon title=${coupon.title} merchant=${coupon.merchantName} expiry=${coupon.expiryDate} " +
                        "needsReview=${coupon.needsReview} crop=${coupon.crop} code=${coupon.codeValue?.let(::maskCode)}")
                    coupon.originalAssetPath?.let { File(it).delete() }
                }
            } finally {
                resolver.delete(uri, null, null)
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TAG = "CouponDiag"
    }
}
