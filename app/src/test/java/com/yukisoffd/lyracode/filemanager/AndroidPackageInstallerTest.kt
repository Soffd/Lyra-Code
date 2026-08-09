package com.yukisoffd.lyracode.filemanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidPackageInstallerTest {
    @Test
    fun recognizesAndroidPackageFormatsCaseInsensitively() {
        assertTrue(isAndroidPackageFile(File("release.APK")))
        assertTrue(isAndroidPackageFile(File("release.apks")))
        assertTrue(isAndroidPackageFile(File("release.xapk")))
        assertTrue(isAndroidPackageFile(File("release.apkm")))
        assertFalse(isAndroidPackageFile(File("release.zip")))
    }

    @Test
    fun usesInstallMultipleAndQuotesSplitPaths() {
        val command = buildPackageManagerInstallCommand(
            listOf(File("/tmp/base apk.apk"), File("/tmp/config.en.apk")),
        )

        assertTrue(command.startsWith("pm install-multiple -r "))
        assertTrue(command.contains("base apk.apk'"))
        assertTrue(command.contains("config.en.apk'"))
    }
}
