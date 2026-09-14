package com.couponit.app.domain

import java.time.LocalDate

/** 사용자가 직접 입력한 종료일. 2026-12-09, 2026.12.09, 2026/12/9, 2026년 12월 9일, 20261209를 받는다. */
object DateInput {
    private val compact = Regex("^(\\d{4})(\\d{2})(\\d{2})$")
    private val separated = Regex("^(\\d{4})\\s*[-./년]\\s*(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})\\s*일?$")

    fun parse(input: String): LocalDate? {
        val value = input.trim()
        val parts = (compact.matchEntire(value) ?: separated.matchEntire(value))?.groupValues?.drop(1) ?: return null
        return runCatching { LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) }.getOrNull()
    }
}
