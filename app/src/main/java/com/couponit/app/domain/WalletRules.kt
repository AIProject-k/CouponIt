package com.couponit.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object WalletRules {
    fun sort(coupons: List<Coupon>): List<Coupon> = coupons.sortedWith(
        compareByDescending<Coupon> { it.favorite }
            .thenBy { if (it.expiryDate != null && it.expiryConfirmed) 0 else 1 }
            .thenBy { if (it.expiryConfirmed) it.expiryDate else null }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.merchantName.orEmpty() }
            .thenBy { it.title },
    )

    fun filter(coupons: List<Coupon>, query: String, merchant: String?): List<Coupon> {
        val needle = query.trim()
        return sort(coupons.filter { coupon ->
            coupon.location == CouponLocation.WALLET && coupon.usageState != UsageState.COMPLETED &&
                (merchant == null || coupon.merchantName == merchant) &&
                (needle.isEmpty() || coupon.title.contains(needle, ignoreCase = true) ||
                    coupon.merchantName.orEmpty().contains(needle, ignoreCase = true))
        })
    }

    fun summary(coupons: List<Coupon>, today: LocalDate): WalletSummary {
        val wallet = coupons.filter {
            it.location == CouponLocation.WALLET && it.usageState != UsageState.COMPLETED
        }
        return WalletSummary(
            unused = wallet.size,
            expiringInSevenDays = wallet.count {
                it.expiryConfirmed && it.expiryDate?.let { date ->
                    ChronoUnit.DAYS.between(today, date) in 0..7
                } == true
            },
            needsReview = wallet.count { it.needsReview },
        )
    }

    fun recordedBalance(events: List<UsageEvent>): Long? {
        val reversedIds = events.asSequence()
            .filter { it.type == UsageEventType.EVENT_REVERSED }
            .mapNotNull { it.reversesEventId }
            .toSet()
        val active = events.filter { it.id !in reversedIds && it.type != UsageEventType.EVENT_REVERSED }
        val baseline = active.filter { it.type == UsageEventType.BALANCE_SET && it.amountMinor != null }
            .maxByOrNull { it.occurredAt } ?: return null
        val spent = active.filter {
            it.type == UsageEventType.SPEND_RECORDED && it.occurredAt.isAfter(baseline.occurredAt)
        }.sumOf { it.amountMinor ?: 0L }
        return (baseline.amountMinor!! - spent).coerceAtLeast(0L)
    }

    fun canAppend(events: List<UsageEvent>, operationId: String): Boolean =
        events.none { it.operationId == operationId }
}
