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

    @Test
    fun appliesBasePathBeforeTheResultLimit() {
        val files = (0 until 300).map { index ->
            WorkspaceFileReference(
                name = "settings.json",
                relativePath = "outside/$index/settings.json",
                uri = "file://outside-$index",
            )
        } + WorkspaceFileReference(
            name = "settings.json",
            relativePath = "target/nested/settings.json",
            uri = "file://target",
        )

        val results = searchWorkspaceFileIndex(
            files = files,
            query = "settings.json",
            limit = 1,
            basePath = "target",
        )

        assertEquals(listOf("target/nested/settings.json"), results.map { it.relativePath })
    }

    @Test
    fun includesDirectoriesOnlyWhenRequested() {
        val directory = WorkspaceFileReference(
            name = "generated",
            relativePath = "build/generated",
            uri = "file://generated",
            directory = true,
        )

        assertTrue(searchWorkspaceFileIndex(listOf(directory), "generated", limit = 10).isEmpty())
        assertEquals(
            listOf(directory),
            searchWorkspaceFileIndex(
                listOf(directory),
                "generated",
                limit = 10,
                includeDirectories = true,
            ),
        )
    }

    @Test
    fun boundedRankingKeepsTheBestMatch() {
        val fuzzyMatches = (0 until 1_000).map { index ->
            WorkspaceFileReference(
                name = "NeedleFileBackup-$index.kt",
                relativePath = "archive/$index/NeedleFileBackup-$index.kt",
                uri = "file://backup-$index",
            )
        }
        val exactMatch = WorkspaceFileReference(
            name = "NeedleFile",
            relativePath = "src/NeedleFile",
            uri = "file://exact",
        )

        val result = searchWorkspaceFileIndex(fuzzyMatches + exactMatch, "NeedleFile", limit = 1)

        assertEquals(exactMatch, result.single())
    }

    @Test
    fun indexedSearchFallsBackToFuzzyMatchingForTypos() {
        val file = WorkspaceFileReference(
            name = "MainActivity.kt",
            relativePath = "app/src/main/MainActivity.kt",
            uri = "file://main",
        )

        val result = searchWorkspaceFileIndex(listOf(file), "mnact", limit = 10)

        assertEquals(listOf(file), result)
    }

    @Test
    fun blankIndexSearchReturnsOnlyTheRequestedInitialPage() {
        val files = (0 until 10_000).map { index ->
            WorkspaceFileReference(
                name = "file-$index.kt",
                relativePath = "src/file-$index.kt",
                uri = "file://file-$index.kt",
            )
        }

        val result = searchWorkspaceFileIndex(files, query = "", limit = 80)

        assertEquals(80, result.size)
    }

    @Test
    fun prioritizesSourceDirectoriesAheadOfGeneratedTrees() {
        assertTrue(shouldDeferWorkspaceIndexDirectory("node_modules"))
        assertTrue(shouldDeferWorkspaceIndexDirectory("app/build"))
        assertTrue(shouldDeferWorkspaceIndexDirectory("project/.git"))
        assertFalse(shouldDeferWorkspaceIndexDirectory("app/src"))
        assertFalse(shouldDeferWorkspaceIndexDirectory("docs"))
    }

    @Test
    fun recognizesAncestorsAndDescendantsOfAScopedSearchPath() {
        assertTrue(workspaceIndexDirectoryMayContainBase("", "app/src"))
        assertTrue(workspaceIndexDirectoryMayContainBase("app", "app/src"))
        assertTrue(workspaceIndexDirectoryMayContainBase("app/src/main", "app/src"))
        assertFalse(workspaceIndexDirectoryMayContainBase("docs", "app/src"))
    }

    @Test
    fun quickPickerResultsRequireASpecificStrongMatch() {
        assertTrue(shouldQuickReturnFromWorkspaceIndex("Main", score = 80))
        assertTrue(shouldQuickReturnFromWorkspaceIndex("Activity", score = 70))
        assertFalse(shouldQuickReturnFromWorkspaceIndex("ma", score = 100))
        assertFalse(shouldQuickReturnFromWorkspaceIndex("src/main", score = 50))
    }
}
