package com.yukisoffd.lyracode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Android17CompatibilityTest {
    @Test
    fun `local network permission is required starting at API 37`() {
        assertFalse(Android17Compatibility.requiresLocalNetworkPermission(36))
        assertTrue(Android17Compatibility.requiresLocalNetworkPermission(37))
        assertTrue(Android17Compatibility.requiresLocalNetworkPermission(38))
    }
}
