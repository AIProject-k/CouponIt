package com.couponit.app.recognition

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

data class ScanRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object ScanRegionPlanner {
    fun plan(width: Int, height: Int): List<ScanRegion> {
        val maxBandHeight = (width * 1.25f).toInt().coerceAtLeast(800)
        if (height <= maxBandHeight) return listOf(ScanRegion(0, 0, width, height))
        val step = (maxBandHeight * .72f).toInt()
        val result = mutableListOf<ScanRegion>()
        var top = 0
        while (top < height) {
            val bottom = (top + maxBandHeight).coerceAtMost(height)
            val adjustedTop = if (bottom == height) (height - maxBandHeight).coerceAtLeast(0) else top
            val region = ScanRegion(0, adjustedTop, width, bottom)
            if (result.lastOrNull() != region) result += region
            if (bottom == height) break
            top += step
        }
        return result
    }
}

class BarcodeRecognizer {
    suspend fun recognize(file: File): List<DetectedCode> {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path) ?: return emptyList()
        val scanner = BarcodeScanning.getClient()
        return try {
            for (region in ScanRegionPlanner.plan(bitmap.width, bitmap.height)) {
                val crop = android.graphics.Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
                val image = InputImage.fromBitmap(crop, 0)
                val detected = scanner.process(image).awaitResult().mapNotNull { barcode ->
                    val raw = barcode.rawValue ?: return@mapNotNull null
                    val box = barcode.boundingBox
                    DetectedCode(
                        format = barcode.format.toString(), rawValue = raw,
                        left = (box?.left ?: 0) + region.left,
                        top = (box?.top ?: 0) + region.top,
                        right = (box?.right ?: image.width) + region.left,
                        bottom = (box?.bottom ?: image.height) + region.top,
                    )
                }.distinctBy { it.format to it.rawValue }
                if (crop !== bitmap) crop.recycle()
                if (detected.isNotEmpty()) return detected
            }
            emptyList()
        } finally {
            scanner.close()
            bitmap.recycle()
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
}
