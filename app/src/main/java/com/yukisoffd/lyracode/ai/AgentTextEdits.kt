package com.yukisoffd.lyracode.ai

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toolTextArgument(name: String): String {
    val exact = when {
        !has(name) || isNull(name) -> null
        opt(name) is String -> getString(name)
        else -> throw IllegalArgumentException("$name 必须是字符串")
    }
    val lines = textLinesArgument("${name}_lines")
    val content = when {
        !exact.isNullOrEmpty() -> exact
        lines != null -> lines
        exact != null -> exact
        else -> ""
    }
    val mayAddTrailingNewline = name == "content" || name == "new_content"
    return if (
        mayAddTrailingNewline &&
        optBoolean("ensure_trailing_newline", false) &&
        !content.endsWith('\n')
    ) {
        "$content\n"
    } else {
        content
    }
}

private fun JSONObject.textLinesArgument(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return when (val value = opt(name)) {
        is JSONArray -> value.joinTextLines()
        is String -> value.decodeTextLinesString()
        else -> throw IllegalArgumentException("$name 必须是字符串数组或可解析的字符串")
    }
}

private fun JSONArray.joinTextLines(): String = buildString {
    for (index in 0 until length()) {
        if (index > 0) append('\n')
        append(optString(index))
    }
}

private fun String.decodeTextLinesString(): String {
    val raw = this
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""

    val parsed = runCatching {
        if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
            JSONArray(trimmed)
        } else {
            // Some model/provider combinations serialize the array value as:
            // "first line", "second line", ""
            // without the surrounding JSON brackets.
            JSONArray("[$trimmed]")
        }
    }.getOrNull()
    if (parsed != null) return parsed.joinTextLines()

    // A provider may also collapse an array into one ordinary multiline string.
    // Keeping that text is safer than silently treating the supplied value as absent.
    return raw.replace("\r\n", "\n").replace('\r', '\n')
}

internal fun applyExactTextReplacement(
    source: String,
    oldContent: String,
    newContent: String,
    expectedReplacements: Int = 1,
): String {
    require(oldContent.isNotEmpty()) { "old_content 不能为空；删除内容时请提供要删除的原文，并将 new_content 留空" }
    require(expectedReplacements > 0) { "expected_replacements 必须大于 0" }
    val matches = source.nonOverlappingOccurrences(oldContent)
    require(matches == expectedReplacements) {
        "精确替换失败：预期匹配 $expectedReplacements 处，实际匹配 $matches 处。请重新读取文件并提供唯一、完整的 old_content。"
    }
    return source.replace(oldContent, newContent)
}

internal fun applyLineRangeReplacement(
    source: String,
    startLine: Int,
    endLine: Int,
    newContent: String,
): String {
    require(startLine >= 1) { "start_line 必须从 1 开始" }
    require(endLine >= startLine) { "end_line 不能小于 start_line" }

    val lineStarts = ArrayList<Int>().apply {
        add(0)
        source.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }
    require(startLine <= lineStarts.size && endLine <= lineStarts.size) {
        "行范围超出文件：文件共 ${lineStarts.size} 行，请重新读取相关片段后再编辑。"
    }

    val startOffset = lineStarts[startLine - 1]
    val endOffset = if (endLine < lineStarts.size) lineStarts[endLine] else source.length
    val newline = if ("\r\n" in source) "\r\n" else "\n"
    val normalizedReplacement = newContent
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", newline)
    val replacement = if (endLine < lineStarts.size && normalizedReplacement.isNotEmpty() && !normalizedReplacement.endsWith(newline)) {
        normalizedReplacement + newline
    } else {
        normalizedReplacement
    }
    return source.replaceRange(startOffset, endOffset, replacement)
}

private fun String.nonOverlappingOccurrences(needle: String): Int {
    var count = 0
    var offset = 0
    while (offset <= length - needle.length) {
        val match = indexOf(needle, offset)
        if (match < 0) break
        count++
        offset = match + needle.length
    }
    return count
}
