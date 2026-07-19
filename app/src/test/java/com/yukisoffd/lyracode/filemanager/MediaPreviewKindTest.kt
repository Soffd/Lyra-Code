package com.yukisoffd.lyracode.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MediaPreviewKindTest {
    @Test
    fun `recognizes common media extensions without case sensitivity`() {
        assertEquals(MediaPreviewKind.IMAGE, mediaPreviewKind(File("photo.HEIC")))
        assertEquals(MediaPreviewKind.AUDIO, mediaPreviewKind(File("song.FLAC")))
        assertEquals(MediaPreviewKind.VIDEO, mediaPreviewKind(File("movie.MKV")))
    }

    @Test
    fun `leaves text and unknown formats to the existing open flow`() {
        assertNull(mediaPreviewKind(File("notes.md")))
        assertNull(mediaPreviewKind(File("archive.bin")))
    }
}
