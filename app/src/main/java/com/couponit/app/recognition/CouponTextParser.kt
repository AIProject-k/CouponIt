package com.couponit.app.recognition

import java.time.LocalDate

data class CouponTextFields(
    val title: String? = null,
    val merchantName: String? = null,
    val expiryDate: LocalDate? = null,
    val expiryConfirmed: Boolean = false,
)

object CouponTextParser {
    private fun label(vararg names: String) = Regex(
        "^[\\s•·\\[【]*(?:" + names.joinToString("|") { it.map(Char::toString).joinToString("\\s*") } + ")[\\s:：\\]】]*",
        RegexOption.IGNORE_CASE,
    )
    private val merchantLabel = label("사용처", "교환처", "사용매장", "브랜드", "이용매장")
    private val titleLabel = label("상품명", "교환상품")
    private val expiryLabel = label("사용유효기간", "유효기간", "사용기간", "이용기간", "교환기간", "유효기한", "만료일", "사용기한", "기간")
    private val otherLabel = label("구매일", "발행일", "주문번호", "쿠폰번호", "고객센터", "유의사항", "사용안내", "금액")
    private val datePattern = Regex("(?<!\\d)(20\\d{2})\\s*(?:[./-]|년)\\s*(\\d{1,2})\\s*(?:[./-]|월)\\s*(\\d{1,2})(?:\\s*일)?(?!\\d)")
    private val brands = listOf("스타벅스", "투썸플레이스", "메가MGC커피", "메가커피", "컴포즈커피", "이디야커피", "빽다방", "파리바게뜨", "뚜레쥬르", "배스킨라빈스", "던킨", "올리브영", "GS25", "CU", "세븐일레븐", "이마트24", "교촌치킨", "BHC", "BBQ")
    // "[2609, 11번가]"처럼 한 줄 전체가 괄호로 감싸진 판매처·주문 태그. 상품명 후보에서 건너뛴다.
    private val tagLine = Regex("^[\\[【(（][^\\]】)）]{1,30}[\\]】)）]$")

    fun parse(text: String): CouponTextFields {
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        fun field(pattern: Regex): String? = lines.mapIndexedNotNull { index, line ->
            val match = pattern.find(line) ?: return@mapIndexedNotNull null
            val value = line.substring(match.range.last + 1).trim().ifEmpty { lines.getOrNull(index + 1).orEmpty() }
            value.takeIf { it.length in 2..80 && !isLabel(it) && !datePattern.containsMatchIn(it) }
        }.distinct().singleOrNull()

        val brandLines = lines.mapIndexedNotNull { index, line -> matchBrand(line)?.let { index to it } }
        val bracketedProducts = lines.mapNotNull { Regex("^\\[([^]\\n]{2,30})]\\s*(.{2,80})$").matchEntire(it) }
        val bracketedProduct = bracketedProducts.singleOrNull()
        val merchant = field(merchantLabel) ?: bracketedProduct?.groupValues?.get(1) ?: brandLines.map { it.second }.distinct().singleOrNull()
        val brandIndex = brandLines.firstOrNull { it.second == merchant }?.first
        val title = field(titleLabel) ?: bracketedProduct?.groupValues?.get(2) ?: brandIndex?.let { index ->
            lines.drop(index + 1).take(3).firstOrNull { !tagLine.matches(it) }
        }?.takeIf {
            it.length in 2..80 && !isLabel(it) && !datePattern.containsMatchIn(it) &&
                it.any(Char::isLetter) && !Regex("안내|유의|선물하기|사용불가|사용 불가").containsMatchIn(it)
        }
        val expiryCandidates = mutableListOf<LocalDate>()
        var ambiguous = false
        lines.forEachIndexed { index, line ->
            if (!expiryLabel.containsMatchIn(line)) return@forEachIndexed
            val section = (listOf(line) + lines.drop(index + 1).take(2).takeWhile { !isLabel(it) }).joinToString(" ")
            val matches = datePattern.findAll(section).toList()
            val dates = matches.map { match ->
                runCatching { LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt()) }.getOrNull()
            }
            val candidate = when {
                dates.any { it == null } -> null
                dates.size == 1 -> dates.single()
                dates.size == 2 && Regex("[~～∼–—-]|부터").containsMatchIn(section.substring(matches[0].range.last + 1, matches[1].range.first)) &&
                    !dates[1]!!.isBefore(dates[0]) -> dates[1]
                else -> null
            }
            if (candidate != null) expiryCandidates += candidate else ambiguous = true
        }
        val expiry = expiryCandidates.distinct().singleOrNull()?.takeUnless { ambiguous }
        return CouponTextFields(title, merchant, expiry, expiry != null)
    }

    private fun matchBrand(line: String): String? {
        val value = compact(line.trim('[', ']')).lowercase()
        brands.firstOrNull { compact(it).lowercase() == value }?.let { return it }
        // 강조 배경 위 글자처럼 OCR이 한 글자만 잘못 읽은 경우("메가MGC커파")만 허용. 짧은 이름은 오탐이 커서 정확히 일치해야 한다.
        return brands.filter { compact(it).length >= 5 && editDistance(compact(it).lowercase(), value) <= 1 }.singleOrNull()
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1).also { it[0] = i }
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun compact(value: String) = value.replace(Regex("\\s+"), "")
    private fun isLabel(value: String) = listOf(merchantLabel, titleLabel, expiryLabel, otherLabel).any { it.containsMatchIn(value) }
}
