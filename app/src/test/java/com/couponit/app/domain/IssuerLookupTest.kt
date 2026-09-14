package com.couponit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuerLookupTest {
    @Test fun `mega coffee suggests coop marketing`() {
        assertEquals("쿠프마케팅", IssuerLookup.suggest("메가MGC커피")?.name)
        assertEquals("쿠프마케팅", IssuerLookup.suggest("메가 커피")?.name)
    }

    @Test fun `unknown or missing merchant has no suggestion`() {
        assertNull(IssuerLookup.suggest("스타벅스"))
        assertNull(IssuerLookup.suggest(null))
    }

    @Test fun `stored issuer name resolves and unknown name does not`() {
        assertEquals("페이즈", IssuerLookup.byName("페이즈")?.name)
        assertNull(IssuerLookup.byName("없는 발행사"))
        assertNull(IssuerLookup.byName(null))
    }

    @Test fun `lookup pages use https and names are unique`() {
        assertTrue(IssuerLookup.issuers.all { it.lookupUrl.startsWith("https://") })
        assertEquals(IssuerLookup.issuers.size, IssuerLookup.issuers.map { it.name }.toSet().size)
    }
}
