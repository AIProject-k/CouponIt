package com.couponit.app.recognition

import com.couponit.app.domain.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponSplitterTest {
    private fun code(top: Int, value: String) = DetectedCode("1", value, 300, top, 640, top + 130)
    private fun line(text: String, top: Int) = TextLine(text, 100, top, 800, top + 40)

    /** 네이버페이 교환권 목록 스크린샷과 같은 배치: 카드마다 제목·바코드·유효기간이 반복된다. */
    private fun card(index: Int, offset: Int) = listOf(
        line("메가MGC커피 선물하기", offset),
        line("[EVENT] 상품 $index", offset + 60),
        line("code-$index", offset + 200),
        line("복사", offset + 280),
        line("93일남음 2026.12.2${index}까지 사용가능", offset + 350),
    )

    @Test fun `splits one region per barcode`() {
        val codes = listOf(code(690, "A"), code(1250, "B"))
        val lines = card(1, 500) + card(2, 1060)
        val slices = CouponSplitter.split(920, 1900, codes, lines)

        assertEquals(2, slices.size)
        assertEquals(listOf("A", "B"), slices.map { it.code.rawValue })
        assertTrue(slices.all { it.region.width == 920 })
        assertFalse(slices.any { it.uncertain })
    }

    @Test fun `each region keeps only its own card text`() {
        val codes = listOf(code(690, "A"), code(1250, "B"))
        val slices = CouponSplitter.split(920, 1900, codes, card(1, 500) + card(2, 1060))

        assertTrue(slices[0].text.contains("상품 1"))
        assertFalse(slices[0].text.contains("상품 2"))
        assertTrue(slices[1].text.contains("상품 2"))
        assertFalse(slices[1].text.contains("상품 1"))
    }

    @Test fun `regions do not overlap and stay inside the image`() {
        val codes = listOf(code(390, "A"), code(950, "B"), code(1510, "C"))
        val slices = CouponSplitter.split(920, 1900, codes, emptyList())

        assertEquals(3, slices.size)
        assertTrue(slices.first().region.top >= 0)
        assertTrue(slices.last().region.bottom <= 1900)
        slices.zipWithNext { a, b -> assertEquals(a.region.bottom, b.region.top) }
    }

    @Test fun `text below the last card is not attached to it`() {
        val codes = listOf(code(690, "A"), code(1250, "B"))
        val lines = card(1, 500) + card(2, 1060) + listOf(line("메가MGC커피 선물하기", 1667), line("[EVENT] 잘린 상품", 1755))
        val slices = CouponSplitter.split(920, 1900, codes, lines)

        assertFalse(slices[1].text.contains("잘린 상품"))
    }

    @Test fun `cut off card without a barcode makes no slice`() {
        val slices = CouponSplitter.split(920, 1900, listOf(code(690, "A")), card(1, 500) + card(2, 1060))
        assertEquals(1, slices.size)
        assertTrue(slices.single().text.contains("상품 1"))
        assertFalse(slices.single().text.contains("상품 2"))
    }

    @Test fun `same code detected in overlapping scan bands makes one slice`() {
        val firstDetection = code(690, "A")
        val overlappingDetection = firstDetection.copy(format = "alternate", top = 692, bottom = 822)

        val slices = CouponSplitter.split(
            920,
            1900,
            listOf(firstDetection, overlappingDetection, code(1250, "B")),
            card(1, 500) + card(2, 1060),
        )

        assertEquals(listOf("A", "B"), slices.map { it.code.rawValue })
    }

    @Test fun `single coupon without evidence of another card keeps the full image`() {
        val lines = listOf(line("멀리 있는 상품명", 40), line("유효기간 2027.01.31", 1750))

        val slice = CouponSplitter.split(920, 1900, listOf(code(900, "A")), lines).single()

        assertEquals(PixelRect(0, 0, 920, 1900), slice.region)
        assertEquals(lines.map { it.text }, slice.lines.map { it.text })
    }

    @Test fun `unevenly spaced cards keep their own expiry text`() {
        val codes = listOf(code(410, "A"), code(1180, "B"), code(1690, "C"))
        val lines = listOf(
            line("[EVENT] 첫 상품", 180), line("2026.10.01까지 사용가능", 620),
            line("[EVENT] 둘째 상품", 930), line("2026.11.02까지 사용가능", 1390),
            line("[EVENT] 셋째 상품", 1500), line("2026.12.03까지 사용가능", 1880),
        )

        val slices = CouponSplitter.split(920, 2000, codes, lines)

        assertTrue(slices[0].text.contains("2026.10.01"))
        assertFalse(slices[0].text.contains("2026.11.02"))
        assertTrue(slices[1].text.contains("2026.11.02"))
        assertFalse(slices[1].text.contains("2026.12.03"))
        assertTrue(slices[2].text.contains("2026.12.03"))
    }

    @Test fun `no barcode means no slice`() {
        assertEquals(emptyList<CouponSlice>(), CouponSplitter.split(920, 1900, emptyList(), card(1, 500)))
    }

    @Test fun `two codes inside one card are flagged for review`() {
        val codes = listOf(code(690, "A"), code(860, "B"), code(1800, "C"))
        val slices = CouponSplitter.split(920, 2400, codes, emptyList())

        assertTrue(slices[0].uncertain)
        assertTrue(slices[1].uncertain)
        assertFalse(slices[2].uncertain)
    }
}
