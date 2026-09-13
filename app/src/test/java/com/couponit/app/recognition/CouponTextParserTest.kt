package com.couponit.app.recognition

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CouponTextParserTest {
    @Test fun `recognizes marketplace period and bracketed product`() {
        val result = CouponTextParser.parse("[버거킹] 불고기와퍼주니어세트\n수량 1개\n기간 2026-11-10 D-59\n사용처 버거킹\n상품 이용안내")
        assertEquals(LocalDate.of(2026, 11, 10), result.expiryDate)
        assertEquals("버거킹", result.merchantName)
        assertEquals("불고기와퍼주니어세트", result.title)
    }

    @Test fun `recognizes usage expiry in notes`() {
        assertEquals(LocalDate.of(2026, 11, 10), CouponTextParser.parse("· 사용 유효기간: ~2026년 11월 10일까지 입니다. (유효기간 연장 불가)").expiryDate)
    }

    @Test fun `period label does not match extension prose`() {
        assertNull(CouponTextParser.parse("기간 연장은 불가합니다\n구매일 2026-11-10").expiryDate)
    }
    @Test fun `extracts labeled merchant title and expiry`() {
        val result = CouponTextParser.parse("사용처 : 별빛카페\n상품명: 아이스 라떼\n유효기간: 2026.12.31")
        assertEquals("별빛카페", result.merchantName)
        assertEquals("아이스 라떼", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), result.expiryDate)
        assertTrue(result.expiryConfirmed)
    }

    @Test fun `uses range end and ignores purchase date`() {
        val result = CouponTextParser.parse("구매일 2026.09.01\n사용기간\n2026.09.01 ~\n2026.12.31")
        assertEquals(LocalDate.of(2026, 12, 31), result.expiryDate)
        assertTrue(result.expiryConfirmed)
    }

    @Test fun `supports Korean date and spaced labels`() {
        val result = CouponTextParser.parse("사 용 처\n별빛카페\n유 효 기 간 2026년 12월 31일까지")
        assertEquals("별빛카페", result.merchantName)
        assertEquals(LocalDate.of(2026, 12, 31), result.expiryDate)
    }

    @Test fun `supports slash dates`() {
        assertEquals(LocalDate.of(2026, 9, 30), CouponTextParser.parse("만료일 2026/9/30").expiryDate)
    }

    @Test fun `rejects impossible dates instead of normalizing`() {
        assertNull(CouponTextParser.parse("유효기간 2026.02.30").expiryDate)
    }

    @Test fun `does not infer expiry from purchase or unlabeled dates`() {
        assertNull(CouponTextParser.parse("구매일 2026.09.13\n2026.12.31").expiryDate)
    }

    @Test fun `conflicting expiry labels need review`() {
        val result = CouponTextParser.parse("유효기간 2026.10.01\n만료일 2026.11.01")
        assertNull(result.expiryDate)
        assertFalse(result.expiryConfirmed)
    }

    @Test fun `does not consume next field as merchant`() {
        assertNull(CouponTextParser.parse("사용처\n유효기간 2026.12.31").merchantName)
    }

    @Test fun `recognizes standalone brand and following product`() {
        val result = CouponTextParser.parse("카카오톡 선물하기\n스타벅스\n아이스 카페 아메리카노 T\n유효기간 2026-12-31")
        assertEquals("스타벅스", result.merchantName)
        assertEquals("아이스 카페 아메리카노 T", result.title)
    }

    @Test fun `does not infer merchant from exclusion prose`() {
        assertNull(CouponTextParser.parse("스타벅스 매장에서는 사용 불가\n고객센터 1234-5678").merchantName)
    }

    @Test fun `empty input stays unknown`() {
        val result = CouponTextParser.parse("")
        assertNull(result.title)
        assertNull(result.merchantName)
        assertNull(result.expiryDate)
    }

    @Test fun `does not choose arbitrary last date without range separator`() {
        assertNull(CouponTextParser.parse("유효기간 2026.10.01 2026.11.01").expiryDate)
    }
}
