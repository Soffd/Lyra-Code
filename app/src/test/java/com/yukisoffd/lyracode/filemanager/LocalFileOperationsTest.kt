package com.yukisoffd.lyracode.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LocalFileOperationsTest {
    @Test
    fun totalSizeIncludesNestedFiles() {
        val root = Files.createTempDirectory("lyra-folder-size-test").toFile()
        try {
            root.resolve("first.txt").writeBytes(ByteArray(7))
            root.resolve("nested").apply { mkdirs() }.resolve("second.bin").writeBytes(ByteArray(13))

            assertEquals(20L, LocalFileOperations.totalSize(root).getOrThrow())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createFileAndBatchOperations() {
        val root = Files.createTempDirectory("lyra-file-manager-test").toFile()
        try {
            val source = root.resolve("source").apply { mkdirs() }
            val copyDestination = root.resolve("copy").apply { mkdirs() }
            val moveDestination = root.resolve("move").apply { mkdirs() }
            val first = LocalFileOperations.createFile(source, "first.txt").getOrThrow().apply { writeText("first") }
            val second = LocalFileOperations.createFile(source, "second.kt").getOrThrow().apply { writeText("second") }

            val copied = LocalFileOperations.copyAll(listOf(first, second), copyDestination).getOrThrow()
            assertEquals(listOf("first.txt", "second.kt"), copied.map { it.name })
            assertTrue(copyDestination.resolve("first.txt").isFile)
            assertTrue(copyDestination.resolve("second.kt").isFile)

            val moved = LocalFileOperations.moveAll(listOf(first, second), moveDestination).getOrThrow()
            assertEquals(2, moved.size)
            assertFalse(first.exists())
            assertFalse(second.exists())

            LocalFileOperations.deleteAll(moved, root).getOrThrow()
            assertTrue(moveDestination.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
