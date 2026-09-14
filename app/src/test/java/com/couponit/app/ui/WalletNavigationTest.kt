package com.couponit.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletNavigationTest {
    @Test fun `home is the top level`() {
        assertNull(WalletNavigation.backTarget(WalletScreen.Home))
    }

    @Test fun `tabs go back to home`() {
        assertEquals(WalletScreen.Home, WalletNavigation.backTarget(WalletScreen.Archive))
        assertEquals(WalletScreen.Home, WalletNavigation.backTarget(WalletScreen.Settings))
    }

    @Test fun `code screen opened from home returns to home`() {
        assertEquals(WalletScreen.Home, WalletNavigation.backTarget(WalletScreen.Present("c1")))
    }

    @Test fun `screens return to where they were opened`() {
        val detailFromArchive = WalletScreen.Detail("c1", from = WalletScreen.Archive)
        val presentFromDetail = WalletScreen.Present("c1", from = detailFromArchive)
        assertEquals(detailFromArchive, WalletNavigation.backTarget(presentFromDetail))
        assertEquals(WalletScreen.Archive, WalletNavigation.backTarget(detailFromArchive))
    }
}
