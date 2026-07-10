package com.yukisoffd.lyracode.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchMatcherTest {
    @Test
    fun matchesExactFileName() {
        val matcher = FileSearchMatcher("test.py")

        assertTrue(matcher.matches("test.py", "scripts/test.py"))
    }

    @Test
    fun matchesNameWithoutSeparators() {
        val matcher = FileSearchMatcher("MainActivity")

        assertTrue(matcher.matches("MainActivity.kt", "app/src/main/java/com/example/MainActivity.kt"))
    }

    @Test
    fun matchesPathTermsInAnySegment() {
        val matcher = FileSearchMatcher("src main py")

        assertTrue(matcher.matches("app.py", "project/src/main/app.py"))
    }

    @Test
    fun matchesWeakFuzzyName() {
        val matcher = FileSearchMatcher("mnact")

        assertTrue(matcher.matches("MainActivity.kt", "app/src/main/java/MainActivity.kt"))
    }

    @Test
    fun blankQueryReturnsBrowsableFiles() {
        val matcher = FileSearchMatcher("")

        assertTrue(matcher.matches("MainActivity.kt", "app/src/main/MainActivity.kt"))
        assertEquals(1, matcher.score("MainActivity.kt", "app/src/main/MainActivity.kt"))
    }

    @Test
    fun scoreRejectsPartialTermMatches() {
        val matcher = FileSearchMatcher("main missing")

        assertFalse(matcher.matches("MainActivity.kt", "app/src/main/MainActivity.kt"))
        assertEquals(0, matcher.score("MainActivity.kt", "app/src/main/MainActivity.kt"))
    }

    @Test
    fun searchesTheEntireCachedIndex() {
        val files = (0 until 10_000).map { index ->
            WorkspaceFileReference(
                name = "generated-$index.tmp",
                relativePath = "build/generated/$index/generated-$index.tmp",
                uri = "file://generated-$index.tmp",
            )
        } + WorkspaceFileReference(
            name = "NeedleFile.kt",
            relativePath = "app/src/main/NeedleFile.kt",
            uri = "file://NeedleFile.kt",
        )

        val results = searchWorkspaceFileIndex(files, "NeedleFile.kt", limit = 80)

        assertEquals(1, results.size)
        assertEquals("app/src/main/NeedleFile.kt", results.single().relativePath)
    }
}
