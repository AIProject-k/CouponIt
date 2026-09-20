package com.couponit.app.recognition

import com.couponit.app.domain.PixelRect
import kotlin.math.max
import kotlin.math.min

/** Field decisions retain their source rectangles; geometry is not discarded before parsing. */
object CouponLayoutParser {
    private val productLabel = Regex("^(?:상품명|교환상품)\\s*[:：]?\\s*")
    private val prefix = Regex("^[\\[(（【]([^\\])）】]{2,30})[\\])）】]\\s*(.+)$")
    private val tags = setOf("event", "이벤트", "특가", "한정", "단독", "증정", "선물", "ice", "hot", "아이스", "핫")
    private val date = Regex("20\\d{2}\\s*[.년/-]")
    private val stop = Regex("^(?:사용처|교환처|브랜드|유효|사용\\s*기간|사용\\s*기한|기간|만료|주문|쿠폰\\s*번호|발행|구매|수량|금액|고객|상품\\s*이용|이용\\s*안내)|안내|유의|선물하기|복사|남음|전체보기|주문내역|까지\\s*사용")

    fun select(lines: List<TextLine>, crop: PixelRect?): List<TextLine> = lines.filter { line ->
        line.right > line.left && line.bottom > line.top && (crop == null ||
            (line.centerY in crop.top until crop.bottom && (line.left + line.right) / 2 in crop.left until crop.right))
    }

    fun parse(input: List<TextLine>, crop: PixelRect? = null): CouponTextFields {
        val lines = rows(select(input, crop))
        val text = lines.mapIndexed { index, line ->
            val next = lines.getOrNull(index + 1)
            if (date.containsMatchIn(line.text) && next != null && next.text.trim().startsWith("까지") && adjacent(line, next))
                line.text + " " + next.text else line.text
        }.joinToString("\n")
        val base = CouponTextParser.parse(text)
        val bracketed = lines.mapNotNull { line -> prefix.matchEntire(line.text)?.let { line to it } }
        val merchantFromPrefix = bracketed.mapNotNull { (_, match) ->
            val name = match.groupValues[1].trim()
            name.takeIf { compact(it).lowercase() !in tags && !it.any(Char::isDigit) &&
                (lines.any { other -> compact(other.text) == compact(it) } || base.merchantName == it ||
                    match.value.startsWith("[")) }
        }.distinct().singleOrNull()
        val merchant = base.merchantName ?: merchantFromPrefix
        val start = lines.indexOfFirst { productLabel.containsMatchIn(it.text) }.takeIf { it >= 0 }
            ?: bracketed.firstOrNull { (_, match) ->
                match.groupValues[1].trim() == merchant || compact(match.groupValues[1]).lowercase() in tags
            }?.first?.let(lines::indexOf)
            ?: base.title?.let { title -> lines.indexOfFirst { compact(it.text).contains(compact(title)) }.takeIf { it >= 0 } }
        val titleLines = mutableListOf<TextLine>()
        var title: String? = base.title
        if (start != null) {
            val first = lines[start]
            val match = prefix.matchEntire(first.text)
            val initial = when {
                productLabel.containsMatchIn(first.text) -> first.text.replaceFirst(productLabel, "")
                match != null && (match.groupValues[1].trim() == merchant || compact(match.groupValues[1]).lowercase() in tags - setOf("ice", "hot", "아이스", "핫")) -> match.groupValues[2]
                else -> base.title ?: first.text
            }
            titleLines += first
            for (next in lines.drop(start + 1).take(10)) {
                if (!adjacent(titleLines.last(), next) || date.containsMatchIn(next.text) || stop.containsMatchIn(next.text) ||
                    prefix.matches(next.text) || !next.text.any(Char::isLetter) || next.text == merchant) break
                titleLines += next
            }
            title = (listOf(initial) + titleLines.drop(1).map { it.text }).joinToString(" ").trim().takeIf { it.isNotEmpty() }
        }
        val evidence = buildMap {
            if (titleLines.isNotEmpty()) put("title", titleLines.toList())
            if (merchant != null) put("merchant", lines.filter { compact(it.text).contains(compact(merchant)) })
            if (base.expiryDate != null) put("expiry", lines.filter { date.containsMatchIn(it.text) || it.text.trim().startsWith("까지") })
        }
        val reasons = buildList {
            if (merchant == null) add("사용처를 확인해 주세요.")
            if (title == null) add("상품명을 확인해 주세요.")
            if (!base.expiryConfirmed) add("종료일을 확인해 주세요.")
        }
        return base.copy(title = title, merchantName = merchant, evidence = evidence, reviewReasons = reasons)
    }

    /** Join pieces on the same baseline, but never bridge a large gap between columns. */
    private fun rows(input: List<TextLine>): List<TextLine> {
        val result = mutableListOf<TextLine>()
        for (line in input.sortedWith(compareBy({ it.top }, { it.left }))) {
            val index = result.indexOfLast { row ->
                val overlap = min(row.bottom, line.bottom) - max(row.top, line.top)
                overlap >= min(row.bottom - row.top, line.bottom - line.top) * .6 &&
                    line.left >= row.right && line.left - row.right <= max(row.bottom - row.top, line.bottom - line.top) * 3
            }
            if (index < 0) result += line else {
                val row = result[index]
                result[index] = TextLine(row.text + " " + line.text, row.left, min(row.top, line.top), line.right, max(row.bottom, line.bottom))
            }
        }
        return result.sortedWith(compareBy({ it.top }, { it.left }))
    }

    private fun adjacent(a: TextLine, b: TextLine): Boolean {
        val height = max(a.bottom - a.top, b.bottom - b.top)
        val overlap = min(a.right, b.right) - max(a.left, b.left)
        return b.top >= a.top && b.top - a.bottom <= height * 1.25 &&
            overlap >= min(a.right - a.left, b.right - b.left) * .4
    }

    fun reconcile(first: CouponTextFields, retry: CouponTextFields): CouponTextFields {
        val conflicts = mutableListOf<String>()
        fun choose(a: String?, b: String?, name: String, keepProposal: Boolean = false): String? {
            if (a != null && b != null && compact(a) != compact(b)) {
                conflicts += "$name 재인식 결과가 달라 원본 확인이 필요해요."
                return if (keepProposal) a else null
            }
            return a ?: b
        }
        val blocked = first.conflicts + retry.conflicts
        val title = choose(first.title, retry.title, "상품명", keepProposal = true)
        val merchant = choose(first.merchantName, retry.merchantName, "사용처", keepProposal = true)
        val expiry = if ("expiry" in blocked) null else choose(first.expiryDate?.toString(), retry.expiryDate?.toString(), "종료일")?.let(java.time.LocalDate::parse)
        val evidence = listOf("title", "merchant", "expiry").associateWith { key ->
            first.evidence[key].orEmpty().ifEmpty { retry.evidence[key].orEmpty() }
        }
        return CouponTextFields(title, merchant, expiry, expiry != null, evidence, conflicts + buildList {
            if (title == null) add("상품명을 확인해 주세요.")
            if (merchant == null) add("사용처를 확인해 주세요.")
            if (expiry == null) add("종료일을 확인해 주세요.")
        }, retried = true, conflicts = blocked + buildSet {
            if (first.title != null && retry.title != null && compact(first.title) != compact(retry.title)) add("title")
            if (first.merchantName != null && retry.merchantName != null && compact(first.merchantName) != compact(retry.merchantName)) add("merchant")
            if (first.expiryDate != null && retry.expiryDate != null && expiry == null) add("expiry")
        })
    }

    fun codeIssues(lines: List<TextLine>, code: DetectedCode): List<String> {
        val printed = lines.filter { line ->
            line.top >= code.bottom - 10 && line.top - code.bottom <= (code.bottom - code.top).coerceAtLeast(20) * 2 &&
                (line.left + line.right) / 2 in code.left..code.right && Regex("[\\d\\s-]{8,40}").matches(line.text)
        }.map { it.text.filter(Char::isDigit) }.filter { it.length >= 8 }.distinct()
        return if (printed.isNotEmpty() && printed.any { it != code.rawValue.filter(Char::isDigit) })
            listOf("바코드와 인쇄된 번호가 달라 확인이 필요해요.") else emptyList()
    }

    private fun compact(value: String) = value.replace(Regex("\\s+"), "")
}
