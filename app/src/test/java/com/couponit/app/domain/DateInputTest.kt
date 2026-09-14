package com.couponit.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateInputTest {
    private val expected = LocalDate.of(2026, 12, 9)

    @Test fun `accepts common typed date formats`() {
        listOf("2026-12-09", "2026.12.09", "2026/12/9", "2026년 12월 9일", "20261209", " 2026-12-09 ").forEach {
            assertEquals(it, expected, DateInput.parse(it))
        }
    }

    @Test fun `rejects impossible or partial dates`() {
        listOf("2026-02-30", "2026-13-01", "202612", "12-09", "내일", "").forEach {
            assertNull(it, DateInput.parse(it))
        }
    }
}
