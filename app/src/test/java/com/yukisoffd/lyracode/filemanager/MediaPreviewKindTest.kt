package com.yukisoffd.lyracode.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
        assertNull(mediaPreviewKind(File("component.ts")))
    }

    @Test
    fun `distinguishes mpeg transport streams from typescript`() {
        val root = Files.createTempDirectory("lyra-ts-kind-test").toFile()
        try {
            val typeScript = root.resolve("component.ts").apply { writeText("export const answer = 42\n") }
            val transportStream = root.resolve("recording.ts").apply {
                writeBytes(ByteArray(188 * 3).also { bytes ->
                    bytes[0] = 0x47
                    bytes[188] = 0x47
                    bytes[376] = 0x47
                })
            }

            assertNull(mediaPreviewKind(typeScript))
            assertEquals(MediaPreviewKind.VIDEO, mediaPreviewKind(transportStream))
        } finally {
            root.deleteRecursively()
        }
    }
}
