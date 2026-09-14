package com.couponit.app.recognition

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.importing.CouponImporter
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 쿠폰 이미지가 왜 등록되지 않는지 기기에서 단계별로 확인하는 진단용 테스트.
 * `-e diagCouponPath <앱 캐시 이미지 경로>`를 줄 때만 실행된다. 쿠폰 번호는 로그에서 가린다.
 */
class CouponImportDiagnosticTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(message: String) = Log.i(TAG, message)
    private fun maskCode(value: String) = value.replace(Regex("\\d"), "#")
    private fun maskLine(line: String) =
        line.replace(Regex("\\d{4}\\s*[-–]\\s*\\d{4}(\\s*[-–]\\s*\\d{4})*"), "####-####-####")

    @Test fun diagnoseRealCoupon(): Unit = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("diagCouponPath")
        assumeTrue("Local private fixture is optional", path != null)
        val file = File(path!!)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        log("image ${bounds.outWidth}x${bounds.outHeight} mime=${bounds.outMimeType} bytes=${file.length()}")

        val codes = runCatching { BarcodeRecognizer().recognize(file) }
        log("barcode " + codes.fold(
            { list -> "count=${list.size} " + list.joinToString { "format=${it.format} len=${it.rawValue.length} value=${maskCode(it.rawValue)}" } },
            { "EXCEPTION ${it::class.java.simpleName}: ${it.message}" },
        ))

        val client = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val text = try {
            val result = Tasks.await(client.process(InputImage.fromFilePath(context, Uri.fromFile(file))))
            result.textBlocks.flatMap { it.lines }
                .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                .joinToString("\n") { it.text }
        } finally {
            client.close()
        }
        text.lines().forEach { log("ocr| ${maskLine(it)}") }
        log("parsed ${CouponTextParser.parse(text)}")

        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "diag-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, bounds.outMimeType ?: "image/jpeg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        try {
            resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            log("resolver mime=${resolver.getType(uri)}")
            val result = CouponImporter(context, repository, BarcodeRecognizer()).import(uri)
            log("import error=${result.error} textRecognitionFailed=${result.textRecognitionFailed} saved=${result.couponId != null}")
            result.couponId?.let { id ->
                val coupon = repository.coupon(id)!!
                log("coupon title=${coupon.title} merchant=${coupon.merchantName} expiry=${coupon.expiryDate} " +
                    "type=${coupon.type} needsReview=${coupon.needsReview} code=${coupon.codeValue?.let(::maskCode)} format=${coupon.codeFormat}")
                coupon.originalAssetPath?.let { File(it).delete() }
            }
        } finally {
            resolver.delete(uri, null, null)
            database.close()
        }
    }

    private companion object {
        const val TAG = "CouponDiag"
    }
}
