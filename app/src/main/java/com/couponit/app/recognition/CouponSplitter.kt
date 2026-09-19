package com.couponit.app.recognition

import com.couponit.app.domain.PixelRect

/** OCR 한 줄과 그 위치. 어느 쿠폰 칸에 속한 글자인지 알려면 좌표가 필요하다. */
data class TextLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
}

/** 한 장의 이미지에서 잘라낸 쿠폰 한 개. */
data class CouponSlice(
    val code: DetectedCode,
    val region: PixelRect,
    val lines: List<TextLine>,
    /** 칸 경계가 애매해 사람이 확인해야 하는 경우 */
    val uncertain: Boolean = false,
) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

/**
 * 스크린샷 한 장에 여러 쿠폰이 있을 때 바코드 위치를 기준으로 칸을 나눈다.
 * 같은 높이로 자르지 않고, 바코드 사이 간격에서 경계를 잡는다.
 * 바코드가 없는 잘린 칸은 쿠폰으로 만들지 않는다(여기서 제외됨).
 */
object CouponSplitter {
    /** 바코드 간격 대비 이 비율보다 가까우면 한 칸에 코드가 둘일 수 있다고 본다. */
    private const val CLOSE_RATIO = 0.35f
    private const val ABOVE_RATIO = 0.75f
    private const val BELOW_RATIO = 0.5f
    private val eventTitle = Regex("^\\s*\\[(?:EVENT|이벤트)]", RegexOption.IGNORE_CASE)

    fun split(imageWidth: Int, imageHeight: Int, codes: List<DetectedCode>, lines: List<TextLine>): List<CouponSlice> {
        // Overlapping scan bands can report the same visual code more than once, occasionally with
        // a different format classification. Repository identity is the raw value, so merge it here too.
        val sorted = codes.sortedBy { it.top }.distinctBy { it.rawValue }
        if (sorted.isEmpty()) return emptyList()
        if (sorted.size == 1) {
            val code = sorted.single()
            val hasCurrentEventTitle = lines.any { it.bottom < code.top && eventTitle.containsMatchIn(it.text) }
            val nextEventIndex = if (hasCurrentEventTitle) {
                lines.indexOfFirst { it.top > code.bottom && eventTitle.containsMatchIn(it.text) }
            } else {
                -1
            }
            val clippedNeighborTop = if (nextEventIndex >= 0) {
                lines.getOrNull(nextEventIndex - 1)
                    ?.takeIf { event -> event.text.trim().endsWith("선물하기") && lines[nextEventIndex].top - event.bottom <= 160 }
                    ?.top ?: lines[nextEventIndex].top
            } else {
                imageHeight
            }
            val region = PixelRect(0, 0, imageWidth, clippedNeighborTop)
            return listOf(CouponSlice(code, region, lines.filter { it.centerY < clippedNeighborTop }))
        }

        val pitch = median(sorted.zipWithNext { a, b -> b.top - a.top }).coerceAtLeast(1)
        val boundaries = sorted.zipWithNext { a, b -> (a.bottom + b.top) / 2 }
        return sorted.mapIndexed { index, code ->
            val top = if (index == 0) (code.top - pitch * ABOVE_RATIO).toInt().coerceAtLeast(0) else boundaries[index - 1]
            val bottom = if (index == sorted.lastIndex) {
                (code.bottom + pitch * BELOW_RATIO).toInt().coerceAtMost(imageHeight)
            } else boundaries[index]
            val gapAbove = if (index == 0) Int.MAX_VALUE else code.top - sorted[index - 1].bottom
            val gapBelow = if (index == sorted.lastIndex) Int.MAX_VALUE else sorted[index + 1].top - code.bottom
            val band = PixelRect(0, top, imageWidth, bottom)
            val region = tightenToCard(band, code, lines.filter { it.centerY in top until bottom })
            CouponSlice(
                code = code,
                region = region,
                lines = lines.filter { it.centerY in region.top until region.bottom },
                uncertain = minOf(gapAbove, gapBelow) < pitch * CLOSE_RATIO,
            )
        }
    }

    /**
     * 칸 경계를 카드 실제 크기로 좁힌다. 바코드에서 위아래로 줄을 훑다가 줄 간격이 갑자기 벌어지면 멈춘다.
     * 카드 안의 줄 간격은 고르고, 페이지 머리말이나 하단 버튼은 확연히 떨어져 있다는 점을 쓴다.
     * 끊을 자리를 못 찾으면 원래 칸(band)을 그대로 둔다.
     */
    private fun tightenToCard(band: PixelRect, code: DetectedCode, inBand: List<TextLine>): PixelRect {
        if (inBand.isEmpty()) return band
        val lineHeight = median(inBand.map { it.bottom - it.top }).coerceAtLeast(1)
        val firstAllowance = (lineHeight * 1.5f).toInt()

        // 바코드에 가장 가까운 줄(상품명·유효기간)은 떨어져 있어도 반드시 포함한다. 글자를 잃는 쪽이 더 나쁘다.
        var top = code.top
        var widest = 0
        var kept = 0
        for (line in inBand.filter { it.top < code.top }.sortedByDescending { it.bottom }) {
            val gap = (top - line.bottom).coerceAtLeast(0)
            if (kept > 0 && gap >= maxOf(firstAllowance, widest * 2)) break
            widest = maxOf(widest, gap)
            top = minOf(top, line.top)
            kept++
        }

        var bottom = code.bottom
        widest = 0
        kept = 0
        for (line in inBand.filter { it.bottom > code.bottom }.sortedBy { it.top }) {
            val gap = (line.top - bottom).coerceAtLeast(0)
            if (kept > 0 && gap >= maxOf(firstAllowance, widest * 2)) break
            widest = maxOf(widest, gap)
            bottom = maxOf(bottom, line.bottom)
            kept++
        }

        return PixelRect(
            band.left,
            (top - lineHeight).coerceAtLeast(band.top),
            band.right,
            (bottom + lineHeight).coerceAtMost(band.bottom),
        )
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        return if (sorted.isEmpty()) 0 else sorted[sorted.size / 2]
    }
}
