package com.yukisoffd.lyracode.ssh

/** Converts a phone keyboard code point plus one-shot terminal modifiers into SSH input bytes. */
internal object TerminalInputEncoder {
    fun encode(codePoint: Int, ctrl: Boolean, alt: Boolean): ByteArray {
        var bytes = when {
            codePoint == '\n'.code -> byteArrayOf('\r'.code.toByte())
            ctrl -> controlCode(codePoint)?.let { byteArrayOf(it) }
                ?: String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
            else -> String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
        }
        if (alt) bytes = byteArrayOf(ESCAPE) + bytes
        return bytes
    }

    private fun controlCode(codePoint: Int): Byte? {
        // ASCII control combinations are case-insensitive: Ctrl+c and Ctrl+C both produce ETX.
        return when (codePoint) {
            ' '.code, '@'.code -> 0x00
            in 'a'.code..'z'.code -> (codePoint - 'a'.code + 1).toByte()
            in 'A'.code..'Z'.code -> (codePoint - 'A'.code + 1).toByte()
            '['.code -> 0x1b
            '\\'.code -> 0x1c
            ']'.code -> 0x1d
            '^'.code -> 0x1e
            '_'.code -> 0x1f
            '?'.code -> 0x7f
            else -> null
        }
    }

    private const val ESCAPE: Byte = 0x1b
}
