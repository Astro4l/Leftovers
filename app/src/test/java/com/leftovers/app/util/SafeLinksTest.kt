package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeLinksTest {
    @Test
    fun acceptsOrdinaryHttpsLinks() {
        assertEquals("https://example.com/p?id=1", SafeLinks.web("https://example.com/p?id=1"))
        assertEquals("https://shop.example.co.ke/item", SafeLinks.web("  https://shop.example.co.ke/item  "))
    }

    @Test
    fun refusesAnythingThatCouldStartAnotherApp() {
        listOf(
            "intent://scan/#Intent;scheme=zxing;end", "tel:+254700000000", "sms:+254700000000?body=hi", "file:///data/data/x",
            "content://com.leftovers.app.files/x", "javascript:alert(1)", "market://details?id=x", "http://example.com", "ftp://example.com/x",
        ).forEach { assertEquals(it, null, SafeLinks.web(it)) }
    }

    @Test
    fun refusesDisguisedOrBrokenAddresses() {
        listOf(
            "https://user:pass@example.com/", "https://example.com:8443/", "https://localhost/", "https://192", "https://example.com/a b",
            "https://example.com/\nsecond", "", "   ", "https://", "not a url", "https://." ,
        ).forEach { assertEquals(it, null, SafeLinks.web(it)) }
        assertEquals(null, SafeLinks.web("https://example.com/" + "a".repeat(3000)))
        assertEquals(null, SafeLinks.web(null))
    }

    @Test
    fun updateNoticesOnlyPointAtTheProjectsOwnReleases() {
        assertEquals(
            "https://github.com/saisaharsh3/Leftovers/releases/tag/v1.9.0",
            SafeLinks.releasePage("https://github.com/saisaharsh3/Leftovers/releases/tag/v1.9.0"),
        )
        listOf(
            "https://github.com/someone-else/Leftovers/releases/tag/v1.9.0", "https://evil.example/saisaharsh3/Leftovers/releases",
            "https://github.com.evil.example/saisaharsh3/Leftovers/", "https://github.com/saisaharsh3/LeftoversX", "intent://x#Intent;end",
        ).forEach { assertEquals(it, null, SafeLinks.releasePage(it)) }
    }
}
