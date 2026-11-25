package com.bookgen.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.ui.theme.BookGenThemeExtras
import com.bookgen.app.ui.theme.CodeMonoFontFamily
import com.bookgen.app.ui.theme.ReaderSerifFontFamily

@Immutable
data class RichTextColors(
    val codeBackground: Color = Color(0x18666666),
    val codeForeground: Color = Color(0xFFB33E24),
    val mathForeground: Color = Color(0xFF1F4E79),
    val accentForeground: Color = Color(0xFF8B4513),
)

@Composable
fun rememberRichTextColors(): RichTextColors {
    val extended = BookGenThemeExtras.colors
    val scheme = MaterialTheme.colorScheme
    return RichTextColors(
        codeBackground = extended.codeSurface.copy(alpha = if (extended.isDarkAppearance) 0.85f else 0.08f),
        codeForeground = if (extended.isDarkAppearance) extended.onCodeSurface else scheme.primary,
        mathForeground = scheme.primary,
        accentForeground = scheme.secondary,
    )
}

internal object LatexSymbols {
    val COMMAND_TO_UNICODE: Map<String, String> = mapOf(
        // Greek lowercase
        "alpha" to "α",
        "beta" to "β",
        "gamma" to "γ",
        "delta" to "δ",
        "epsilon" to "ε",
        "varepsilon" to "ε",
        "zeta" to "ζ",
        "eta" to "η",
        "theta" to "θ",
        "vartheta" to "ϑ",
        "iota" to "ι",
        "kappa" to "κ",
        "lambda" to "λ",
        "mu" to "μ",
        "nu" to "ν",
        "xi" to "ξ",
        "pi" to "π",
        "varpi" to "ϖ",
        "rho" to "ρ",
        "varrho" to "ϱ",
        "sigma" to "σ",
        "varsigma" to "ς",
        "tau" to "τ",
        "upsilon" to "υ",
        "phi" to "φ",
        "varphi" to "ϕ",
        "chi" to "χ",
        "psi" to "ψ",
        "omega" to "ω",
        // Greek uppercase
        "Gamma" to "Γ",
        "Delta" to "Δ",
        "Theta" to "Θ",
        "Lambda" to "Λ",
        "Xi" to "Ξ",
        "Pi" to "Π",
        "Sigma" to "Σ",
        "Phi" to "Φ",
        "Psi" to "Ψ",
        "Omega" to "Ω",
        // Operators, relations, sets & logic
        "cdot" to "·",
        "cdots" to "⋯",
        "ldots" to "…",
        "dots" to "…",
        "times" to "×",
        "div" to "÷",
        "pm" to "±",
        "mp" to "∓",
        "circ" to "∘",
        "oplus" to "⊕",
        "approx" to "≈",
        "fallingdotseq" to "≒",
        "sim" to "∼",
        "simeq" to "≃",
        "cong" to "≅",
        "equiv" to "≡",
        "propto" to "∝",
        "le" to "≤",
        "leq" to "≤",
        "ge" to "≥",
        "geq" to "≥",
        "neq" to "≠",
        "ne" to "≠",
        "ll" to "≪",
        "gg" to "≫",
        "preceq" to "⪯",
        "succeq" to "⪰",
        "infty" to "∞",
        "to" to "→",
        "rightarrow" to "→",
        "leftarrow" to "←",
        "longrightarrow" to "⟶",
        "longleftarrow" to "⟵",
        "leftrightarrow" to "↔",
        "Rightarrow" to "⇒",
        "Leftarrow" to "⇐",
        "implies" to "⇒",
        "iff" to "⇔",
        "mapsto" to "↦",
        "in" to "∈",
        "notin" to "∉",
        "ni" to "∋",
        "subset" to "⊂",
        "subseteq" to "⊆",
        "supset" to "⊃",
        "supseteq" to "⊇",
        "cup" to "∪",
        "cap" to "∩",
        "bigcup" to "⋃",
        "bigcap" to "⋂",
        "setminus" to "∖",
        "emptyset" to "∅",
        "varnothing" to "∅",
        "forall" to "∀",
        "exists" to "∃",
        "nexists" to "∄",
        "land" to "∧",
        "lor" to "∨",
        "bigwedge" to "⋀",
        "bigvee" to "⋁",
        "neg" to "¬",
        "lnot" to "¬",
        "partial" to "∂",
        "nabla" to "∇",
        "mid" to " | ",
        "parallel" to "∥",
        "colon" to ":",
        // Large operators
        "sum" to "∑",
        "prod" to "∏",
        "int" to "∫",
        // Brackets / delimiters
        "lceil" to "⌈",
        "rceil" to "⌉",
        "lfloor" to "⌊",
        "rfloor" to "⌋",
        "langle" to "⟨",
        "rangle" to "⟩",
        // Spacing
        "quad" to "  ",
        "qquad" to "    ",
    )

    val BLACKBOARD_MAP: Map<String, String> = mapOf(
        "R" to "ℝ",
        "N" to "ℕ",
        "Z" to "ℤ",
        "Q" to "ℚ",
        "C" to "ℂ",
        "P" to "ℙ",
        "E" to "𝔼",
    )

    val UPRIGHT_FUNCTIONS: Set<String> = setOf(
        "log", "ln", "exp", "max", "min", "lim", "inf", "sup",
        "arg", "bmod", "mod", "Pr", "sin", "cos", "tan", "det", "dim",
    )

    val SIZING_NOOPS: Set<String> = setOf(
        "left", "right", "bigl", "bigr", "Bigr", "Bigl", "big", "Big", "displaystyle",
    )

    val LARGE_OPERATORS: Set<String> = setOf("sum", "prod", "int", "bigcup", "bigcap", "bigwedge", "bigvee")
}

fun formatInlineRichText(
    raw: String,
    format: MarkupFormat,
    baseFontSize: TextUnit = 16.sp,
    colors: RichTextColors = RichTextColors(),
): AnnotatedString = buildAnnotatedString {
    appendFormattedSegment(
        text = raw,
        format = format,
        baseFontSize = baseFontSize,
        colors = colors,
    )
}

private fun AnnotatedString.Builder.appendFormattedSegment(
    text: String,
    format: MarkupFormat,
    baseFontSize: TextUnit,
    colors: RichTextColors,
) {
    var i = 0
    val n = text.length
    while (i < n) {
        // 1. LaTeX inline delimiter \( ... \)
        if (text.startsWith("\\(", i)) {
            val end = text.indexOf("\\)", i + 2)
            if (end > i + 2) {
                appendInlineMathSpan(text.substring(i + 2, end), colors)
                i = end + 2
                continue
            }
        }

        // 2. Backslash escapes (\*, \_, \$, \`) and leaked bare math symbols (\epsilon, \Delta)
        if (text[i] == '\\' && i + 1 < n) {
            val next = text[i + 1]
            if (next in charArrayOf('*', '_', '`', '$', '\\', '+')) {
                append(next)
                i += 2
                continue
            }
            if (next.isLetter()) {
                var end = i + 1
                while (end < n && text[end].isLetter()) {
                    end++
                }
                val cmd = text.substring(i + 1, end)
                val replacement = LatexSymbols.COMMAND_TO_UNICODE[cmd]
                if (replacement != null) {
                    append(replacement)
                    i = end
                    continue
                }
            }
        }

        // 3. Backtick-wrapped inline stem/latexmath macro (e.g. `stem:[2^{128}]`) or plain inline code `...`
        if (text[i] == '`') {
            val closeTick = text.indexOf('`', i + 1)
            if (closeTick > i + 1 && !text.substring(i + 1, closeTick).contains('\n')) {
                val inner = text.substring(i + 1, closeTick)
                val unwrappedMath = unwrapBracketedInlineMacro(inner)
                if (unwrappedMath != null) {
                    appendInlineMathSpan(unwrappedMath, colors)
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = CodeMonoFontFamily,
                            fontSize = (baseFontSize.value * 0.88f).sp,
                            background = colors.codeBackground,
                            color = colors.codeForeground,
                        ),
                    ) {
                        append(inner)
                    }
                }
                i = closeTick + 1
                continue
            }
        }

        // 4. AsciiDoc inline stem / latexmath macros: [stem:[...]], stem:[...], latexmath:[...], [stem]+...+
        val macroMatch = matchInlineStemMacro(text, i)
        if (macroMatch != null) {
            appendInlineMathSpan(macroMatch.latex, colors)
            i = macroMatch.nextIndex
            continue
        }

        // 5. Inline TeX math $...$ (guarded against currency like $100 or SQL $1, $2)
        if (text[i] == '$' && !(i + 1 < n && text[i + 1] == '$')) {
            val closeDollar = findValidInlineDollarClose(text, i)
            if (closeDollar != null) {
                appendInlineMathSpan(text.substring(i + 1, closeDollar), colors)
                i = closeDollar + 1
                continue
            }
        }

        // 6. Unconstrained / Markdown bold: **...**
        if (text.startsWith("**", i)) {
            val closeIdx = findClosingDoubleMarker(text, i + 2, "**")
            if (closeIdx != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendFormattedSegment(text.substring(i + 2, closeIdx), format, baseFontSize, colors)
                }
                i = closeIdx + 2
                continue
            }
        }

        // 7. Double underscore __...__ (Bold in Markdown, italic in AsciiDoc)
        if (text.startsWith("__", i)) {
            val closeIdx = findClosingDoubleMarker(text, i + 2, "__")
            if (closeIdx != null) {
                val style = if (format == MarkupFormat.ASCIIDOC) {
                    SpanStyle(fontStyle = FontStyle.Italic)
                } else {
                    SpanStyle(fontWeight = FontWeight.Bold)
                }
                withStyle(style) {
                    appendFormattedSegment(text.substring(i + 2, closeIdx), format, baseFontSize, colors)
                }
                i = closeIdx + 2
                continue
            }
        }

        // 8. Single *...* (Bold in AsciiDoc, Italic in Markdown)
        if (text[i] == '*') {
            val closeIdx = findClosingSingleStar(text, i)
            if (closeIdx != null) {
                val style = if (format == MarkupFormat.ASCIIDOC) {
                    SpanStyle(fontWeight = FontWeight.Bold)
                } else {
                    SpanStyle(fontStyle = FontStyle.Italic)
                }
                withStyle(style) {
                    appendFormattedSegment(text.substring(i + 1, closeIdx), format, baseFontSize, colors)
                }
                i = closeIdx + 1
                continue
            }
        }

        // 9. Constrained italic _..._ (never inside snake_case_identifiers or math subscripts f_i)
        if (text[i] == '_') {
            val closeIdx = findClosingSingleUnderscore(text, i)
            if (closeIdx != null) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendFormattedSegment(text.substring(i + 1, closeIdx), format, baseFontSize, colors)
                }
                i = closeIdx + 1
                continue
            }
        }

        append(text[i])
        i++
    }
}

private data class StemMacroMatch(
    val latex: String,
    val nextIndex: Int,
)

private fun unwrapBracketedInlineMacro(raw: String): String? {
    val trimmed = raw.trim()
    val m = matchInlineStemMacro(trimmed, 0) ?: return null
    return if (m.nextIndex == trimmed.length) m.latex else null
}

private fun matchInlineStemMacro(text: String, start: Int): StemMacroMatch? {
    // Pattern A: [stem]+...+ or [latexmath]+...+
    for (prefix in arrayOf("[stem]+", "[latexmath]+")) {
        if (text.startsWith(prefix, start)) {
            val bodyStart = start + prefix.length
            var cur = bodyStart
            while (cur < text.length) {
                if (text[cur] == '\n') break
                if (text[cur] == '+' && text.getOrNull(cur - 1) != '\\') {
                    val latex = text.substring(bodyStart, cur).replace("\\+", "+")
                    if (latex.isNotEmpty()) {
                        return StemMacroMatch(latex, cur + 1)
                    }
                    break
                }
                cur++
            }
        }
    }

    // Pattern B: [stem:[...]] / [latexmath:[...]]
    for (prefix in arrayOf("[stem:[", "[latexmath:[")) {
        if (text.startsWith(prefix, start)) {
            val bodyStart = start + prefix.length
            val closeInner = findMatchingBracket(text, bodyStart)
            if (closeInner != null && text.getOrNull(closeInner + 1) == ']') {
                return StemMacroMatch(text.substring(bodyStart, closeInner), closeInner + 2)
            }
        }
    }

    // Pattern C: stem:[...] / latexmath:[...]
    for (prefix in arrayOf("stem:[", "latexmath:[")) {
        if (text.startsWith(prefix, start)) {
            val bodyStart = start + prefix.length
            val closeInner = findMatchingBracket(text, bodyStart)
            if (closeInner != null) {
                return StemMacroMatch(text.substring(bodyStart, closeInner), closeInner + 1)
            }
        }
    }

    return null
}

private fun findMatchingBracket(text: String, bodyStart: Int): Int? {
    var depth = 1
    var cur = bodyStart
    while (cur < text.length) {
        val ch = text[cur]
        if (ch == '\\' && cur + 1 < text.length) {
            cur += 2
            continue
        }
        if (ch == '\n') return null
        when (ch) {
            '[' -> depth++
            ']' -> {
                depth--
                if (depth == 0) return cur
            }
        }
        cur++
    }
    return null
}

private fun findValidInlineDollarClose(text: String, openIdx: Int): Int? {
    if (openIdx + 1 >= text.length) return null
    val afterOpen = text[openIdx + 1]
    if (afterOpen.isWhitespace() || afterOpen == '$' || afterOpen == '\n') return null

    var cur = openIdx + 1
    while (cur < text.length) {
        val ch = text[cur]
        if (ch == '\n') return null
        if (ch == '\\' && cur + 1 < text.length) {
            cur += 2
            continue
        }
        if (ch == '$') {
            val beforeClose = text[cur - 1]
            val afterClose = text.getOrNull(cur + 1)
            if (!beforeClose.isWhitespace() && afterClose != '$' && (afterClose == null || !afterClose.isDigit())) {
                val inner = text.substring(openIdx + 1, cur).trim()
                val looksNumericOnly = inner.all { it.isDigit() || it == ',' || it == '.' || it == ' ' }
                if (!looksNumericOnly) {
                    return cur
                }
            }
            return null
        }
        cur++
    }
    return null
}

private fun findClosingDoubleMarker(text: String, contentStart: Int, marker: String): Int? {
    if (contentStart >= text.length || text[contentStart].isWhitespace()) return null
    var cur = contentStart
    while (cur <= text.length - marker.length) {
        if (text[cur] == '\\' && cur + 1 < text.length) {
            cur += 2
            continue
        }
        if (text.startsWith("\n\n", cur)) return null
        if (text.startsWith(marker, cur) && !text[cur - 1].isWhitespace()) {
            return cur
        }
        cur++
    }
    return null
}

private fun findClosingSingleStar(text: String, openIdx: Int): Int? {
    val next = text.getOrNull(openIdx + 1) ?: return null
    if (next.isWhitespace() || next == '*') return null
    val prev = text.getOrNull(openIdx - 1)
    if (prev != null && prev.isAsciiAlnum() && next.isAsciiAlnum()) {
        return null
    }

    var cur = openIdx + 1
    while (cur < text.length) {
        val ch = text[cur]
        if (ch == '\\' && cur + 1 < text.length) {
            cur += 2
            continue
        }
        if (ch == '\n') return null
        if (ch == '*' && text.getOrNull(cur + 1) != '*') {
            if (!text[cur - 1].isWhitespace()) {
                val after = text.getOrNull(cur + 1)
                if (!(text[cur - 1].isAsciiAlnum() && after != null && after.isAsciiAlnum())) {
                    return cur
                }
            }
        }
        cur++
    }
    return null
}

private fun findClosingSingleUnderscore(text: String, openIdx: Int): Int? {
    val prev = text.getOrNull(openIdx - 1)
    val next = text.getOrNull(openIdx + 1) ?: return null
    if (prev != null && prev.isAsciiAlnumOrUnderscore()) return null
    if (next.isWhitespace() || next == '_') return null

    var cur = openIdx + 1
    while (cur < text.length) {
        val ch = text[cur]
        if (ch == '\\' && cur + 1 < text.length) {
            cur += 2
            continue
        }
        if (ch == '\n') return null
        if (ch == '_' && text.getOrNull(cur + 1) != '_') {
            val after = text.getOrNull(cur + 1)
            if (!text[cur - 1].isWhitespace() && (after == null || !after.isAsciiAlnumOrUnderscore())) {
                return cur
            }
        }
        cur++
    }
    return null
}

private fun Char.isAsciiAlnum(): Boolean =
    (this in 'a'..'z') || (this in 'A'..'Z') || (this in '0'..'9')

private fun Char.isAsciiAlnumOrUnderscore(): Boolean = isAsciiAlnum() || this == '_'

private fun AnnotatedString.Builder.appendInlineMathSpan(
    latex: String,
    colors: RichTextColors,
) {
    withStyle(
        SpanStyle(
            fontFamily = ReaderSerifFontFamily,
            color = colors.mathForeground,
            fontWeight = FontWeight.Medium,
        ),
    ) {
        appendInlineLatexReadable(latex.trim())
    }
}

/**
 * Formats inline LaTeX strings into clean Unicode + Compose superscript/subscript spans.
 */
internal fun AnnotatedString.Builder.appendInlineLatexReadable(latex: String) {
    var i = 0
    val n = latex.length
    while (i < n) {
        val ch = latex[i]
        if (ch == '\\') {
            if (i + 1 >= n) break
            val next = latex[i + 1]
            when {
                next in charArrayOf('{', '}', '_', '%', '#', '&', '$') -> {
                    append(next)
                    i += 2
                }
                next == '\\' -> {
                    append(' ')
                    i += 2
                }
                next in charArrayOf(',', ';', ':', ' ') -> {
                    append(' ')
                    i += 2
                }
                next == '!' -> i += 2
                next == '|' -> {
                    append("‖")
                    i += 2
                }
                next.isLetter() -> {
                    var end = i + 1
                    while (end < n && latex[end].isLetter()) end++
                    val cmd = latex.substring(i + 1, end)
                    i = end
                    when (cmd) {
                        "frac" -> {
                            val numArg = readLatexGroupOrAtom(latex, i)
                            i = numArg.nextIndex
                            val denArg = readLatexGroupOrAtom(latex, i)
                            i = denArg.nextIndex
                            val num = numArg.content.trim()
                            val den = denArg.content.trim()
                            if (needsInlineParens(num)) {
                                append('(')
                                appendInlineLatexReadable(num)
                                append(')')
                            } else {
                                appendInlineLatexReadable(num)
                            }
                            append('/')
                            if (needsInlineParens(den)) {
                                append('(')
                                appendInlineLatexReadable(den)
                                append(')')
                            } else {
                                appendInlineLatexReadable(den)
                            }
                        }
                        "sqrt" -> {
                            i = skipSpaces(latex, i)
                            if (i < n && latex[i] == '[') {
                                val close = latex.indexOf(']', i + 1)
                                if (close > i) {
                                    withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)) {
                                        appendInlineLatexReadable(latex.substring(i + 1, close))
                                    }
                                    i = close + 1
                                }
                            }
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            append('√')
                            val body = arg.content.trim()
                            if (body.length > 1) {
                                append('(')
                                appendInlineLatexReadable(body)
                                append(')')
                            } else {
                                appendInlineLatexReadable(body)
                            }
                        }
                        "text", "mathrm", "textrm", "operatorname" -> {
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            append(arg.content)
                        }
                        "mathbf", "textbf" -> {
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                appendInlineLatexReadable(arg.content)
                            }
                        }
                        "mathcal" -> {
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            append(arg.content)
                        }
                        "mathbb" -> {
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            append(LatexSymbols.BLACKBOARD_MAP[arg.content.trim()] ?: arg.content)
                        }
                        "bar", "overline" -> {
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            appendInlineLatexReadable(arg.content)
                            append('\u0304')
                        }
                        "underbrace" -> {
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            appendInlineLatexReadable(arg.content)
                        }
                        "xrightarrow" -> {
                            i = skipSpaces(latex, i)
                            if (i < n && latex[i] == '[') {
                                val close = latex.indexOf(']', i + 1)
                                if (close > i) i = close + 1
                            }
                            val arg = readLatexGroupOrAtom(latex, i)
                            i = arg.nextIndex
                            append(" →")
                            if (arg.content.isNotBlank()) {
                                append('(')
                                appendInlineLatexReadable(arg.content)
                                append(')')
                            }
                            append(' ')
                        }
                        in LatexSymbols.SIZING_NOOPS -> Unit
                        in LatexSymbols.UPRIGHT_FUNCTIONS -> {
                            withStyle(SpanStyle(fontFamily = FontFamily.SansSerif, fontStyle = FontStyle.Normal)) {
                                append(cmd)
                            }
                        }
                        else -> append(LatexSymbols.COMMAND_TO_UNICODE[cmd] ?: cmd)
                    }
                }
                else -> {
                    append(next)
                    i += 2
                }
            }
            continue
        }

        when (ch) {
            '^' -> {
                val arg = readLatexGroupOrAtom(latex, i + 1)
                i = arg.nextIndex
                withStyle(
                    SpanStyle(
                        baselineShift = BaselineShift.Superscript,
                        fontSize = 0.72.em,
                    ),
                ) {
                    appendInlineLatexReadable(arg.content)
                }
            }
            '_' -> {
                val arg = readLatexGroupOrAtom(latex, i + 1)
                i = arg.nextIndex
                withStyle(
                    SpanStyle(
                        baselineShift = BaselineShift.Subscript,
                        fontSize = 0.72.em,
                    ),
                ) {
                    appendInlineLatexReadable(arg.content)
                }
            }
            '{' , '}' -> i++
            else -> {
                append(ch)
                i++
            }
        }
    }
}

fun formatInlineLatexToPlainText(latex: String): String = buildAnnotatedString {
    appendInlineLatexReadable(latex.trim())
}.text

private fun needsInlineParens(expr: String): Boolean {
    val clean = expr.trim()
    if (clean.length <= 2) return false
    return clean.any { it == '+' || it == '-' || it == ' ' || it == '\\' }
}

internal data class LatexArg(
    val content: String,
    val nextIndex: Int,
)

internal fun skipSpaces(text: String, index: Int): Int {
    var i = index
    while (i < text.length && text[i].isWhitespace()) i++
    return i
}

internal fun readLatexGroupOrAtom(text: String, index: Int): LatexArg {
    val start = skipSpaces(text, index)
    if (start >= text.length) return LatexArg("", start)
    if (text[start] == '{') {
        var depth = 1
        var cur = start + 1
        while (cur < text.length) {
            if (text[cur] == '\\' && cur + 1 < text.length) {
                cur += 2
                continue
            }
            when (text[cur]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return LatexArg(text.substring(start + 1, cur), cur + 1)
                    }
                }
            }
            cur++
        }
        return LatexArg(text.substring(start + 1), text.length)
    }
    if (text[start] == '\\') {
        var cur = start + 1
        while (cur < text.length && text[cur].isLetter()) cur++
        if (cur == start + 1 && cur < text.length) cur++
        return LatexArg(text.substring(start, cur), cur)
    }
    return LatexArg(text[start].toString(), start + 1)
}
