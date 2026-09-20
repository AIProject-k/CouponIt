package com.couponit.app.recognition

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.couponit.app.domain.PixelRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.sqrt

class CouponTextRecognizer(context: Context) {
    private val context = context.applicationContext

    /** 한 장에 여러 쿠폰이 있을 수 있어, 줄마다 위치를 함께 돌려준다. */
    suspend fun recognizeLines(file: File): List<TextLine> = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        read(image)
    }

    private suspend fun read(image: InputImage): List<TextLine> {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            // Wait for ML Kit to finish before closing the recognizer, including cancellation.
            val lines = suspendCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        continuation.resume(
                            result.textBlocks.flatMap { it.lines }
                                .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                                .map { line ->
                                    val box = line.boundingBox
                                    TextLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0)
                                },
                        )
                    }
                    .addOnFailureListener { continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.resumeWithException(kotlinx.coroutines.CancellationException("OCR cancelled")) }
            }
            kotlin.coroutines.coroutineContext.ensureActive()
            return lines
        } finally {
            recognizer.close()
        }
    }

    /** Keep whole-image OCR as evidence and retry only unresolved fields using a focused image. */
    suspend fun recognize(
        file: File,
        crop: PixelRect? = null,
        initialLines: List<TextLine>? = null,
        code: DetectedCode? = null,
    ): CouponTextFields = withContext(Dispatchers.IO) {
        val lines = initialLines ?: recognizeLines(file)
        val selected = CouponLayoutParser.select(lines, crop)
        val initial = CouponLayoutParser.parse(selected)
        val codeIssues = code?.let { CouponLayoutParser.codeIssues(selected, it) }.orEmpty()
        val tinyText = initial.evidence.values.flatten().any { it.bottom - it.top < 16 }
        val unfamiliarLayout = initial.evidence["title"].orEmpty().size > 1 ||
            (initial.title != null && selected.none { Regex("^(상품명|교환상품)\\s*[:：]?").containsMatchIn(it.text) }) ||
            (initial.merchantName != null && CouponTextParser.parse(selected.joinToString("\n") { it.text }).merchantName == null)
        if (!initial.needsReview && codeIssues.isEmpty() && !tinyText && !unfamiliarLayout) return@withContext initial
        try {
            val retry = retryLines(file, crop, selected)
            val result = CouponLayoutParser.reconcile(initial, CouponLayoutParser.parse(retry))
            val retryIssues = code?.let { CouponLayoutParser.codeIssues(retry, it) }.orEmpty()
            val layoutNote = buildList {
                if (unfamiliarLayout || initial.title == null && result.title != null)
                    add("추론하거나 추가 인식한 상품명은 원문과 비교해 주세요.")
                if (initial.merchantName == null && result.merchantName != null)
                    add("추가 인식한 사용처를 원문과 비교해 주세요.")
            }
            result.copy(reviewReasons = (result.reviewReasons + codeIssues + retryIssues + layoutNote).distinct())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            initial.copy(retried = true, reviewReasons = (initial.reviewReasons + codeIssues + "추가 인식을 완료하지 못했어요. 원본을 확인해 주세요.").distinct())
        }
    }

    private suspend fun retryLines(file: File, crop: PixelRect?, selected: List<TextLine>): List<TextLine> {
        val raw = requireNotNull(BitmapFactory.decodeFile(file.path)) { "이미지를 읽을 수 없어요." }
        val matrix = Matrix()
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setScale(-1f, 1f); matrix.postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setScale(-1f, 1f); matrix.postRotate(90f) }
        }
        val source = if (matrix.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        try {
            val region = crop ?: selected.takeIf { it.isNotEmpty() }?.let {
                PixelRect(it.minOf { line -> line.left } - 24, it.minOf { line -> line.top } - 24,
                    it.maxOf { line -> line.right } + 24, it.maxOf { line -> line.bottom } + 24)
            } ?: PixelRect(0, 0, source.width, source.height)
            val left = region.left.coerceIn(0, source.width - 1)
            val top = region.top.coerceIn(0, source.height - 1)
            val right = region.right.coerceIn(left + 1, source.width)
            val bottom = region.bottom.coerceIn(top + 1, source.height)
            val scale = min(2.0, sqrt(4_000_000.0 / ((right - left).toDouble() * (bottom - top)))).toFloat()
            val focused = createBitmap(((right - left) * scale).toInt().coerceAtLeast(1),
                ((bottom - top) * scale).toInt().coerceAtLeast(1))
            try {
                val canvas = Canvas(focused)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                }
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.scale(scale, scale)
                canvas.drawBitmap(source, -left.toFloat(), -top.toFloat(), paint)
                return read(InputImage.fromBitmap(focused, 0)).map {
                    TextLine(it.text, left + (it.left / scale).toInt(), top + (it.top / scale).toInt(),
                        left + (it.right / scale).toInt(), top + (it.bottom / scale).toInt())
                }
            } finally { focused.recycle() }
        } finally {
            if (source !== raw) source.recycle()
            raw.recycle()
        }
    }
}
