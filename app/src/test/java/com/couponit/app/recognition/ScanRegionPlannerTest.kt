package com.couponit.app.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRegionPlannerTest {
    @Test
    fun `normal image is scanned once at full size`() {
        assertEquals(listOf(ScanRegion(0, 0, 1080, 1200)), ScanRegionPlanner.plan(1080, 1200))
    }

    @Test
    fun `long screenshot is split into overlapping readable bands`() {
        val regions = ScanRegionPlanner.plan(1080, 2340)

        assertTrue(regions.size >= 3)
        assertTrue(regions.all { it.height <= 1350 })
        assertTrue(regions.any { 850 in it.top until it.bottom })
        assertEquals(0, regions.first().top)
        assertEquals(2340, regions.last().bottom)
    }
}
