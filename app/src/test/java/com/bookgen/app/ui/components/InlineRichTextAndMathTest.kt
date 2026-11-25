package com.bookgen.app.ui.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import com.bookgen.app.domain.model.MarkupFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineRichTextAndMathTest {

    @Test
    fun formatsAsciiDocBoldJapaneseWordsAndInlineStemMacros() {
        val raw = "後の*アラート閾値設定*では *デュレーション分析* と *デュレーション（Duration）* を使い、[stem]+(100 - 98) \\div 4 = 0.5円+ となる。"
        val annotated = formatInlineRichText(raw, MarkupFormat.ASCIIDOC)
        val plain = annotated.text

        assertFalse("Asterisks should be consumed for bold spans: $plain", plain.contains('*'))
        assertFalse("Raw [stem]+...+ macro should be rendered cleanly: $plain", plain.contains("[stem]"))
        assertTrue(plain.contains("アラート閾値設定"))
        assertTrue(plain.contains("デュレーション分析"))
        assertTrue(plain.contains("デュレーション（Duration）"))
        assertTrue("Division command should render as ÷: $plain", plain.contains("(100 - 98) ÷ 4 = 0.5円"))

        val boldTexts = annotated.spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .map { plain.substring(it.start, it.end) }
        assertTrue(boldTexts.contains("アラート閾値設定"))
        assertTrue(boldTexts.contains("デュレーション分析"))
        assertTrue(boldTexts.contains("デュレーション（Duration）"))
    }

    @Test
    fun formatsInlineMathSubscriptsSuperscriptsGreekAndGuardsFalsePositives() {
        val raw = "平均収益は [stem:[R_p]]、計算量は `stem:[2^{128}]` かつ \\(\\alpha + \\beta\\) で、費用は \$100 や \$1, \$2 とし、変数 app_data_v1 とポインタ (c *http.Client, req *http.Request) は保持する。"
        val annotated = formatInlineRichText(raw, MarkupFormat.ASCIIDOC)
        val plain = annotated.text

        assertTrue(plain.contains("Rp"))
        assertTrue(plain.contains("2128"))
        assertTrue(plain.contains("α + β"))
        assertTrue("Currency/SQL placeholders should remain verbatim: $plain", plain.contains("\$100"))
        assertTrue("SQL placeholders should remain verbatim: $plain", plain.contains("\$1, \$2"))
        assertTrue("snake_case must remain literal: $plain", plain.contains("app_data_v1"))
        assertTrue("Go pointer syntax must remain intact: $plain", plain.contains("*http.Client, req *http.Request"))

        val hasSubscript = annotated.spanStyles.any { it.item.baselineShift == BaselineShift.Subscript }
        val hasSuperscript = annotated.spanStyles.any { it.item.baselineShift == BaselineShift.Superscript }
        assertTrue("Expected Subscript BaselineShift on R_p", hasSubscript)
        assertTrue("Expected Superscript BaselineShift on 2^{128}", hasSuperscript)
    }

    @Test
    fun parsesNestedFractionAndCasesLatexAst() {
        val macFormula = """D_{mac} = \frac{\sum_{t=1}^{N} \frac{t \cdot C}{(1 + y)^t} + \frac{N \cdot M}{(1 + y)^N}}{P}"""
        val node = parseLatexToMathNode(macFormula)
        assertTrue(node is MathNode.Row)
        val rowItems = (node as MathNode.Row).items
        assertTrue(rowItems.any { it is MathNode.Fraction })

        val outerFrac = rowItems.filterIsInstance<MathNode.Fraction>().first()
        val numText = mathNodeToPlainText(outerFrac.numerator)
        assertTrue("Expected summation sigma in numerator: $numText", numText.contains("∑"))
        assertTrue("Expected denominator P", mathNodeToPlainText(outerFrac.denominator).trim() == "P")

        val casesFormula = """\text{状態} = \begin{cases} O(1) & \text{即座に決定} \\ O(n) & \text{線形探索} \end{cases}"""
        val casesNode = parseLatexToMathNode(casesFormula) as MathNode.Row
        val cases = casesNode.items.filterIsInstance<MathNode.Cases>().first()
        assertEquals(2, cases.rows.size)
        assertEquals(2, cases.rows[0].size)
        assertTrue(mathNodeToPlainText(cases).contains("線形探索"))
    }

    @Test
    fun separatesHybridProseAndFormulaLinesInsideStemBlocks() {
        val hybridStem = """
            ある解 x が別の解 y に *支配される(dominated)* とは、f_i(x) \geq f_i(y) が成立することをいう。
            p \iff \nexists q \in S : q \preceq p
        """.trimIndent()

        val segments = parseMathBlockSegments(hybridStem)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is MathSegment.Prose)
        assertTrue(segments[1] is MathSegment.Formula)

        val proseRendered = formatInlineRichText((segments[0] as MathSegment.Prose).text, MarkupFormat.ASCIIDOC)
        assertFalse(proseRendered.text.contains('*'))
        assertTrue(proseRendered.text.contains("支配される(dominated)"))
        assertTrue(proseRendered.text.contains("≥"))

        val formulaText = (segments[1] as MathSegment.Formula).plainSummary
        assertTrue(formulaText.contains("⇔"))
        assertTrue(formulaText.contains("∄"))
        assertTrue(formulaText.contains("⪯"))
    }
}
