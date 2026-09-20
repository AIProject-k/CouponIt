package com.couponit.app.importing

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.ImportCoupon
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.data.local.ImageAssetEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponType
import com.couponit.app.domain.PixelRect
import com.couponit.app.recognition.BarcodeRecognizer
import com.couponit.app.recognition.CouponSlice
import com.couponit.app.recognition.CouponTextFields
import com.couponit.app.recognition.CouponTextRecognizer
import com.couponit.app.recognition.CouponSplitter
import com.couponit.app.recognition.TextLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object ImportPolicy {
    fun isSupported(mime: String?): Boolean = mime == "image/jpeg" || mime == "image/png"
    fun privateFileName(assetId: String, mime: String): String =
        "asset-$assetId.${if (mime == "image/png") "png" else "jpg"}"
}

/**
 * 이미지 한 장에서 만들어진 결과.
 * couponId는 첫 번째로 저장한 쿠폰이며, 화면 안내는 개수를 쓴다.
 */
data class ImportResult(
    val couponId: String?,
    val error: String?,
    val textRecognitionFailed: Boolean = false,
    /** 이미지에서 찾은 쿠폰 칸 수 */
    val found: Int = 0,
    val saved: Int = 0,
    /** 같은 번호가 이미 있어 건너뛴 수 */
    val duplicates: Int = 0,
    val needsReview: Int = 0,
)

class CouponImporter(
    private val context: Context,
    private val repository: CouponRepository,
    private val recognizer: BarcodeRecognizer,
    private val textRecognizer: CouponTextRecognizer = CouponTextRecognizer(context),
) {
    /** 상세 화면 재인식. 쿠폰이 원본의 한 칸이면 그 칸의 글자만 쓴다. */
    suspend fun recognizeText(path: String, crop: PixelRect? = null): CouponTextFields =
        textRecognizer.recognize(File(path), crop)

    suspend fun import(uri: Uri): ImportResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        if (!ImportPolicy.isSupported(mime)) return ImportResult(null, "JPEG 또는 PNG 이미지만 추가할 수 있어요.")

        val assetId = UUID.randomUUID().toString()
        val originals = File(context.filesDir, "coupon-originals").apply { mkdirs() }
        val destination = File(originals, ImportPolicy.privateFileName(assetId, mime!!))
        var persisted = false
        return try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "이미지를 열 수 없어요." }
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "손상된 이미지예요." }

            val asset = ImageAssetEntity(assetId, "ORIGINAL", destination.path, sha256(destination), mime, bounds.outWidth, bounds.outHeight, destination.length())
            val codes = try {
                recognizer.recognize(destination)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            var textRecognitionFailed = false
            val lines = try {
                textRecognizer.recognizeLines(destination)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                textRecognitionFailed = true
                emptyList()
            }

            val slices = CouponSplitter.split(bounds.outWidth, bounds.outHeight, codes, lines)
            val prepared = if (slices.isEmpty()) {
                prepareWholeImage(destination, assetId, lines)
            } else {
                prepareSlices(destination, assetId, slices)
            }
            val write = repository.saveImport(asset, prepared)
            persisted = write.savedCouponIds.isNotEmpty()
            val result = ImportResult(
                couponId = write.savedCouponIds.firstOrNull(),
                error = null,
                textRecognitionFailed = textRecognitionFailed,
                found = prepared.size,
                saved = write.savedCouponIds.size,
                duplicates = write.duplicates,
                needsReview = prepared.count { it.coupon.id in write.savedCouponIds && it.coupon.needsReview },
            )
            if (result.saved == 0) {
                // 전부 중복이면 새 사본을 남기지 않는다.
                destination.delete()
            }
            result
        } catch (error: CancellationException) {
            // Room may commit immediately before cancellation is delivered. Query outside the
            // cancelled context so a committed coupon never loses its original file.
            val committed = persisted || withContext(NonCancellable) { repository.hasAsset(assetId) }
            if (!committed) destination.delete()
            throw error
        } catch (error: Exception) {
            if (!persisted) destination.delete()
            ImportResult(null, error.message ?: "이미지를 저장하지 못했어요.")
        }
    }

    /** 바코드를 찾지 못한 이미지. 통째로 한 개의 쿠폰으로 두고 사람이 확인한다. */
    private suspend fun prepareWholeImage(
        destination: File,
        assetId: String,
        lines: List<TextLine>,
    ): List<ImportCoupon> {
        val fields = textRecognizer.recognize(destination, initialLines = lines)
        val coupon = Coupon(
            id = UUID.randomUUID().toString(),
            title = fields.title ?: "정보 확인 필요",
            merchantName = fields.merchantName,
            type = CouponType.UNKNOWN,
            expiryDate = fields.expiryDate,
            expiryConfirmed = fields.expiryConfirmed,
            needsReview = true,
            originalAssetPath = destination.path,
            assetId = assetId,
        )
        return listOf(ImportCoupon(coupon, emptyList()))
    }

    private suspend fun prepareSlices(
        destination: File,
        assetId: String,
        slices: List<CouponSlice>,
    ): List<ImportCoupon> = slices.map { slice ->
        val fields = textRecognizer.recognize(destination, slice.region, slice.lines, slice.code)
        val couponId = UUID.randomUUID().toString()
        val needsReview = slice.uncertain || fields.needsReview
        val coupon = Coupon(
            id = couponId,
            title = fields.title ?: "정보 확인 필요",
            merchantName = fields.merchantName,
            type = CouponType.EXCHANGE,
            expiryDate = fields.expiryDate,
            expiryConfirmed = fields.expiryConfirmed && !slice.uncertain,
            needsReview = needsReview,
            originalAssetPath = destination.path,
            codeValue = slice.code.rawValue,
            codeFormat = slice.code.format,
            assetId = assetId,
            crop = slice.region,
        )
        ImportCoupon(
            coupon,
            listOf(
                CodeCandidateEntity(
                    id = "$couponId-0", couponId = couponId, format = slice.code.format, rawValue = slice.code.rawValue,
                    left = slice.code.left, top = slice.code.top, right = slice.code.right, bottom = slice.code.bottom,
                    selected = true, confirmed = false,
                ),
            ),
        )
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
