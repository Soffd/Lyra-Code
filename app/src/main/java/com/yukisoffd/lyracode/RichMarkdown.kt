package com.yukisoffd.lyracode

import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.layout.SubcomposeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser
import ru.noties.jlatexmath.JLatexMathDrawable
import java.io.File
import kotlin.math.max

private val richMarkdownFlavour by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

private val richMarkdownParser by lazy {
    MarkdownParser(richMarkdownFlavour)
}

private val inlineLatexRegex = Regex("\\\\\\((.+?)\\\\\\)")
private val blockLatexRegex = Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL)
private val codeBlockRegex = Regex("```[\\s\\S]*?```|`[^`\n]*`", RegexOption.DOT_MATCHES_ALL)
private val latexBlockLineBreakRegex = Regex("""[ \t]*\r?\n[ \t]*""")

private data class RichMarkdownParseResult(
    val content: String,
    val root: ASTNode,
)

@Composable
@OptIn(ExperimentalCoroutinesApi::class)
internal fun RichMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    var data by remember(markdown) { mutableStateOf(parseRichMarkdown(markdown)) }
    val currentMarkdown by rememberUpdatedState(markdown)
    LaunchedEffect(Unit) {
        snapshotFlow { currentMarkdown }
            .distinctUntilChanged()
            .mapLatest { parseRichMarkdown(it) }
            .flowOn(Dispatchers.Default)
            .catch { data = parseRichMarkdown(currentMarkdown, fallback = true) }
            .collect { data = it }
    }

    ProvideTextStyle(style) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.root.children.fastForEach { child ->
                RichMarkdownNode(child, data.content)
            }
        }
    }
}

private fun parseRichMarkdown(content: String, fallback: Boolean = false): RichMarkdownParseResult {
    val preprocessed = if (fallback) content else preProcessMarkdownLatex(content)
    return RichMarkdownParseResult(
        content = preprocessed,
        root = richMarkdownParser.buildMarkdownTreeFromString(preprocessed),
    )
}

private fun preProcessMarkdownLatex(content: String): String {
    val codeRanges = codeBlockRegex.findAll(content).map { it.range }.toList()
    fun inCodeBlock(index: Int): Boolean = codeRanges.any { index in it }
    var result = inlineLatexRegex.replace(content) { match ->
        if (inCodeBlock(match.range.first)) {
            match.value
        } else {
            "$" + match.groupValues[1] + "$"
        }
    }
    result = blockLatexRegex.replace(result) { match ->
        if (inCodeBlock(match.range.first)) {
            match.value
        } else {
            "$$" + match.groupValues[1].trim().replace(latexBlockLineBreakRegex, " ") + "$$"
        }
    }
    return result
}

@Composable
private fun RichMarkdownNode(
    node: ASTNode,
    content: String,
    listLevel: Int = 0,
) {
    when (node.type) {
        MarkdownElementTypes.MARKDOWN_FILE -> node.children.fastForEach { RichMarkdownNode(it, content, listLevel) }
        MarkdownElementTypes.PARAGRAPH -> RichParagraph(node, content)
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> RichHeading(node, content)
        MarkdownElementTypes.UNORDERED_LIST -> RichList(node, content, ordered = false, level = listLevel)
        MarkdownElementTypes.ORDERED_LIST -> RichList(node, content, ordered = true, level = listLevel)
        MarkdownElementTypes.BLOCK_QUOTE -> RichQuote(node, content)
        MarkdownElementTypes.CODE_FENCE -> RichCodeFence(node, content)
        MarkdownElementTypes.CODE_BLOCK -> RichCodeBlock(node.getTextInNode(content))
        GFMElementTypes.TABLE -> RichTable(node, content)
        GFMElementTypes.BLOCK_MATH -> RichMathBlock(node.getTextInNode(content))
        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        )
        MarkdownTokenTypes.EOL -> Unit
        else -> {
            if (node.children.isEmpty()) {
                val text = node.getTextInNode(content).trim()
                if (text.isNotEmpty()) Text(text)
            } else {
                node.children.fastForEach { RichMarkdownNode(it, content, listLevel) }
            }
        }
    }
}

@Composable
private fun RichHeading(node: ASTNode, content: String) {
    val level = when (node.type) {
        MarkdownElementTypes.ATX_1 -> 1
        MarkdownElementTypes.ATX_2 -> 2
        MarkdownElementTypes.ATX_3 -> 3
        MarkdownElementTypes.ATX_4 -> 4
        MarkdownElementTypes.ATX_5 -> 5
        else -> 6
    }
    val fontSize = when (level) {
        1 -> 25.sp
        2 -> 22.sp
        3 -> 20.sp
        4 -> 18.sp
        else -> 16.sp
    }
    val child = node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT } ?: return
    ProvideTextStyle(LocalTextStyle.current.copy(fontSize = fontSize, fontWeight = FontWeight.Bold)) {
        Box(Modifier.padding(top = if (level <= 2) 12.dp else 8.dp, bottom = 4.dp)) {
            RichInlineText(child.children.ifEmpty { listOf(child) }, content)
        }
    }
}

@Composable
private fun RichParagraph(node: ASTNode, content: String) {
    if (node.findChildOfTypeRecursive(MarkdownElementTypes.IMAGE, GFMElementTypes.BLOCK_MATH) != null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.children.fastForEach { child -> RichInlineAsBlock(child, content) }
        }
        return
    }
    RichInlineText(node.children, content)
}

@Composable
private fun RichInlineAsBlock(node: ASTNode, content: String) {
    when (node.type) {
        MarkdownElementTypes.IMAGE -> RichImage(node, content)
        GFMElementTypes.BLOCK_MATH -> RichMathBlock(node.getTextInNode(content))
        else -> RichInlineText(listOf(node), content)
    }
}

@Composable
private fun RichQuote(node: ASTNode, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(0.dp, Color.Transparent))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f))
            .padding(start = 10.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        node.children.fastForEach { RichMarkdownNode(it, content) }
    }
}

@Composable
private fun RichList(node: ASTNode, content: String, ordered: Boolean, level: Int) {
    Column(Modifier.padding(start = (level * 12).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var index = 1
        node.children.fastForEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                val marker = if (ordered) {
                    child.findChildOfTypeRecursive(MarkdownTokenTypes.LIST_NUMBER)?.getTextInNode(content)?.trim().orEmpty()
                        .ifBlank { "${index}." }
                } else {
                    when (level % 3) {
                        0 -> "•"
                        1 -> "◦"
                        else -> "▪"
                    }
                }
                RichListItem(child, content, marker, level)
                index++
            }
        }
    }
}

@Composable
private fun RichListItem(node: ASTNode, content: String, marker: String, level: Int) {
    val direct = node.children.filterNot {
        it.type == MarkdownElementTypes.UNORDERED_LIST ||
            it.type == MarkdownElementTypes.ORDERED_LIST ||
            it.type == MarkdownTokenTypes.LIST_BULLET ||
            it.type == MarkdownTokenTypes.LIST_NUMBER
    }
    val nested = node.children.filter {
        it.type == MarkdownElementTypes.UNORDERED_LIST || it.type == MarkdownElementTypes.ORDERED_LIST
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(marker, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                direct.fastForEach { RichMarkdownNode(it, content, level) }
            }
        }
        nested.fastForEach { RichMarkdownNode(it, content, level + 1) }
    }
}

@Composable
private fun RichCodeFence(node: ASTNode, content: String) {
    val raw = node.getTextInNode(content)
    val firstLine = raw.lineSequence().firstOrNull().orEmpty()
    val language = firstLine.removePrefix("```").trim().substringBefore(' ').ifBlank { "text" }
    val start = node.children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.startOffset ?: return
    val end = node.children.lastOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset ?: start
    RichCodeBlock(content.substring(start, end).trimEnd(), language)
}

@Composable
private fun RichCodeBlock(code: String, language: String = "text") {
    val clipboard = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.50f))
            .border(BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)), RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colorScheme.surfaceVariant.copy(alpha = 0.66f))
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language.ifBlank { "text" },
                modifier = Modifier.weight(1f),
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = uiText("复制代码"), modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = remember(code, language, colorScheme) { highlightedCode(code, language, colorScheme) },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RichTable(node: ASTNode, content: String) {
    val header = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
    val rows = node.children.filter { it.type == GFMElementTypes.ROW }
    val headerCells = header?.children?.filter { it.type == GFMTokenTypes.CELL }?.map { it.getTextInNode(content).trim() }.orEmpty()
    val rowCells = rows.map { row ->
        row.children.filter { it.type == GFMTokenTypes.CELL }.map { it.getTextInNode(content).trim() }
    }
    val columnCount = max(headerCells.size, rowCells.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) return
    val context = LocalContext.current
    val csv = remember(headerCells, rowCells, columnCount) { buildCsv(headerCells, rowCells, columnCount) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(
            onClick = {
                val name = "lyra_table_${System.currentTimeMillis()}.csv"
                val result = saveCsvToDownloads(context, name, csv)
                Toast.makeText(
                    context,
                    result.fold({ uiText("已导出到 Download/") + name }, { uiText("导出失败: ") + it.message.orEmpty() }),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = uiText("导出 CSV"), modifier = Modifier.size(19.dp))
        }
    }
    RichDataTable(
        headers = List(columnCount) { column ->
            { RichTableCell(headerCells.getOrNull(column).orEmpty(), header = true) }
        },
        rows = rowCells.map { row ->
            List(columnCount) { column ->
                { RichTableCell(row.getOrNull(column).orEmpty(), header = false) }
            }
        },
        columnMinWidths = List(columnCount) { 92.dp },
        columnMaxWidths = List(columnCount) { 240.dp },
    )
}

@Composable
private fun RichTableCell(text: String, header: Boolean) {
    ProvideTextStyle(
        LocalTextStyle.current.copy(
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            fontWeight = if (header) FontWeight.SemiBold else LocalTextStyle.current.fontWeight,
        ),
    ) {
        RichMarkdownContent(text, modifier = Modifier.widthIn(min = 60.dp))
    }
}

@Composable
private fun RichDataTable(
    headers: List<@Composable () -> Unit>,
    rows: List<List<@Composable () -> Unit>>,
    columnMinWidths: List<Dp>,
    columnMaxWidths: List<Dp>,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(4.dp))
            .horizontalScroll(scroll),
    ) {
        SubcomposeLayout { constraints ->
            val columnCount = max(headers.size, rows.maxOfOrNull { it.size } ?: 0)
            val rowCount = rows.size
            if (columnCount == 0) return@SubcomposeLayout layout(0, 0) {}
            val infinity = Constraints.Infinity
            val minWidths = IntArray(columnCount) { columnMinWidths.getOrNull(it)?.roundToPx() ?: 0 }
            val maxWidths = IntArray(columnCount) { columnMaxWidths.getOrNull(it)?.roundToPx() ?: Int.MAX_VALUE }
            val colWidths = IntArray(columnCount)

            fun measureCell(key: String, header: Boolean, row: Int, col: Int, width: Int? = null, minHeight: Int = 0): Placeable {
                val measurables = subcompose(key) {
                    RichCellBox(
                        background = if (header) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f) else Color.Transparent,
                    ) {
                        if (header) headers.getOrNull(col)?.invoke() else rows[row].getOrNull(col)?.invoke()
                    }
                }
                val cellConstraints = if (width == null) {
                    Constraints(0, maxWidths[col], 0, infinity)
                } else {
                    Constraints(width, width, minHeight, infinity)
                }
                return measurables.first().measure(cellConstraints)
            }

            val headerFirst = Array(columnCount) { col ->
                measureCell("header-first-$col", header = true, row = 0, col = col).also {
                    colWidths[col] = max(minWidths[col], it.width).coerceAtMost(maxWidths[col])
                }
            }
            val bodyFirst = Array(rowCount * columnCount) { index ->
                val row = index / columnCount
                val col = index % columnCount
                measureCell("body-first-$row-$col", header = false, row = row, col = col).also {
                    colWidths[col] = max(colWidths[col], max(minWidths[col], it.width)).coerceAtMost(maxWidths[col])
                }
            }
            val headerHeight = headerFirst.maxOfOrNull { it.height } ?: 0
            val rowHeights = IntArray(rowCount) { row ->
                (0 until columnCount).maxOf { col -> bodyFirst[row * columnCount + col].height }
            }
            val headerFinal = Array(columnCount) { col ->
                measureCell("header-final-$col", header = true, row = 0, col = col, width = colWidths[col], minHeight = headerHeight)
            }
            val bodyFinal = Array(rowCount * columnCount) { index ->
                val row = index / columnCount
                val col = index % columnCount
                measureCell("body-final-$row-$col", header = false, row = row, col = col, width = colWidths[col], minHeight = rowHeights[row])
            }
            val tableWidth = colWidths.sum()
            val tableHeight = headerHeight + rowHeights.sum()
            val finalWidth = tableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
            val finalHeight = tableHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(finalWidth, finalHeight) {
                var x = 0
                for (col in 0 until columnCount) {
                    headerFinal[col].placeRelative(x, 0)
                    x += colWidths[col]
                }
                var y = headerHeight
                for (row in 0 until rowCount) {
                    x = 0
                    for (col in 0 until columnCount) {
                        bodyFinal[row * columnCount + col].placeRelative(x, y)
                        x += colWidths[col]
                    }
                    y += rowHeights[row]
                }
            }
        }
    }
}

@Composable
private fun RichCellBox(background: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .then(if (background != Color.Transparent) Modifier.background(background) else Modifier)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun RichImage(node: ASTNode, content: String) {
    val alt = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content)?.trim('[', ']').orEmpty()
    val url = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content).orEmpty()
    if (url.isNotBlank()) {
        MarkdownMediaPreview(alt.ifBlank { uiText("媒体文件") }, url)
    }
}

@Composable
private fun RichInlineText(nodes: List<ASTNode>, content: String) {
    val colorScheme = MaterialTheme.colorScheme
    val textStyle = LocalTextStyle.current
    val density = LocalDensity.current
    val inlineContents = remember { mutableStateMapOf<String, InlineTextContent>() }
    val key = remember(nodes, content) { nodes.joinToString("|") { "${it.startOffset}:${it.endOffset}:${it.type}" } }
    val annotated = remember(key, colorScheme, textStyle, density) {
        inlineContents.clear()
        buildAnnotatedString {
            nodes.fastForEach {
                appendInlineMarkdownNode(
                    it,
                    content,
                    inlineContents,
                    colorScheme,
                    density,
                    textStyle,
                )
            }
        }
    }
    Text(
        text = annotated,
        inlineContent = inlineContents,
        modifier = Modifier.fillMaxWidth(),
        style = textStyle.copy(color = if (textStyle.color == Color.Unspecified) colorScheme.onSurface else textStyle.color),
        softWrap = true,
        overflow = TextOverflow.Visible,
    )
}

private fun AnnotatedString.Builder.appendInlineMarkdownNode(
    node: ASTNode,
    content: String,
    inlineContents: MutableMap<String, InlineTextContent>,
    colorScheme: androidx.compose.material3.ColorScheme,
    density: Density,
    style: TextStyle,
) {
    when (node.type) {
        MarkdownElementTypes.EMPH -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            node.children.trimMarkdownMarkers(MarkdownTokenTypes.EMPH, 1).fastForEach {
                appendInlineMarkdownNode(it, content, inlineContents, colorScheme, density, style)
            }
        }
        MarkdownElementTypes.STRONG -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            node.children.trimMarkdownMarkers(MarkdownTokenTypes.EMPH, 2).fastForEach {
                appendInlineMarkdownNode(it, content, inlineContents, colorScheme, density, style)
            }
        }
        GFMElementTypes.STRIKETHROUGH -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            node.children.trimMarkdownMarkers(GFMTokenTypes.TILDE, 2).fastForEach {
                appendInlineMarkdownNode(it, content, inlineContents, colorScheme, density, style)
            }
        }
        MarkdownElementTypes.CODE_SPAN -> withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 0.95.em,
                background = colorScheme.surfaceVariant.copy(alpha = 0.75f),
                color = colorScheme.primary,
            ),
        ) {
            append(" ")
            append(node.getTextInNode(content).trim('`'))
            append(" ")
        }
        MarkdownElementTypes.INLINE_LINK -> appendInlineLink(node, content, colorScheme)
        MarkdownElementTypes.AUTOLINK -> appendInlineLink(node, content, colorScheme)
        GFMElementTypes.INLINE_MATH -> {
            val formula = node.getTextInNode(content)
            val id = "math-${node.startOffset}-${node.endOffset}"
            val fontSize = resolvedFontSize(style)
            val size = with(density) {
                assumeLatexSize(formula, fontSize.toPx()).let {
                    (it.width() + 12).toSp() to (it.height() + 8).toSp()
                }
            }
            inlineContents[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = size.first,
                    height = size.second,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                RichLatexText(
                    latex = formula,
                    fontSize = fontSize,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            appendInlineContent(id, formula)
        }
        MarkdownElementTypes.IMAGE -> append(node.getTextInNode(content))
        MarkdownTokenTypes.EOL -> append("\n")
        else -> {
            if (node is LeafASTNode) {
                append(node.getTextInNode(content))
            } else {
                node.children.fastForEach {
                    appendInlineMarkdownNode(it, content, inlineContents, colorScheme, density, style)
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineLink(
    node: ASTNode,
    content: String,
    colorScheme: androidx.compose.material3.ColorScheme,
) {
    val destination = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content)
        ?: node.getTextInNode(content).trim('<', '>')
    val label = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content)?.trim('[', ']')
        ?: destination
    withLink(LinkAnnotation.Url(destination)) {
        withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
            append(label)
        }
    }
}

@Composable
private fun RichMathBlock(formula: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.CenterStart,
    ) {
        RichLatexText(
            latex = formula,
            color = LocalContentColor.current,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun RichLatexText(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    val density = LocalDensity.current
    val resolvedStyle = style.merge(
        fontSize = resolvedFontSize(style, fontSize),
        color = if (color == Color.Unspecified) LocalContentColor.current else color,
    )
    val drawable = remember(latex, resolvedStyle, density) {
        runCatching {
            with(density) {
                JLatexMathDrawable.builder(processLatex(latex))
                    .textSize(resolvedStyle.fontSize.toPx())
                    .color(resolvedStyle.color.toArgb())
                    .background(Color.Transparent.toArgb())
                    .padding(0)
                    .align(JLatexMathDrawable.ALIGN_LEFT)
                    .build()
            }
        }.getOrNull()
    }
    if (drawable == null) {
        Text(text = latex, modifier = modifier, style = resolvedStyle, fontFamily = FontFamily.Monospace)
        return
    }
    with(density) {
        Canvas(
            modifier = modifier.size(
                width = drawable.bounds.width().toDp(),
                height = drawable.bounds.height().toDp(),
            ),
        ) {
            drawable.draw(drawContext.canvas.nativeCanvas)
        }
    }
}

private fun assumeLatexSize(latex: String, fontSizePx: Float): Rect = runCatching {
    val processed = processLatex(latex)
    val bounds = JLatexMathDrawable.builder(processed)
        .textSize(fontSizePx)
        .padding(0)
        .build()
        .bounds
    if (bounds.width() > 0 && bounds.height() > 0) {
        bounds
    } else {
        Rect(0, 0, (processed.length * fontSizePx * 0.55f).toInt().coerceAtLeast(1), fontSizePx.toInt().coerceAtLeast(1))
    }
}.getOrDefault(Rect(0, 0, 0, 0))

private fun processLatex(raw: String): String {
    val text = raw.trim()
    return when {
        text.startsWith("$$") && text.endsWith("$$") -> text.removeSurrounding("$$").trim()
        text.startsWith("$") && text.endsWith("$") -> text.removeSurrounding("$").trim()
        text.startsWith("\\(") && text.endsWith("\\)") -> text.removeSurrounding("\\(", "\\)").trim()
        text.startsWith("\\[") && text.endsWith("\\]") -> text.removeSurrounding("\\[", "\\]").trim()
        else -> text
    }
}

private fun resolvedFontSize(style: TextStyle, override: TextUnit = TextUnit.Unspecified): TextUnit {
    if (override.isSpecified) return override
    if (style.fontSize.isSpecified) return style.fontSize
    return 16.sp
}

private fun highlightedCode(
    code: String,
    language: String,
    colorScheme: androidx.compose.material3.ColorScheme,
): AnnotatedString = buildAnnotatedString {
    val normalized = language.lowercase()
    append(code)
    if (code.isBlank()) return@buildAnnotatedString
    when {
        normalized in setOf("json", "jsonc") -> {
            Regex(""""(?:\\.|[^"\\])*"""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.primary), it.range.first, it.range.last + 1)
            }
            Regex("""\b(true|false|null)\b""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.tertiary), it.range.first, it.range.last + 1)
            }
            Regex("""(?<![\w.])-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?(?![\w.])""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.secondary), it.range.first, it.range.last + 1)
            }
        }
        normalized in setOf("kt", "kotlin", "java", "js", "javascript", "ts", "typescript", "py", "python", "go", "rs", "rust", "c", "cpp", "swift") -> {
            val keywords = when (normalized) {
                "py", "python" -> "False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield"
                "js", "javascript", "ts", "typescript" -> "async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|from|function|if|import|in|instanceof|let|new|null|return|super|switch|this|throw|try|typeof|undefined|var|void|while|with|yield"
                else -> "abstract|as|break|case|catch|class|const|continue|data|default|do|else|enum|expect|export|extends|false|finally|for|fun|if|import|in|inline|interface|internal|is|lateinit|new|null|object|open|override|package|private|protected|public|return|sealed|static|super|switch|this|throw|true|try|typealias|val|var|when|while"
            }
            Regex("""//.*|#.*|/\*[\s\S]*?\*/""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.outline), it.range.first, it.range.last + 1)
            }
            Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.primary), it.range.first, it.range.last + 1)
            }
            Regex("""\b($keywords)\b""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.tertiary, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
            }
        }
        normalized in setOf("sh", "bash", "shell", "zsh", "powershell", "ps1") -> {
            Regex("""(^|\s)(sudo|cd|ls|cat|grep|find|git|npm|python|python3|pip|chmod|chown|mkdir|rm|cp|mv|echo|export|set)\b""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.tertiary, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
            }
            Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""").findAll(code).forEach {
                addStyle(SpanStyle(color = colorScheme.primary), it.range.first, it.range.last + 1)
            }
        }
    }
}

private fun buildCsv(headers: List<String>, rows: List<List<String>>, columnCount: Int): String {
    val lines = mutableListOf<List<String>>()
    if (headers.any { it.isNotBlank() }) {
        lines += List(columnCount) { headers.getOrNull(it).orEmpty() }
    }
    rows.forEach { row ->
        lines += List(columnCount) { row.getOrNull(it).orEmpty() }
    }
    return lines.joinToString("\n") { cells -> cells.joinToString(",") { csvCell(it) } }
}

private fun csvCell(value: String): String {
    val clean = value.replace("\r\n", "\n").replace('\r', '\n')
    val escaped = clean.replace("\"", "\"\"")
    return if (escaped.any { it == ',' || it == '"' || it == '\n' }) "\"$escaped\"" else escaped
}

private fun saveCsvToDownloads(context: Context, fileName: String, csv: String): Result<Unit> = runCatching {
    val bytes = csv.toByteArray(Charsets.UTF_8)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error(uiText("无法创建下载文件"))
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error(uiText("无法写入下载文件"))
    } else {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).writeBytes(bytes)
    }
}

private fun ASTNode.getTextInNode(text: String): String {
    return text.substring(startOffset.coerceIn(0, text.length), endOffset.coerceIn(0, text.length))
}

private fun ASTNode.findChildOfTypeRecursive(vararg types: IElementType): ASTNode? {
    if (type in types) return this
    children.fastForEach { child ->
        val found = child.findChildOfTypeRecursive(*types)
        if (found != null) return found
    }
    return null
}

private fun List<ASTNode>.trimMarkdownMarkers(type: IElementType, size: Int): List<ASTNode> {
    if (isEmpty() || size <= 0) return this
    var start = 0
    var end = this.size
    var count = 0
    while (start < end && count < size && this[start].type == type) {
        start++
        count++
    }
    count = 0
    while (end > start && count < size && this[end - 1].type == type) {
        end--
        count++
    }
    return subList(start, end)
}

