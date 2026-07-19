package com.yukisoffd.lyracode.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LocalFileSearchTest {
    @Test
    fun `searches files and folders recursively with type filters`() {
        val root = Files.createTempDirectory("lyra-file-search").toFile()
        try {
            val nested = root.resolve("Pictures").apply { mkdirs() }
            nested.resolve("summer-photo.jpg").writeText("image")
            root.resolve("summer-notes.txt").writeText("notes")

            val all = LocalFileOperations.search(root, "summer").getOrThrow()
            assertEquals(setOf("summer-photo.jpg", "summer-notes.txt"), all.map { it.name }.toSet())

            val folders = LocalFileOperations.search(
                root,
                "pictures",
                includeFiles = false,
                includeDirectories = true,
            ).getOrThrow()
            assertEquals(listOf("Pictures"), folders.map { it.name })

            val filesOnly = LocalFileOperations.search(
                root,
                "summer",
                includeFiles = true,
                includeDirectories = false,
            ).getOrThrow()
            assertTrue(filesOnly.all { !it.directory })
        } finally {
            root.deleteRecursively()
        }
    }
}
