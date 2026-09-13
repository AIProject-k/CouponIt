package com.couponit.app.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRulesTest {
    private val today = LocalDate.of(2026, 9, 13)

    @Test
    fun `wallet sort puts confirmed nearest expiry first and unknown dates last`() {
        val items = listOf(
            coupon("unknown", expiry = null),
            coupon("later", expiry = today.plusDays(10)),
            coupon("soon", expiry = today.plusDays(2)),
            coupon("unconfirmed", expiry = today.plusDays(1), expiryConfirmed = false),
        )

        assertEquals(listOf("soon", "later", "unconfirmed", "unknown"), WalletRules.sort(items).map { it.id })
    }

    @Test
    fun `summary excludes completed archived and unconfirmed expiry`() {
        val items = listOf(
            coupon("soon", expiry = today.plusDays(7)),
            coupon("unknown", expiry = today.plusDays(1), expiryConfirmed = false, review = true),
            coupon("used", usageState = UsageState.COMPLETED),
            coupon("archived", location = CouponLocation.ARCHIVED),
        )

        assertEquals(WalletSummary(unused = 2, expiringInSevenDays = 1, needsReview = 1), WalletRules.summary(items, today))
    }

    @Test
    fun `search matches merchant or title while preserving wallet only`() {
        val items = listOf(
            coupon("a", merchant = "별다방", title = "아메리카노"),
            coupon("b", merchant = "편의점", title = "카페라떼"),
            coupon("c", merchant = "별다방", title = "사용됨", location = CouponLocation.ARCHIVED),
            coupon("d", merchant = "별다방", title = "사용 완료", usageState = UsageState.COMPLETED),
        )

        assertEquals(listOf("a"), WalletRules.filter(items, query = "별다방", merchant = null).map { it.id })
        assertEquals(listOf("b"), WalletRules.filter(items, query = "라떼", merchant = "편의점").map { it.id })
    }

    @Test
    fun `balance is unknown without a confirmed baseline`() {
        val events = listOf(usage("spend", UsageEventType.SPEND_RECORDED, amount = 2_000))
        assertNull(WalletRules.recordedBalance(events))
    }

    @Test
    fun `balance applies spends after latest baseline and ignores reversed events`() {
        val events = listOf(
            usage("old", UsageEventType.SPEND_RECORDED, amount = 1_000, seconds = 1),
            usage("base", UsageEventType.BALANCE_SET, amount = 10_000, seconds = 2),
            usage("spend", UsageEventType.SPEND_RECORDED, amount = 3_000, seconds = 3),
            usage("reversal", UsageEventType.EVENT_REVERSED, seconds = 4, reverses = "spend"),
            usage("active", UsageEventType.SPEND_RECORDED, amount = 1_500, seconds = 5),
        )

        assertEquals(8_500L, WalletRules.recordedBalance(events))
    }

    @Test
    fun `operation id rejects duplicate usage submission`() {
        val events = listOf(usage("one", UsageEventType.REDEEM_MARKED, operationId = "tap-1"))
        assertFalse(WalletRules.canAppend(events, "tap-1"))
        assertTrue(WalletRules.canAppend(events, "tap-2"))
    }

    private fun coupon(
        id: String,
        merchant: String = "가상매장",
        title: String = id,
        expiry: LocalDate? = null,
        expiryConfirmed: Boolean = expiry != null,
        usageState: UsageState = UsageState.UNUSED,
        location: CouponLocation = CouponLocation.WALLET,
        review: Boolean = false,
    ) = Coupon(
        id = id,
        title = title,
        merchantName = merchant,
        type = CouponType.EXCHANGE,
        expiryDate = expiry,
        expiryConfirmed = expiryConfirmed,
        usageState = usageState,
        location = location,
        needsReview = review,
    )

    private fun usage(
        id: String,
        type: UsageEventType,
        amount: Long? = null,
        seconds: Long = 0,
        reverses: String? = null,
        operationId: String = id,
    ) = UsageEvent(id, "coupon", type, amount, "KRW", Instant.ofEpochSecond(seconds), reverses, operationId)
}
