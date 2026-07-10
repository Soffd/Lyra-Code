package com.yukisoffd.lyracode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilePickerTest {
    @Test
    fun opensOnlyForAtPrefixWithWorkspace() {
        assertTrue(shouldShowWorkspaceFilePicker("@", enabled = true, hasWorkspace = true))
        assertFalse(shouldShowWorkspaceFilePicker("@file.kt", enabled = true, hasWorkspace = false))
        assertFalse(shouldShowWorkspaceFilePicker("hello @file.kt", enabled = true, hasWorkspace = true))
        assertFalse(shouldShowWorkspaceFilePicker("@", enabled = false, hasWorkspace = true))
    }
}
