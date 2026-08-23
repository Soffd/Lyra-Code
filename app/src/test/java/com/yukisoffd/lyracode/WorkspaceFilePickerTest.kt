package com.yukisoffd.lyracode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilePickerTest {
    @Test
    fun opensForHashPrefixWithWorkspace() {
        assertTrue(shouldShowWorkspaceFilePicker("#", enabled = true, hasWorkspace = true))
        assertFalse(shouldShowWorkspaceFilePicker("#file.kt", enabled = true, hasWorkspace = false))
        assertFalse(shouldShowWorkspaceFilePicker("hello #file.kt", enabled = true, hasWorkspace = true))
        assertFalse(shouldShowWorkspaceFilePicker("#", enabled = false, hasWorkspace = true))
    }

    @Test
    fun keepsLegacyAtPrefixCompatible() {
        assertTrue(shouldShowWorkspaceFilePicker("@", enabled = true, hasWorkspace = true))
        assertEquals("MainActivity", workspaceFilePickerQuery("@MainActivity explain this"))
    }

    @Test
    fun parsesAndRemovesWorkspaceShortcutPrefix() {
        assertEquals("MainActivity", workspaceFilePickerQuery("#MainActivity explain this"))
        assertEquals("explain this", removeWorkspaceMentionPrefix("#MainActivity explain this"))
        assertNull(workspaceFilePickerQuery("explain #MainActivity"))
    }

    @Test
    fun slashSkillPickerOpensEvenWhenCandidateListIsEmpty() {
        assertTrue(shouldShowSkillPicker("/", enabled = true))
        assertTrue(shouldShowSkillPicker("/documents", enabled = true))
        assertFalse(shouldShowSkillPicker("hello /documents", enabled = true))
        assertFalse(shouldShowSkillPicker("/", enabled = false))
        assertEquals("documents", skillPickerQuery("/documents summarize this"))
        assertEquals("summarize this", removeSkillSlashPrefix("/documents summarize this"))
    }
}
