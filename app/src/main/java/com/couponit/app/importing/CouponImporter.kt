package com.couponit.app.importing

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.data.local.ImageAssetEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponType
import com.couponit.app.recognition.BarcodeRecognizer
import com.couponit.app.recognition.CouponTextFields
import com.couponit.app.recognition.CouponTextRecognizer
import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object ImportPolicy {
    fun isSupported(mime: String?): Boolean = mime == "image/jpeg" || mime == "image/png"
    fun privateFileName(assetId: String, mime: String): String =
        "asset-$assetId.${if (mime == "image/png") "png" else "jpg"}"
}

data class ImportResult(val couponId: String?, val error: String?, val textRecognitionFailed: Boolean = false)

class CouponImporter(
    private val context: Context,
    private val repository: CouponRepository,
    private val recognizer: BarcodeRecognizer,
    private val textRecognizer: CouponTextRecognizer = CouponTextRecognizer(context),
) {
    suspend fun recognizeText(path: String): CouponTextFields = textRecognizer.recognize(File(path))

    suspend fun import(uri: Uri): ImportResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        if (!ImportPolicy.isSupported(mime)) return ImportResult(null, "JPEG 또는 PNG 이미지만 추가할 수 있어요.")

        val couponId = UUID.randomUUID().toString()
        val assetId = UUID.randomUUID().toString()
        val originals = File(context.filesDir, "coupon-originals").apply { mkdirs() }
        val destination = File(originals, ImportPolicy.privateFileName(assetId, mime!!))
        return try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "이미지를 열 수 없어요." }
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "손상된 이미지예요." }
            val sha256 = destination.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }

            var coupon = Coupon(couponId, "정보 확인 필요", null, type = CouponType.UNKNOWN, needsReview = true, originalAssetPath = destination.path)
            repository.save(coupon)
            repository.saveAsset(ImageAssetEntity(assetId, couponId, "ORIGINAL", destination.path, sha256, mime, bounds.outWidth, bounds.outHeight, destination.length()))

            val codes = recognizer.recognize(destination)
            repository.saveCandidates(codes.mapIndexed { index, code ->
                CodeCandidateEntity(
                    id = "$assetId-$index", couponId = couponId, format = code.format,
                    rawValue = code.rawValue, left = code.left, top = code.top, right = code.right,
                    bottom = code.bottom, selected = codes.size == 1, confirmed = false,
                )
            })
            if (codes.size == 1) {
                coupon = coupon.copy(type = CouponType.EXCHANGE, codeValue = codes.first().rawValue, codeFormat = codes.first().format)
                repository.save(coupon)
            }
            var textRecognitionFailed = false
            val fields = try {
                recognizeText(destination.path)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                textRecognitionFailed = true
                CouponTextFields()
            }
            repository.save(coupon.copy(
                title = fields.title ?: coupon.title,
                merchantName = fields.merchantName,
                expiryDate = fields.expiryDate,
                expiryConfirmed = fields.expiryConfirmed,
                needsReview = fields.title == null || fields.merchantName == null || !fields.expiryConfirmed || coupon.codeValue == null,
            ))
            ImportResult(couponId, null, textRecognitionFailed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            destination.delete()
            ImportResult(null, error.message ?: "이미지를 저장하지 못했어요.")
        }
    }
}
