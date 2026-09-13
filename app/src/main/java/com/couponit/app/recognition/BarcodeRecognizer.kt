package com.couponit.app.recognition

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class DetectedCode(
    val format: String,
    val rawValue: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

class BarcodeRecognizer(private val context: Context) {
    suspend fun recognize(file: File): List<DetectedCode> {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        return BarcodeScanning.getClient().process(image).awaitResult().mapNotNull { barcode ->
            val raw = barcode.rawValue ?: return@mapNotNull null
            val box = barcode.boundingBox
            DetectedCode(
                format = barcode.format.toString(), rawValue = raw,
                left = box?.left ?: 0, top = box?.top ?: 0,
                right = box?.right ?: image.width, bottom = box?.bottom ?: image.height,
            )
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
}
