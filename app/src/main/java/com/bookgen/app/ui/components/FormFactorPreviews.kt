package com.bookgen.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.ui.theme.BookGenTheme

/**
 * Multi-preview covering Phone, Unfolded Foldable, Tablet, and Desktop window sizes
 * so adaptive layouts can be verified visually across form factors.
 */
@Preview(name = "Phone", widthDp = 412, heightDp = 915, showSystemUi = true)
@Preview(name = "Foldable Unfolded", widthDp = 673, heightDp = 841, showSystemUi = true)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800, showSystemUi = true)
@Preview(name = "Desktop", widthDp = 1600, heightDp = 900, showSystemUi = true)
annotation class FormFactorPreviews

private data class PreviewBookItem(
    val id: String,
    val title: String,
    val topic: String,
    val status: BookStatus,
    val emptyOrFailedCount: Int,
)

private val SAMPLE_PREVIEW_BOOKS = listOf(
    PreviewBookItem("p1", "Rust × Android NDK 実践設計", "FFI と並列生成の実戦解説", BookStatus.COMPLETED, 0),
    PreviewBookItem("p2", "債券ポートフォリオの数理", "デュレーション分析とリスク管理", BookStatus.GENERATING, 2),
    PreviewBookItem("p3", "AsciiDoc 電子書籍出版入門", "EPUB と印刷組版のモダンワークフロー", BookStatus.PARTIAL, 1),
)

@FormFactorPreviews
@Composable
private fun AdaptiveCatalogFormFactorPreview() {
    BookGenTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(
                    key = "preview-header",
                    contentType = "header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        text = "Co-Author Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(
                    count = SAMPLE_PREVIEW_BOOKS.size,
                    key = { index -> SAMPLE_PREVIEW_BOOKS[index].id },
                    contentType = { "book-card" },
                ) { index ->
                    val book = SAMPLE_PREVIEW_BOOKS[index]
                    EditorialCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = book.topic,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            BookStatusBadge(
                                status = book.status,
                                emptyOrFailedCount = book.emptyOrFailedCount,
                            )
                        }
                    }
                }
            }
        }
    }
}
