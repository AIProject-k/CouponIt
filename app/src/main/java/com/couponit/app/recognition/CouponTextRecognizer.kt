package com.couponit.app.recognition

import android.content.Context
import android.net.Uri
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

class CouponTextRecognizer(context: Context) {
    private val context = context.applicationContext

    /** 한 장에 여러 쿠폰이 있을 수 있어, 줄마다 위치를 함께 돌려준다. */
    suspend fun recognizeLines(file: File): List<TextLine> = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
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
            ensureActive()
            lines
        } finally {
            recognizer.close()
        }
    }

    /**
     * 상세 화면의 재인식. crop을 주면 그 칸의 글자만 쓴다.
     * 잘라낸 이미지를 새로 읽지 않고 원본을 읽어 좌표로 거른다. 잘라서 읽으면 글자 묶음이 달라져
     * 가져올 때와 다른 결과가 나오기 때문이다(첫 칸의 종료일을 놓치는 사례 확인).
     */
    suspend fun recognize(file: File, crop: PixelRect? = null): CouponTextFields {
        val lines = recognizeLines(file)
        val selected = if (crop == null) lines else lines.filter { it.centerY in crop.top until crop.bottom }
        return CouponTextParser.parse(selected.joinToString("\n") { it.text })
    }
}
