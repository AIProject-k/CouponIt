package com.couponit.app.recognition

import java.time.LocalDate

data class CouponTextFields(
    val title: String? = null,
    val merchantName: String? = null,
    val expiryDate: LocalDate? = null,
    val expiryConfirmed: Boolean = false,
    val evidence: Map<String, List<TextLine>> = emptyMap(),
    val reviewReasons: List<String> = emptyList(),
    val retried: Boolean = false,
    val conflicts: Set<String> = emptySet(),
) {
    val needsReview: Boolean get() = title == null || merchantName == null || !expiryConfirmed || reviewReasons.isNotEmpty()
}

object CouponTextParser {
    private fun label(vararg names: String) = Regex(
        "^[\\s•·\\[【]*(?:" + names.joinToString("|") { it.map(Char::toString).joinToString("\\s*") } + ")[\\s:：\\]】]*",
        RegexOption.IGNORE_CASE,
    )
    private val merchantLabel = label("사용처", "교환처", "사용매장", "브랜드", "이용매장")
    private val titleLabel = label("상품명", "교환상품")
    private val expiryLabel = label("사용유효기간", "유효기간", "사용기간", "이용기간", "교환기간", "유효기한", "만료일", "사용기한", "기간")
    private val otherLabel = label("구매일", "발행일", "주문번호", "쿠폰번호", "고객센터", "유의사항", "사용안내", "금액")
    // OCR이 숫자 사이에 공백을 넣어 읽는 경우가 있어("2026.1 2.21") 월·일 안의 공백 하나를 허용한다.
    private val datePattern = Regex("(?<!\\d)(20\\d{2})\\s*(?:[./-]|년)\\s*(\\d\\s?\\d?)\\s*(?:[./-]|월)\\s*(\\d\\s?\\d?)(?:\\s*일)?(?!\\d)")
    private val brands = listOf("스타벅스", "투썸플레이스", "메가MGC커피", "메가커피", "컴포즈커피", "이디야커피", "빽다방", "파리바게뜨", "뚜레쥬르", "배스킨라빈스", "던킨", "올리브영", "GS25", "CU", "세븐일레븐", "이마트24", "교촌치킨", "BHC", "BBQ")
    // "[2609, 11번가]"처럼 한 줄 전체가 괄호로 감싸진 판매처·주문 태그. 상품명 후보에서 건너뛴다.
    private val tagLine = Regex("^[\\[【(（][^\\]】)）]{1,30}[\\]】)）]$")
    private val bracketProduct = Regex("^\\[([^]\\n]{2,30})]\\s*(.{2,80})$")
    // "[EVENT] 상품명"의 대괄호는 사용처가 아니라 행사 표시다.
    private val bracketTags = setOf("event", "이벤트", "특가", "한정", "단독", "증정", "선물")
    // "메가MGC커피 선물하기"처럼 브랜드 뒤에 붙는 안내 문구. 긴 것부터 뗀다.
    private val brandSuffixes = listOf("모바일교환권", "선물하기", "기프티콘", "교환권", "e쿠폰", "금액권", "쿠폰")
    // 라벨 없이 "2026.12.21까지 사용가능"으로 적힌 종료일
    private val untilSuffix = Regex("^\\s*까지")
    // 상품명으로 볼 수 없는 화면 안내 문구
    private val noiseLine = Regex("안내|유의|선물하기|사용불가|사용 불가|복사|남음|전체보기|주문내역|총\\s*\\d+\\s*장")

    fun parse(text: String): CouponTextFields {
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        fun field(pattern: Regex): String? = lines.mapIndexedNotNull { index, line ->
            val match = pattern.find(line) ?: return@mapIndexedNotNull null
            val value = line.substring(match.range.last + 1).trim().ifEmpty { lines.getOrNull(index + 1).orEmpty() }
            value.takeIf { it.length in 2..80 && !isLabel(it) && !datePattern.containsMatchIn(it) }
        }.distinct().singleOrNull()

        val brandLines = lines.mapIndexedNotNull { index, line -> matchBrand(line)?.let { index to it } }
        val brackets = lines.mapNotNull { bracketProduct.matchEntire(it) }
        val bracketMerchant = brackets.filterNot { isBracketTag(it.groupValues[1]) }.map { it.groupValues[1].trim() }.distinct().singleOrNull()
        val bracketTitle = brackets.map { it.groupValues[2].trim() }.distinct().singleOrNull()
        val productMerchant = bracketTitle?.let(::matchBrandPrefix)
        val merchant = field(merchantLabel) ?: bracketMerchant ?: brandLines.map { it.second }.distinct().singleOrNull() ?: productMerchant
        val brandIndex = brandLines.firstOrNull { it.second == merchant }?.first
        val title = field(titleLabel) ?: bracketTitle ?: brandIndex?.let { index ->
            lines.drop(index + 1).take(3).firstOrNull { !tagLine.matches(it) }
        }?.takeIf {
            it.length in 2..80 && !isLabel(it) && !datePattern.containsMatchIn(it) &&
                it.any(Char::isLetter) && !noiseLine.containsMatchIn(it)
        }
        val expiryCandidates = mutableListOf<LocalDate>()
        var ambiguous = false
        lines.forEachIndexed { index, line ->
            if (!expiryLabel.containsMatchIn(line)) return@forEachIndexed
            val section = (listOf(line) + lines.drop(index + 1).take(2).takeWhile { !isLabel(it) }).joinToString(" ")
            val matches = datePattern.findAll(section).toList()
            val dates = matches.map(::toDate)
            val candidate = when {
                dates.any { it == null } -> null
                dates.size == 1 -> dates.single()
                dates.size == 2 && Regex("[~～∼–—-]|부터").containsMatchIn(section.substring(matches[0].range.last + 1, matches[1].range.first)) &&
                    !dates[1]!!.isBefore(dates[0]) -> dates[1]
                else -> null
            }
            if (candidate != null) expiryCandidates += candidate else ambiguous = true
        }
        // 라벨 없이 "2026.12.21까지 사용가능"으로만 적힌 경우. "93일남음"처럼 상대 기간은 절대 날짜를 대신하지 않는다.
        for (line in lines) {
            for (match in datePattern.findAll(line)) {
                if (!untilSuffix.containsMatchIn(line.substring(match.range.last + 1))) continue
                val date = toDate(match)
                if (date == null) ambiguous = true else expiryCandidates += date
            }
        }
        val expiry = expiryCandidates.distinct().singleOrNull()?.takeUnless { ambiguous }
        return CouponTextFields(title, merchant, expiry, expiry != null,
            conflicts = if (ambiguous || expiryCandidates.distinct().size > 1) setOf("expiry") else emptySet())
    }

    private fun toDate(match: MatchResult): LocalDate? = runCatching {
        LocalDate.of(match.groupValues[1].toInt(), compact(match.groupValues[2]).toInt(), compact(match.groupValues[3]).toInt())
    }.getOrNull()

    private fun isBracketTag(value: String) = compact(value).lowercase() in bracketTags

    private fun matchBrand(line: String): String? {
        val raw = compact(line.trim('[', ']')).lowercase()
        val suffix = brandSuffixes.map { compact(it).lowercase() }
            .firstOrNull { raw.endsWith(it) && raw.length > it.length }
        val value = if (suffix != null) raw.removeSuffix(suffix) else raw
        brands.firstOrNull { compact(it).lowercase() == value }?.let { return it }
        // 강조 배경 위 글자처럼 OCR이 한 글자만 잘못 읽은 경우("메가MGC커파")만 허용. 짧은 이름은 오탐이 커서 정확히 일치해야 한다.
        return brands.filter { compact(it).length >= 5 && editDistance(compact(it).lowercase(), value) <= 1 }.singleOrNull()
    }

    private fun matchBrandPrefix(line: String): String? {
        val value = compact(line).lowercase()
        return brands.filter { value.startsWith(compact(it).lowercase()) }.singleOrNull()
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
