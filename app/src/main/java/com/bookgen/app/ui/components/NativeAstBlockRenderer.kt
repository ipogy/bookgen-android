package com.bookgen.app.ui.components

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookgen.app.domain.model.AdmonitionKind
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.ReaderAppearance
import com.bookgen.app.domain.model.ReaderFontChoice
import com.bookgen.app.ui.theme.BookGenExtendedColors
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras
import com.bookgen.app.ui.theme.CodeMonoFontFamily
import com.bookgen.app.ui.theme.ReaderSerifFontFamily
import com.bookgen.app.ui.theme.UiSansFontFamily
import kotlinx.coroutines.launch

@Composable
fun NativeAstBlockView(
    block: DocBlock,
    appearance: ReaderAppearance,
    modifier: Modifier = Modifier,
    format: MarkupFormat = MarkupFormat.ASCIIDOC,
    bodyFamily: FontFamily = when (appearance.fontChoice) {
        ReaderFontChoice.SERIF -> ReaderSerifFontFamily
        ReaderFontChoice.SANS -> UiSansFontFamily
    },
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    extendedColors: BookGenExtendedColors = BookGenThemeExtras.colors,
    richColors: RichTextColors = rememberRichTextColors(),
    bodyStyle: TextStyle = TextStyle(
        fontFamily = bodyFamily,
        fontSize = appearance.fontSizeSp.sp,
        lineHeight = (appearance.fontSizeSp * appearance.lineHeightMultiplier).sp,
        color = colorScheme.onBackground,
    ),
    clipboard: Clipboard = LocalClipboard.current,
    onCopiedCode: () -> Unit = {},
) {
    when (block) {
        is DocBlock.Heading -> {
            val size = when (block.level) {
                1 -> (appearance.fontSizeSp + 7f).sp
                2 -> (appearance.fontSizeSp + 4.5f).sp
                3 -> (appearance.fontSizeSp + 2.5f).sp
                else -> (appearance.fontSizeSp + 1f).sp
            }
            val styledHeading = remember(block.text, format, size, richColors) {
                formatInlineRichText(block.text, format, size, richColors)
            }
            Text(
                text = styledHeading,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = bodyFamily,
                    fontSize = size,
                    lineHeight = (size.value * 1.35f).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colorScheme.onBackground,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
            )
        }

        is DocBlock.Paragraph -> {
            val styledParagraph = remember(block.text, format, appearance.fontSizeSp, richColors) {
                formatInlineRichText(block.text, format, appearance.fontSizeSp.sp, richColors)
            }
            SelectionContainer(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = styledParagraph,
                    style = bodyStyle,
                )
            }
        }

        is DocBlock.CodeBlock -> {
            CodeBlockCard(
                language = block.language,
                code = block.code,
                extendedColors = extendedColors,
                clipboard = clipboard,
                onCopiedCode = onCopiedCode,
                modifier = modifier,
            )
        }

        is DocBlock.Admonition -> {
            AdmonitionCard(
                kind = block.kind,
                title = block.title,
                body = block.body,
                format = format,
                bodyStyle = bodyStyle,
                colorScheme = colorScheme,
                extendedColors = extendedColors,
                richColors = richColors,
                modifier = modifier,
            )
        }

        is DocBlock.Table -> {
            AstTableCard(
                headers = block.headers,
                rows = block.rows,
                format = format,
                colorScheme = colorScheme,
                extendedColors = extendedColors,
                richColors = richColors,
                modifier = modifier,
            )
        }

        is DocBlock.MathBlock -> {
            NativeMathBlockView(
                latex = block.latex,
                baseFontSizeSp = appearance.fontSizeSp,
                modifier = modifier,
                colorScheme = colorScheme,
                extendedColors = extendedColors,
                richColors = richColors,
                clipboard = clipboard,
                onCopiedFormula = onCopiedCode,
            )
        }

        is DocBlock.BulletList -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                block.items.forEach { item ->
                    val styledItem = remember(item, format, appearance.fontSizeSp, richColors) {
                        formatInlineRichText(item, format, appearance.fontSizeSp.sp, richColors)
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            style = bodyStyle,
                            color = colorScheme.primary,
                            modifier = Modifier.width(18.dp),
                        )
                        Text(
                            text = styledItem,
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is DocBlock.NumberedList -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                block.items.forEachIndexed { idx, item ->
                    val styledItem = remember(item, format, appearance.fontSizeSp, richColors) {
                        formatInlineRichText(item, format, appearance.fontSizeSp.sp, richColors)
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${idx + 1}.",
                            style = bodyStyle.copy(fontFeatureSettings = "tnum"),
                            color = colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = styledItem,
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is DocBlock.Quote -> {
            val accent = extendedColors.borderSubtle
            val styledQuote = remember(block.text, format, appearance.fontSizeSp, richColors) {
                formatInlineRichText(block.text, format, appearance.fontSizeSp.sp, richColors)
            }
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val x = 4.dp.toPx()
                        val topY = 4.dp.toPx()
                        val bottomY = (size.height - 4.dp.toPx()).coerceAtLeast(topY)
                        val strokeW = 3.dp.toPx()
                        onDrawBehind {
                            drawLine(
                                color = accent,
                                start = Offset(x, topY),
                                end = Offset(x, bottomY),
                                strokeWidth = strokeW,
                            )
                        }
                    }
                    .padding(start = 18.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = styledQuote,
                    style = bodyStyle.copy(
                        color = colorScheme.onSurfaceVariant,
                    ),
                )
                if (!block.attribution.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "— ${block.attribution}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DocBlock.Divider -> {
            HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = extendedColors.borderSubtle,
            )
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String?,
    code: String,
    extendedColors: BookGenExtendedColors,
    clipboard: Clipboard,
    onCopiedCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val extended = extendedColors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookGenRadius.Control,
        color = extended.codeSurface,
        contentColor = extended.onCodeSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = language?.ifBlank { "code" } ?: "code",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = CodeMonoFontFamily),
                    color = extended.onCodeSurface.copy(alpha = 0.72f),
                )
                EditorialTooltipBox(label = "コードをコピー") {
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("BookGen Code", code)),
                                )
                                onCopiedCode()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "コードをコピー",
                            tint = extended.onCodeSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CodeMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                        color = extended.onCodeSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdmonitionCard(
    kind: AdmonitionKind,
    title: String?,
    body: String,
    format: MarkupFormat,
    bodyStyle: TextStyle,
    colorScheme: ColorScheme,
    extendedColors: BookGenExtendedColors,
    richColors: RichTextColors,
    modifier: Modifier = Modifier,
) {
    val accent = when (kind) {
        AdmonitionKind.WARNING, AdmonitionKind.CAUTION -> colorScheme.secondary
        AdmonitionKind.TIP -> colorScheme.tertiary
        AdmonitionKind.NOTE, AdmonitionKind.IMPORTANT -> colorScheme.primary
    }
    val styledBody = remember(body, format, bodyStyle.fontSize, richColors) {
        formatInlineRichText(body, format, bodyStyle.fontSize, richColors)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookGenRadius.Control,
        color = extendedColors.admonitionSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .drawWithCache {
                    val strokeW = 4.dp.toPx()
                    val h = size.height
                    onDrawBehind {
                        drawLine(
                            color = accent,
                            start = Offset(0f, 0f),
                            end = Offset(0f, h),
                            strokeWidth = strokeW,
                        )
                    }
                }
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = title?.ifBlank { null } ?: kind.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = styledBody, style = bodyStyle)
        }
    }
}

@Composable
private fun AstTableCard(
    headers: List<String>,
    rows: List<List<String>>,
    format: MarkupFormat,
    colorScheme: ColorScheme,
    extendedColors: BookGenExtendedColors,
    richColors: RichTextColors,
    modifier: Modifier = Modifier,
) {
    val border = extendedColors.borderSubtle
    val colCount = headers.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 1)
    val colWidth = if (colCount <= 3) 164.dp else 132.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookGenRadius.Control,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            if (headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(
                            color = colorScheme.surfaceContainerHigh,
                            shape = BookGenRadius.Badge,
                        )
                        .padding(vertical = 4.dp),
                ) {
                    headers.forEach { header ->
                        val styledHeader = remember(header, format, richColors) {
                            formatInlineRichText(header, format, 13.sp, richColors)
                        }
                        Text(
                            text = styledHeader,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .width(colWidth)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                HorizontalDivider(color = border)
            }
            rows.forEachIndexed { idx, row ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    row.forEach { cell ->
                        val styledCell = remember(cell, format, richColors) {
                            formatInlineRichText(cell, format, 12.5.sp, richColors)
                        }
                        Text(
                            text = styledCell,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(colWidth)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                if (idx < rows.lastIndex) {
                    HorizontalDivider(color = border.copy(alpha = 0.6f))
                }
            }
        }
    }
}
