package com.couponit.app.domain

/** 쿠폰 사용 여부를 사람이 직접 조회할 수 있는 발행사 페이지. 앱이 자동 조회하지는 않는다. */
data class CouponIssuer(
    val name: String,
    val lookupUrl: String,
    val howTo: String,
)

object IssuerLookup {
    val issuers = listOf(
        CouponIssuer("쿠프마케팅", "https://cs.inumber.co.kr/Web/CouponInquiry", "아이넘버 고객센터에서 휴대폰 인증 후 조회"),
        CouponIssuer("기프티쇼", "https://mobilecs.giftishow.co.kr/user/login", "휴대폰 인증 후 발급된 쿠폰 조회"),
        CouponIssuer("페이즈", "https://www.paysgift.com/Mobile/Inquiry", "휴대폰 인증 후 쿠폰번호로 조회"),
    )

    // 사용처 공식 안내에서 발행사를 확인한 경우만 추천한다. 원본에 다른 발행사가 적혀 있으면 사용자가 바꾼다.
    private val issuerByMerchant = mapOf(
        "메가MGC커피" to "쿠프마케팅", // mega-mgccoffee.com FAQ: 앱 미등록 모바일상품권은 쿠프마케팅 문의
        "메가커피" to "쿠프마케팅",
    )

    fun byName(name: String?): CouponIssuer? = issuers.firstOrNull { it.name == name }

    fun suggest(merchantName: String?): CouponIssuer? =
        byName(issuerByMerchant[merchantName?.replace(Regex("\\s+"), "")])
}
