package com.bookgen.app.ui.components

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.ui.theme.BookGenExtendedColors
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras
import com.bookgen.app.ui.theme.CodeMonoFontFamily
import com.bookgen.app.ui.theme.ReaderSerifFontFamily
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface MathNode {
    data class Row(val items: List<MathNode>) : MathNode
    data class Text(val text: String, val isUpright: Boolean = false, val isBold: Boolean = false) : MathNode
    data class Symbol(val glyph: String, val isLargeOp: Boolean = false) : MathNode
    data class Fraction(val numerator: MathNode, val denominator: MathNode) : MathNode
    data class Script(val base: MathNode, val subscript: MathNode?, val superscript: MathNode?) : MathNode
    data class Sqrt(val body: MathNode, val index: MathNode? = null) : MathNode
    data class Cases(val rows: List<List<MathNode>>) : MathNode
    data class OverBar(val body: MathNode) : MathNode
    data class UnderBrace(val body: MathNode, val label: MathNode?) : MathNode
}

sealed interface MathSegment {
    data class Prose(val text: String) : MathSegment
    data class Formula(val node: MathNode, val plainSummary: String) : MathSegment
    data object BlankSpacer : MathSegment
}

fun parseMathBlockSegments(raw: String): List<MathSegment> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()

    val segments = mutableListOf<MathSegment>()
    val lines = trimmed.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val lt = line.trim()
        if (lt.isEmpty()) {
            segments.add(MathSegment.BlankSpacer)
            i++
            continue
        }

        // Accumulate multi-line \begin{...}...\end{...} or unclosed brace LaTeX block cleanly
        if (lt.contains("\\begin{") || hasUnbalancedBraces(lt)) {
            val blockLines = mutableListOf(line)
            var depth = envDepthDelta(lt)
            var braceBalance = braceDelta(lt)
            i++
            while (i < lines.size && (depth > 0 || braceBalance > 0)) {
                val nextLine = lines[i]
                blockLines.add(nextLine)
                depth += envDepthDelta(nextLine)
                braceBalance += braceDelta(nextLine)
                i++
            }
            val joined = blockLines.joinToString("\n")
            val node = parseLatexToMathNode(joined)
            segments.add(MathSegment.Formula(node, mathNodeToPlainText(node)))
            continue
        }

        if (isNaturalLanguageOrProcedureLine(lt)) {
            segments.add(MathSegment.Prose(lt))
            i++
            continue
        }

        val node = parseLatexToMathNode(lt)
        segments.add(MathSegment.Formula(node, mathNodeToPlainText(node)))
        i++
    }
    return segments
}

private fun isNaturalLanguageOrProcedureLine(line: String): Boolean {
    if (Regex("""^\s*(手順\s*\d+|Step\s*\d+|\d+\.\s+\S)""").containsMatchIn(line) && !line.contains("\\frac")) {
        return true
    }
    if (line.startsWith("//") || line.startsWith("function ")) {
        return true
    }
    val hasBoldMarker = Regex("""\*[^\s*][^*]*\*""").containsMatchIn(line)
    if (hasBoldMarker && !line.contains("\\frac") && !line.contains("\\begin")) {
        return true
    }
    val hasJapaneseSentence = line.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }
    val hasStructuralLatex = line.contains("\\frac") || line.contains("\\sum") || line.contains("\\prod") ||
        line.contains("\\int") || line.contains("\\sqrt") || line.contains("\\lim") || line.contains("\\begin")
    if (hasJapaneseSentence && !hasStructuralLatex && (line.endsWith("。") || line.endsWith("：") || !line.contains('='))) {
        return true
    }
    return false
}

private fun envDepthDelta(line: String): Int {
    val begins = Regex("""\\begin\{""").findAll(line).count()
    val ends = Regex("""\\end\{""").findAll(line).count()
    return begins - ends
}

private fun braceDelta(line: String): Int {
    var delta = 0
    var i = 0
    while (i < line.length) {
        if (line[i] == '\\' && i + 1 < line.length) {
            i += 2
            continue
        }
        when (line[i]) {
            '{' -> delta++
            '}' -> delta--
        }
        i++
    }
    return delta
}

private fun hasUnbalancedBraces(line: String): Boolean = braceDelta(line) > 0

fun parseLatexToMathNode(raw: String): MathNode {
    val cleaned = raw.trim()
        .removePrefix("$$").removeSuffix("$$")
        .removePrefix("\\[").removeSuffix("\\]")
        .trim()
    return normalizeRow(parseSequence(cleaned, stopAtEndEnv = null).first)
}

private fun parseSequence(
    text: String,
    startIndex: Int = 0,
    stopAtEndEnv: String? = null,
): Pair<List<MathNode>, Int> {
    val nodes = mutableListOf<MathNode>()
    var i = startIndex
    val n = text.length

    while (i < n) {
        if (stopAtEndEnv != null && text.startsWith("\\end{$stopAtEndEnv}", i)) {
            i += "\\end{$stopAtEndEnv}".length
            return nodes to i
        }

        val ch = text[i]
        if (ch == '\r') {
            i++
            continue
        }
        if (ch == '\n' || ch == '\t') {
            appendSpaceIfNeeded(nodes)
            i++
            continue
        }
        if (ch == ' ') {
            appendSpaceIfNeeded(nodes)
            while (i < n && text[i] == ' ') i++
            continue
        }

        // Grouping { ... }
        if (ch == '{') {
            val arg = readLatexGroupOrAtom(text, i)
            i = arg.nextIndex
            nodes.add(parseLatexToMathNode(arg.content))
            continue
        }
        if (ch == '}') {
            i++
            continue
        }

        // Subscript / Superscript attached to preceding node
        if (ch == '_' || ch == '^') {
            val isSub = ch == '_'
            val arg = readLatexGroupOrAtom(text, i + 1)
            i = arg.nextIndex
            val scriptNode = parseLatexToMathNode(arg.content)
            val prev = if (nodes.isNotEmpty()) nodes.removeAt(nodes.lastIndex) else MathNode.Text("")
            val merged = when (prev) {
                is MathNode.Script -> if (isSub) {
                    prev.copy(subscript = scriptNode)
                } else {
                    prev.copy(superscript = scriptNode)
                }
                else -> if (isSub) {
                    MathNode.Script(base = prev, subscript = scriptNode, superscript = null)
                } else {
                    MathNode.Script(base = prev, subscript = null, superscript = scriptNode)
                }
            }
            nodes.add(merged)
            continue
        }

        // LaTeX commands
        if (ch == '\\') {
            if (i + 1 >= n) break
            val next = text[i + 1]
            when {
                next in charArrayOf('{', '}', '_', '%', '#', '&', '$') -> {
                    nodes.add(MathNode.Text(next.toString(), isUpright = true))
                    i += 2
                }
                next == '\\' -> {
                    // Row separator outside environment -> space/separator; consume optional [4pt]
                    i += 2
                    i = skipOptionalBracketArg(text, i)
                    appendSpaceIfNeeded(nodes)
                }
                next in charArrayOf(',', ';', ':', ' ') -> {
                    appendSpaceIfNeeded(nodes)
                    i += 2
                }
                next == '!' -> i += 2
                next == '|' -> {
                    nodes.add(MathNode.Symbol("‖"))
                    i += 2
                }
                next.isLetter() -> {
                    var end = i + 1
                    while (end < n && text[end].isLetter()) end++
                    val cmd = text.substring(i + 1, end)
                    i = end
                    when (cmd) {
                        "frac", "dfrac", "tfrac" -> {
                            val numArg = readLatexGroupOrAtom(text, i)
                            i = numArg.nextIndex
                            val denArg = readLatexGroupOrAtom(text, i)
                            i = denArg.nextIndex
                            nodes.add(
                                MathNode.Fraction(
                                    numerator = parseLatexToMathNode(numArg.content),
                                    denominator = parseLatexToMathNode(denArg.content),
                                ),
                            )
                        }
                        "sqrt" -> {
                            var idxNode: MathNode? = null
                            i = skipSpaces(text, i)
                            if (i < n && text[i] == '[') {
                                val close = text.indexOf(']', i + 1)
                                if (close > i) {
                                    idxNode = parseLatexToMathNode(text.substring(i + 1, close))
                                    i = close + 1
                                }
                            }
                            val bodyArg = readLatexGroupOrAtom(text, i)
                            i = bodyArg.nextIndex
                            nodes.add(MathNode.Sqrt(body = parseLatexToMathNode(bodyArg.content), index = idxNode))
                        }
                        "text", "mathrm", "textrm", "operatorname" -> {
                            val arg = readLatexGroupOrAtom(text, i)
                            i = arg.nextIndex
                            nodes.add(MathNode.Text(arg.content, isUpright = true))
                        }
                        "mathbf", "textbf" -> {
                            val arg = readLatexGroupOrAtom(text, i)
                            i = arg.nextIndex
                            nodes.add(MathNode.Text(arg.content, isUpright = true, isBold = true))
                        }
                        "mathcal" -> {
                            val arg = readLatexGroupOrAtom(text, i)
                            i = arg.nextIndex
                            nodes.add(MathNode.Text(arg.content))
                        }
                        "mathbb" -> {
                            val arg = readLatexGroupOrAtom(text, i)
                            i = arg.nextIndex
                            val mapped = LatexSymbols.BLACKBOARD_MAP[arg.content.trim()] ?: arg.content
                            nodes.add(MathNode.Text(mapped, isUpright = true))
                        }
                        "bar", "overline" -> {
                            val arg = readLatexGroupOrAtom(text, i)
                            i = arg.nextIndex
                            nodes.add(MathNode.OverBar(parseLatexToMathNode(arg.content)))
                        }
                        "underbrace" -> {
                            val bodyArg = readLatexGroupOrAtom(text, i)
                            i = bodyArg.nextIndex
                            var labelNode: MathNode? = null
                            val peek = skipSpaces(text, i)
                            if (peek < n && text[peek] == '_') {
                                val labelArg = readLatexGroupOrAtom(text, peek + 1)
                                i = labelArg.nextIndex
                                labelNode = parseLatexToMathNode(labelArg.content)
                            }
                            nodes.add(MathNode.UnderBrace(parseLatexToMathNode(bodyArg.content), labelNode))
                        }
                        "xrightarrow" -> {
                            i = skipOptionalBracketArg(text, i)
                            val labelArg = readLatexGroupOrAtom(text, i)
                            i = labelArg.nextIndex
                            nodes.add(
                                MathNode.Script(
                                    base = MathNode.Symbol("⟶", isLargeOp = true),
                                    subscript = null,
                                    superscript = if (labelArg.content.isNotBlank()) {
                                        parseLatexToMathNode(labelArg.content)
                                    } else null,
                                ),
                            )
                        }
                        "begin" -> {
                            val envArg = readLatexGroupOrAtom(text, i)
                            val envName = envArg.content.trim()
                            val envEndMarker = "\\end{$envName}"
                            val closeIdx = text.indexOf(envEndMarker, envArg.nextIndex)
                            val body = if (closeIdx >= 0) {
                                i = closeIdx + envEndMarker.length
                                text.substring(envArg.nextIndex, closeIdx)
                            } else {
                                i = n
                                text.substring(envArg.nextIndex)
                            }
                            val parsedRows = parseEnvironmentRows(body)
                            when (envName) {
                                "cases" -> nodes.add(MathNode.Cases(parsedRows))
                                else -> {
                                    parsedRows.forEachIndexed { rIdx, cols ->
                                        if (rIdx > 0) nodes.add(MathNode.Text("; ", isUpright = true))
                                        cols.forEachIndexed { cIdx, cell ->
                                            if (cIdx > 0) nodes.add(MathNode.Text("  ", isUpright = true))
                                            nodes.add(cell)
                                        }
                                    }
                                }
                            }
                        }
                        in LatexSymbols.SIZING_NOOPS -> Unit
                        in LatexSymbols.UPRIGHT_FUNCTIONS -> {
                            val isLarge = cmd in setOf("lim", "min", "max", "inf", "sup")
                            nodes.add(MathNode.Symbol(cmd, isLargeOp = isLarge))
                        }
                        in LatexSymbols.LARGE_OPERATORS -> {
                            val glyph = LatexSymbols.COMMAND_TO_UNICODE[cmd] ?: cmd
                            val supportsStacked = cmd in setOf("sum", "prod", "bigcup", "bigcap", "bigwedge", "bigvee")
                            nodes.add(MathNode.Symbol(glyph, isLargeOp = supportsStacked))
                        }
                        else -> {
                            val glyph = LatexSymbols.COMMAND_TO_UNICODE[cmd]
                            if (glyph != null) {
                                nodes.add(MathNode.Symbol(glyph))
                            } else {
                                nodes.add(MathNode.Text(cmd))
                            }
                        }
                    }
                }
                else -> {
                    nodes.add(MathNode.Text(next.toString(), isUpright = true))
                    i += 2
                }
            }
            continue
        }

        // Ordinary alphanumeric / punctuation / CJK runs
        val start = i
        val isDigitOrPunct = ch.isDigit() || ch in charArrayOf('(', ')', '[', ']', '=', '+', '-', '<', '>', ',', '.', ':', '/', '|')
        if (isDigitOrPunct) {
            while (i < n && (text[i].isDigit() || text[i] in charArrayOf('.', ','))) {
                i++
            }
            if (i == start) i++
            nodes.add(MathNode.Text(text.substring(start, i), isUpright = true))
        } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
            while (i < n && (text[i] in 'a'..'z' || text[i] in 'A'..'Z')) {
                i++
            }
            nodes.add(MathNode.Text(text.substring(start, i), isUpright = false))
        } else {
            while (i < n && !text[i].isWhitespace() && text[i] !in charArrayOf('\\', '{', '}', '_', '^') &&
                text[i] !in 'a'..'z' && text[i] !in 'A'..'Z' && !text[i].isDigit()
            ) {
                i++
            }
            if (i == start) i++
            nodes.add(MathNode.Text(text.substring(start, i), isUpright = true))
        }
    }

    return nodes to i
}

private fun parseEnvironmentRows(body: String): List<List<MathNode>> {
    val rawRows = splitTopLevelRowBreaks(body)
    return rawRows
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { rowStr ->
            splitTopLevelAlignments(rowStr).map { cell ->
                parseLatexToMathNode(cell.trim())
            }
        }
}

private fun splitTopLevelRowBreaks(text: String): List<String> {
    val rows = mutableListOf<String>()
    val sb = StringBuilder()
    var braceDepth = 0
    var i = 0
    while (i < text.length) {
        if (text[i] == '{' && text.getOrNull(i - 1) != '\\') {
            braceDepth++
        } else if (text[i] == '}' && text.getOrNull(i - 1) != '\\' && braceDepth > 0) {
            braceDepth--
        } else if (braceDepth == 0 && text.startsWith("\\\\", i)) {
            rows.add(sb.toString())
            sb.clear()
            i = skipOptionalBracketArg(text, i + 2)
            continue
        }
        sb.append(text[i])
        i++
    }
    if (sb.isNotEmpty()) rows.add(sb.toString())
    return rows
}

private fun splitTopLevelAlignments(row: String): List<String> {
    val cols = mutableListOf<String>()
    val sb = StringBuilder()
    var braceDepth = 0
    var i = 0
    while (i < row.length) {
        if (row[i] == '\\' && i + 1 < row.length) {
            sb.append(row[i]).append(row[i + 1])
            i += 2
            continue
        }
        when (row[i]) {
            '{' -> braceDepth++
            '}' -> if (braceDepth > 0) braceDepth--
            '&' -> if (braceDepth == 0) {
                cols.add(sb.toString())
                sb.clear()
                i++
                continue
            }
        }
        sb.append(row[i])
        i++
    }
    cols.add(sb.toString())
    return cols
}

private fun skipOptionalBracketArg(text: String, start: Int): Int {
    val idx = skipSpaces(text, start)
    if (idx < text.length && text[idx] == '[') {
        val close = text.indexOf(']', idx + 1)
        if (close > idx) return close + 1
    }
    return start
}

private fun appendSpaceIfNeeded(nodes: MutableList<MathNode>) {
    val last = nodes.lastOrNull()
    if (last == null) return
    if (last is MathNode.Text && last.text.endsWith(" ")) return
    nodes.add(MathNode.Text(" ", isUpright = true))
}

private fun normalizeRow(items: List<MathNode>): MathNode = when (items.size) {
    0 -> MathNode.Text("", isUpright = true)
    1 -> items.first()
    else -> MathNode.Row(items)
}

fun mathNodeToPlainText(node: MathNode): String = when (node) {
    is MathNode.Text -> node.text
    is MathNode.Symbol -> node.glyph
    is MathNode.Row -> node.items.joinToString("") { mathNodeToPlainText(it) }
    is MathNode.Fraction -> "(${mathNodeToPlainText(node.numerator)}) / (${mathNodeToPlainText(node.denominator)})"
    is MathNode.Script -> buildString {
        append(mathNodeToPlainText(node.base))
        node.subscript?.let { append("_").append(mathNodeToPlainText(it)) }
        node.superscript?.let { append("^").append(mathNodeToPlainText(it)) }
    }
    is MathNode.Sqrt -> {
        val prefix = node.index?.let { "${mathNodeToPlainText(it)}√" } ?: "√"
        "$prefix(${mathNodeToPlainText(node.body)})"
    }
    is MathNode.Cases -> node.rows.joinToString(" | ") { row ->
        row.joinToString(" ") { mathNodeToPlainText(it) }
    }
    is MathNode.OverBar -> "${mathNodeToPlainText(node.body)}\u0304"
    is MathNode.UnderBrace -> buildString {
        append(mathNodeToPlainText(node.body))
        node.label?.let { append(" [").append(mathNodeToPlainText(it)).append("]") }
    }
}

@Composable
fun NativeMathBlockView(
    latex: String,
    baseFontSizeSp: Float,
    modifier: Modifier = Modifier,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    extendedColors: BookGenExtendedColors = BookGenThemeExtras.colors,
    richColors: RichTextColors = rememberRichTextColors(),
    clipboard: Clipboard = LocalClipboard.current,
    onCopiedFormula: () -> Unit = {},
) {
    val segments = remember(latex) { parseMathBlockSegments(latex) }
    val scope = rememberCoroutineScope()
    val scheme = colorScheme
    val extended = extendedColors
    val isPureProseStem = remember(segments) {
        segments.isNotEmpty() && segments.all { it is MathSegment.Prose || it is MathSegment.BlankSpacer }
    }
    val readableSummary = remember(segments) {
        segments.joinToString("\n") { seg ->
            when (seg) {
                is MathSegment.Formula -> seg.plainSummary
                is MathSegment.Prose -> formatInlineRichText(seg.text, MarkupFormat.ASCIIDOC).text
                MathSegment.BlankSpacer -> ""
            }
        }.trim()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = readableSummary.ifBlank { "数式ブロック" }
            },
        shape = BookGenRadius.Control,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, extended.borderSubtle),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isPureProseStem) "解説 / 定義" else "数式",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.primary,
                )
                EditorialTooltipBox(label = "数式をコピー") {
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("BookGen Formula", latex)),
                                )
                                onCopiedFormula()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "数式をコピー",
                            tint = scheme.onSurfaceVariant.copy(alpha = 0.78f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                segments.forEach { segment ->
                    when (segment) {
                        MathSegment.BlankSpacer -> Spacer(Modifier.height(4.dp))
                        is MathSegment.Prose -> {
                            val styledProse = remember(segment.text, baseFontSizeSp, richColors) {
                                formatInlineRichText(
                                    raw = segment.text,
                                    format = MarkupFormat.ASCIIDOC,
                                    baseFontSize = baseFontSizeSp.sp,
                                    colors = richColors,
                                )
                            }
                            SelectionContainer {
                                Text(
                                    text = styledProse,
                                    style = TextStyle(
                                        fontFamily = ReaderSerifFontFamily,
                                        fontSize = (baseFontSizeSp * 0.94f).sp,
                                        lineHeight = (baseFontSizeSp * 1.48f).sp,
                                        color = scheme.onSurface,
                                    ),
                                )
                            }
                        }
                        is MathSegment.Formula -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                SelectionContainer {
                                    MathNodeView(
                                        node = segment.node,
                                        fontSizeSp = (baseFontSizeSp * 1.04f).coerceIn(14f, 26f),
                                        textColor = scheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MathNodeView(
    node: MathNode,
    fontSizeSp: Float,
    textColor: Color,
) {
    when (node) {
        is MathNode.Row -> RenderMathRow(node.items, fontSizeSp, textColor)
        is MathNode.Text, is MathNode.Symbol -> {
            val inline = buildAnnotatedString { appendInlineNode(node, textColor) }
            Text(
                text = inline,
                style = mathTextStyle(fontSizeSp, textColor),
                softWrap = false,
            )
        }
        is MathNode.Fraction -> {
            val childFont = (fontSizeSp * 0.86f).coerceAtLeast(11f)
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MathNodeView(node.numerator, childFont, textColor)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.2.dp)
                        .background(textColor.copy(alpha = 0.85f)),
                )
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MathNodeView(node.denominator, childFont, textColor)
                }
            }
        }
        is MathNode.Script -> {
            val baseSym = node.base as? MathNode.Symbol
            if (baseSym?.isLargeOp == true) {
                val limitFont = (fontSizeSp * 0.68f).coerceAtLeast(10f)
                val opFont = if (baseSym.glyph.length == 1 && !baseSym.glyph[0].isLetter()) {
                    fontSizeSp * 1.35f
                } else {
                    fontSizeSp
                }
                Column(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    node.superscript?.let { sup ->
                        MathNodeView(sup, limitFont, textColor)
                    }
                    MathNodeView(node.base, opFont, textColor)
                    node.subscript?.let { sub ->
                        MathNodeView(sub, limitFont, textColor)
                    }
                }
            } else if (isInlineTextCapable(node)) {
                val inline = buildAnnotatedString { appendInlineNode(node, textColor) }
                Text(
                    text = inline,
                    style = mathTextStyle(fontSizeSp, textColor),
                    softWrap = false,
                )
            } else {
                ComplexScriptLayout(
                    base = { MathNodeView(node.base, fontSizeSp, textColor) },
                    superscript = node.superscript?.let { sup ->
                        { MathNodeView(sup, (fontSizeSp * 0.72f).coerceAtLeast(10f), textColor) }
                    },
                    subscript = node.subscript?.let { sub ->
                        { MathNodeView(sub, (fontSizeSp * 0.72f).coerceAtLeast(10f), textColor) }
                    },
                )
            }
        }
        is MathNode.Sqrt -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                node.index?.let { idx ->
                    MathNodeView(idx, (fontSizeSp * 0.65f).coerceAtLeast(10f), textColor)
                }
                Text(
                    text = "√",
                    style = mathTextStyle(fontSizeSp * 1.15f, textColor),
                )
                Box(
                    modifier = Modifier
                        .drawWithCache {
                            val lineColor = textColor.copy(alpha = 0.85f)
                            val y = 1.dp.toPx()
                            val strokeW = 1.2.dp.toPx()
                            val endX = size.width
                            onDrawBehind {
                                drawLine(
                                    color = lineColor,
                                    start = Offset(0f, y),
                                    end = Offset(endX, y),
                                    strokeWidth = strokeW,
                                )
                            }
                        }
                        .padding(top = 3.dp, start = 2.dp, end = 3.dp),
                ) {
                    MathNodeView(node.body, fontSizeSp, textColor)
                }
            }
        }
        is MathNode.Cases -> {
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .fillMaxHeight()
                        .drawWithCache {
                            val w = size.width
                            val h = size.height.coerceAtLeast(24.dp.toPx())
                            val midY = h / 2f
                            val path = Path().apply {
                                moveTo(w * 0.88f, 2f)
                                cubicTo(w * 0.35f, 2f, w * 0.55f, midY * 0.88f, w * 0.14f, midY)
                                cubicTo(w * 0.55f, midY * 1.12f, w * 0.35f, h - 2f, w * 0.88f, h - 2f)
                            }
                            val stroke = Stroke(
                                width = 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            )
                            val braceColor = textColor.copy(alpha = 0.85f)
                            onDrawBehind {
                                drawPath(
                                    path = path,
                                    color = braceColor,
                                    style = stroke,
                                )
                            }
                        },
                )
                Column(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    node.rows.forEach { cols ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            cols.forEach { cell ->
                                MathNodeView(cell, (fontSizeSp * 0.94f).coerceAtLeast(12f), textColor)
                            }
                        }
                    }
                }
            }
        }
        is MathNode.OverBar -> {
            Box(
                modifier = Modifier
                    .drawWithCache {
                        val lineColor = textColor.copy(alpha = 0.85f)
                        val y = 1.dp.toPx()
                        val strokeW = 1.1.dp.toPx()
                        val endX = size.width
                        onDrawBehind {
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, y),
                                end = Offset(endX, y),
                                strokeWidth = strokeW,
                            )
                        }
                    }
                    .padding(top = 3.dp),
            ) {
                MathNodeView(node.body, fontSizeSp, textColor)
            }
        }
        is MathNode.UnderBrace -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MathNodeView(node.body, fontSizeSp, textColor)
                node.label?.let { lbl ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(textColor.copy(alpha = 0.45f)),
                    )
                    MathNodeView(lbl, (fontSizeSp * 0.7f).coerceAtLeast(10f), textColor)
                }
            }
        }
    }
}

@Composable
private fun RenderMathRow(
    items: List<MathNode>,
    fontSizeSp: Float,
    textColor: Color,
) {
    // Coalesce adjacent inline-text-capable nodes into one typographically aligned AnnotatedString run.
    val grouped = remember(items) { groupRowItems(items) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        grouped.forEach { entry ->
            when (entry) {
                is RowEntry.InlineRun -> {
                    val annotated = buildAnnotatedString {
                        entry.nodes.forEach { appendInlineNode(it, textColor) }
                    }
                    Text(
                        text = annotated,
                        style = mathTextStyle(fontSizeSp, textColor),
                        softWrap = false,
                    )
                }
                is RowEntry.BlockNode -> {
                    MathNodeView(entry.node, fontSizeSp, textColor)
                }
            }
        }
    }
}

private sealed interface RowEntry {
    data class InlineRun(val nodes: List<MathNode>) : RowEntry
    data class BlockNode(val node: MathNode) : RowEntry
}

private fun groupRowItems(items: List<MathNode>): List<RowEntry> {
    val result = mutableListOf<RowEntry>()
    val inlineBuffer = mutableListOf<MathNode>()
    fun flushInline() {
        if (inlineBuffer.isNotEmpty()) {
            result.add(RowEntry.InlineRun(inlineBuffer.toList()))
            inlineBuffer.clear()
        }
    }
    for (item in items) {
        if (item is MathNode.Row) {
            flushInline()
            result.addAll(groupRowItems(item.items))
        } else if (isInlineTextCapable(item)) {
            inlineBuffer.add(item)
        } else {
            flushInline()
            result.add(RowEntry.BlockNode(item))
        }
    }
    flushInline()
    return result
}

private fun isInlineTextCapable(node: MathNode): Boolean = when (node) {
    is MathNode.Text -> true
    is MathNode.Symbol -> !node.isLargeOp
    is MathNode.Row -> node.items.all { isInlineTextCapable(it) }
    is MathNode.Script -> {
        val baseSym = node.base as? MathNode.Symbol
        (baseSym?.isLargeOp != true) &&
            isInlineTextCapable(node.base) &&
            (node.subscript == null || isInlineTextCapable(node.subscript)) &&
            (node.superscript == null || isInlineTextCapable(node.superscript))
    }
    is MathNode.Fraction,
    is MathNode.Sqrt,
    is MathNode.Cases,
    is MathNode.OverBar,
    is MathNode.UnderBrace,
    -> false
}

private fun AnnotatedString.Builder.appendInlineNode(node: MathNode, textColor: Color) {
    when (node) {
        is MathNode.Text -> {
            val style = when {
                node.isBold -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal)
                node.isUpright -> SpanStyle(fontStyle = FontStyle.Normal)
                else -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            withStyle(style) {
                append(node.text)
            }
        }
        is MathNode.Symbol -> {
            val upright = node.glyph.length > 1 || !node.glyph[0].isLetter()
            val needsSpacePad = node.glyph in BINARY_SPACED_SYMBOLS
            withStyle(
                SpanStyle(
                    fontStyle = if (upright) FontStyle.Normal else FontStyle.Italic,
                    color = textColor,
                ),
            ) {
                if (needsSpacePad) append('\u2009')
                append(node.glyph)
                if (needsSpacePad) append('\u2009')
            }
        }
        is MathNode.Row -> node.items.forEach { appendInlineNode(it, textColor) }
        is MathNode.Script -> {
            appendInlineNode(node.base, textColor)
            node.subscript?.let { sub ->
                withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.72.em)) {
                    appendInlineNode(sub, textColor)
                }
            }
            node.superscript?.let { sup ->
                withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.72.em)) {
                    appendInlineNode(sup, textColor)
                }
            }
        }
        else -> append(mathNodeToPlainText(node))
    }
}

@Composable
private fun ComplexScriptLayout(
    base: @Composable () -> Unit,
    superscript: (@Composable () -> Unit)?,
    subscript: (@Composable () -> Unit)?,
) {
    Layout(
        content = {
            base()
            superscript?.invoke()
            subscript?.invoke()
        },
    ) { measurables, constraints ->
        val basePlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        var nextIdx = 1
        val supPlaceable = if (superscript != null) measurables[nextIdx++].measure(constraints.copy(minWidth = 0, minHeight = 0)) else null
        val subPlaceable = if (subscript != null) measurables[nextIdx].measure(constraints.copy(minWidth = 0, minHeight = 0)) else null

        val topExtra = ((supPlaceable?.height ?: 0) * 0.4f).roundToInt()
        val bottomExtra = ((subPlaceable?.height ?: 0) * 0.4f).roundToInt()
        val scriptWidth = max(supPlaceable?.width ?: 0, subPlaceable?.width ?: 0)
        val totalWidth = basePlaceable.width + scriptWidth
        val totalHeight = max(
            basePlaceable.height + topExtra + bottomExtra,
            (supPlaceable?.height ?: 0) + (subPlaceable?.height ?: 0),
        )

        layout(totalWidth, totalHeight) {
            basePlaceable.placeRelative(0, topExtra)
            supPlaceable?.placeRelative(basePlaceable.width, 0)
            subPlaceable?.placeRelative(
                basePlaceable.width,
                (topExtra + basePlaceable.height - (subPlaceable.height * 0.6f).roundToInt()).coerceAtLeast(0),
            )
        }
    }
}

private val BINARY_SPACED_SYMBOLS: Set<String> = setOf(
    "≈", "≒", "≤", "≥", "≠", "≪", "≫", "∈", "∉", "⊆", "⪯", "→", "⟶", "⇒", "⇔", "×", "÷", "·", "∼", "∝",
)

private fun mathTextStyle(fontSizeSp: Float, textColor: Color): TextStyle = TextStyle(
    fontFamily = ReaderSerifFontFamily,
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp * 1.4f).sp,
    color = textColor,
)
