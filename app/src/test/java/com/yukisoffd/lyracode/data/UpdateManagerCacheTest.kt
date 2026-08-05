package com.yukisoffd.lyracode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class UpdateManagerCacheTest {
    @Test
    fun removesEveryOldUpdateArtifactAndKeepsPendingPackage() {
        val directory = Files.createTempDirectory("lyra-update-cache").toFile()
        try {
            val pending = directory.resolve("LyraCode-3.0.apk").apply { writeText("pending") }
            val previous = directory.resolve("LyraCode-2.0.apk").apply { writeText("previous") }
            val muchOlder = directory.resolve("LyraCode-1.0.APK").apply { writeText("older") }
            val partial = directory.resolve("LyraCode-4.0.apk.part").apply { writeText("partial") }
            val unrelated = directory.resolve("metadata.json").apply { writeText("keep") }

            val deleted = deleteCachedUpdateArtifacts(directory, keepFile = pending)

            assertEquals(3, deleted)
            assertTrue(pending.exists())
            assertFalse(previous.exists())
            assertFalse(muchOlder.exists())
            assertFalse(partial.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun removesAllUpdateArtifactsAfterUpgradeCompletes() {
        val directory = Files.createTempDirectory("lyra-update-cache").toFile()
        try {
            val previous = directory.resolve("LyraCode-2.0.apk").apply { writeText("previous") }
            val muchOlder = directory.resolve("LyraCode-1.0.apk").apply { writeText("older") }

            val deleted = deleteCachedUpdateArtifacts(directory)

            assertEquals(2, deleted)
            assertFalse(previous.exists())
            assertFalse(muchOlder.exists())
            assertTrue(directory.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
