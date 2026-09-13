package com.couponit.app.recognition

import android.content.Context
import android.net.Uri
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

    suspend fun recognize(file: File): CouponTextFields = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            // Wait for ML Kit to finish before closing the recognizer, including cancellation.
            val text = suspendCoroutine<String> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val lines = result.textBlocks.flatMap { it.lines }
                            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        continuation.resume(lines.joinToString("\n") { it.text })
                    }
                    .addOnFailureListener { continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.resumeWithException(kotlinx.coroutines.CancellationException("OCR cancelled")) }
            }
            ensureActive()
            CouponTextParser.parse(text)
        } finally {
            recognizer.close()
        }
    }
}
