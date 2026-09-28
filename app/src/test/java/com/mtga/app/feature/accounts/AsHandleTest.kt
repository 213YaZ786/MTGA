package com.mtga.app.feature.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsHandleTest {

    @Test
    fun `a typed handle is read as is`() {
        assertEquals("nasa", AccountsViewModel.asHandle("@nasa "))
    }

    @Test
    fun `a pasted profile or post link gives the account`() {
        assertEquals("nasa", AccountsViewModel.asHandle("https://x.com/nasa"))
        assertEquals("nasa", AccountsViewModel.asHandle("x.com/nasa?s=20"))
        assertEquals("nasa", AccountsViewModel.asHandle("https://twitter.com/nasa/status/2104470073905713351"))
    }

    @Test
    fun `a link to something else is not an account`() {
        assertNull(AccountsViewModel.asHandle("https://x.com/search?q=space"))
        assertNull(AccountsViewModel.asHandle("https://example.com/nasa"))
    }
}
