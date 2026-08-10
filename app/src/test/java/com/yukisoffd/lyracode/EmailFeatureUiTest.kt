package com.yukisoffd.lyracode

import org.junit.Assert.assertTrue
import org.junit.Test

class EmailFeatureUiTest {
    @Test
    fun emailDependencyLicenseIsBundled() {
        assertTrue(LicenseTexts.EPL_2_0.contains("Eclipse Public License - v 2.0"))
        assertTrue(LicenseTexts.EPL_2_0.contains("https://www.eclipse.org/legal/epl-2.0/"))
    }
}
