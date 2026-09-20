package com.couponit.app.recognition

import com.couponit.app.domain.PixelRect
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CouponLayoutParserTest {
    private fun line(text: String, y: Int, x: Int = 100, width: Int = 600, height: Int = 40) =
        TextLine(text, x, y, x + width, y + height)

    @Test fun `unknown brand and wrapped product retain every component`() {
        val result = CouponLayoutParser.parse(listOf(
            line("구름식당", 100, height = 55), line("(구름식당) 버거+콜라R", 175),
            line("+감자튀김+2", 225), line("치즈스틱", 275),
            line("2026.09.29", 350, width = 250), line("까지 사용", 350, x = 380, width = 220),
            line("주문번호 1234567890", 440),
        ))
        assertEquals("구름식당", result.merchantName)
        assertEquals("버거+콜라R +감자튀김+2 치즈스틱", result.title)
        assertEquals(LocalDate.of(2026, 9, 29), result.expiryDate)
        assertEquals(3, result.evidence["title"]?.size)
    }

    @Test fun `layout selection respects both axes`() {
        val lines = listOf(line("사용처 왼쪽카페", 100, width = 240), line("사용처 오른쪽카페", 100, x = 500, width = 300),
            line("유효기간 2026.10.01", 200, width = 240), line("유효기간 2026.11.01", 200, x = 500, width = 300))
        val result = CouponLayoutParser.parse(lines, PixelRect(0, 0, 400, 400))
        assertEquals("왼쪽카페", result.merchantName)
        assertEquals(LocalDate.of(2026, 10, 1), result.expiryDate)
    }

    @Test fun `adjacent expiry caption may be on next line`() {
        val result = CouponLayoutParser.parse(listOf(line("2027/01/15", 100), line("까지 사용 가능", 145)))
        assertEquals(LocalDate.of(2027, 1, 15), result.expiryDate)
    }

    @Test fun `does not combine far away date and caption`() {
        assertNull(CouponLayoutParser.parse(listOf(line("2027/01/15", 100), line("까지 사용 가능", 600))).expiryDate)
    }

    @Test fun `multiline labeled title ends at metadata`() {
        val result = CouponLayoutParser.parse(listOf(line("사용처: 처음보는베이커리", 50), line("상품명: 빵 세트", 120),
            line("소금빵 2개 + 식빵 1개", 170), line("유효기간: 2026-10-12", 250)))
        assertEquals("빵 세트 소금빵 2개 + 식빵 1개", result.title)
        assertEquals("처음보는베이커리", result.merchantName)
    }

    @Test fun `conflicting reread never silently overwrites expiry`() {
        val a = CouponTextFields(expiryDate = LocalDate.of(2026, 10, 1), expiryConfirmed = true)
        val b = CouponTextFields(expiryDate = LocalDate.of(2026, 11, 1), expiryConfirmed = true)
        val result = CouponLayoutParser.reconcile(a, b)
        assertNull(result.expiryDate)
        assertFalse(result.expiryConfirmed)
        assertTrue(result.reviewReasons.isNotEmpty())
    }

    @Test fun `order number is not used as barcode verification`() {
        val code = DetectedCode("1", "111122223333", 100, 300, 700, 400)
        assertTrue(CouponLayoutParser.codeIssues(listOf(line("주문번호 999988887777", 240)), code).isEmpty())
        assertTrue(CouponLayoutParser.codeIssues(listOf(line("9999 8888 7777", 420)), code).isNotEmpty())
        assertTrue(CouponLayoutParser.codeIssues(listOf(line("1111 2222 3333", 420)), code).isEmpty())
    }

    @Test fun `unknown event product does not manufacture merchant`() {
        assertNull(CouponLayoutParser.parse(listOf(line("[EVENT] 미스터리 세트", 100))).merchantName)
    }

    @Test fun `retry cannot erase conflicting date evidence`() {
        val first = CouponLayoutParser.parse(listOf(line("유효기간 2026.10.01", 100), line("만료일 2026.11.01", 300)))
        val retry = CouponLayoutParser.parse(listOf(line("유효기간 2026.10.01", 100)))
        assertNull(CouponLayoutParser.reconcile(first, retry).expiryDate)
    }
}
