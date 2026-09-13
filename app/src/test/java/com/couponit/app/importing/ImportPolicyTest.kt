package com.couponit.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPolicyTest {
    @Test
    fun `accepts only jpeg and png for first release`() {
        assertTrue(ImportPolicy.isSupported("image/jpeg"))
        assertTrue(ImportPolicy.isSupported("image/png"))
        assertFalse(ImportPolicy.isSupported("image/heic"))
        assertFalse(ImportPolicy.isSupported(null))
    }

    @Test
    fun `private filename never contains original name or code text`() {
        assertEquals("asset-abc123.jpg", ImportPolicy.privateFileName("abc123", "image/jpeg"))
        assertEquals("asset-abc123.png", ImportPolicy.privateFileName("abc123", "image/png"))
    }
}
