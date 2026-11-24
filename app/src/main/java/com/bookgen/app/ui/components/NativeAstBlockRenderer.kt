package com.bookgen.app.ui.components

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookgen.app.domain.model.AdmonitionKind
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.ReaderAppearance
import com.bookgen.app.domain.model.ReaderFontChoice
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
    onCopiedCode: () -> Unit = {},
) {
    val bodyFamily = when (appearance.fontChoice) {
        ReaderFontChoice.SERIF -> ReaderSerifFontFamily
        ReaderFontChoice.SANS -> UiSansFontFamily
    }
    val bodyStyle = TextStyle(
        fontFamily = bodyFamily,
        fontSize = appearance.fontSizeSp.sp,
        lineHeight = (appearance.fontSizeSp * appearance.lineHeightMultiplier).sp,
        color = MaterialTheme.colorScheme.onBackground,
    )

    when (block) {
        is DocBlock.Heading -> {
            val size = when (block.level) {
                1 -> (appearance.fontSizeSp + 7f).sp
                2 -> (appearance.fontSizeSp + 4.5f).sp
                3 -> (appearance.fontSizeSp + 2.5f).sp
                else -> (appearance.fontSizeSp + 1f).sp
            }
            Text(
                text = block.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = bodyFamily,
                    fontSize = size,
                    lineHeight = (size.value * 1.35f).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
            )
        }

        is DocBlock.Paragraph -> {
            SelectionContainer(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = block.text,
                    style = bodyStyle,
                )
            }
        }

        is DocBlock.CodeBlock -> {
            CodeBlockCard(
                language = block.language,
                code = block.code,
                onCopiedCode = onCopiedCode,
                modifier = modifier,
            )
        }

        is DocBlock.Admonition -> {
            AdmonitionCard(
                kind = block.kind,
                title = block.title,
                body = block.body,
                bodyStyle = bodyStyle,
                modifier = modifier,
            )
        }

        is DocBlock.Table -> {
            AstTableCard(
                headers = block.headers,
                rows = block.rows,
                modifier = modifier,
            )
        }

        is DocBlock.MathBlock -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = BookGenRadius.Control,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, BookGenThemeExtras.colors.borderSubtle),
            ) {
                Text(
                    text = block.latex,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = CodeMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        is DocBlock.BulletList -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                block.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(18.dp),
                        )
                        Text(
                            text = item,
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
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${idx + 1}.",
                            style = bodyStyle.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = item,
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is DocBlock.Quote -> {
            val accent = BookGenThemeExtras.colors.borderSubtle
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = accent,
                            start = Offset(4.dp.toPx(), 4.dp.toPx()),
                            end = Offset(4.dp.toPx(), size.height - 4.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                    .padding(start = 18.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = block.text,
                    style = bodyStyle.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                if (!block.attribution.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "— ${block.attribution}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DocBlock.Divider -> {
            HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = BookGenThemeExtras.colors.borderSubtle,
            )
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String?,
    code: String,
    onCopiedCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val extended = BookGenThemeExtras.colors

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
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val accent = when (kind) {
        AdmonitionKind.WARNING, AdmonitionKind.CAUTION -> MaterialTheme.colorScheme.secondary
        AdmonitionKind.TIP -> MaterialTheme.colorScheme.tertiary
        AdmonitionKind.NOTE, AdmonitionKind.IMPORTANT -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookGenRadius.Control,
        color = BookGenThemeExtras.colors.admonitionSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawLine(
                        color = accent,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 4.dp.toPx(),
                    )
                }
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = title?.ifBlank { null } ?: kind.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = bodyStyle)
        }
    }
}

@Composable
private fun AstTableCard(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
) {
    val border = BookGenThemeExtras.colors.borderSubtle
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookGenRadius.Control,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            if (headers.isNotEmpty()) {
                Row(modifier = Modifier.padding(bottom = 6.dp)) {
                    headers.forEach { header ->
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(136.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                HorizontalDivider(color = border)
            }
            rows.forEachIndexed { idx, row ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(136.dp)
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
