package com.bookgen.app

import com.bookgen.core.uniffi.BookExporter
import com.bookgen.core.uniffi.BookGenException
import com.bookgen.core.uniffi.BookStore
import com.bookgen.core.uniffi.DocumentBlock
import com.bookgen.core.uniffi.ExportFormat
import com.bookgen.core.uniffi.ExportProgressListener
import com.bookgen.core.uniffi.ExportRequest
import com.bookgen.core.uniffi.MarkupFormat
import com.bookgen.core.uniffi.NewBookDraft
import com.bookgen.core.uniffi.NewChapterPlan
import com.bookgen.core.uniffi.NewSectionPlan
import com.bookgen.core.uniffi.ProgressEvent
import com.bookgen.core.uniffi.ProgressStage
import com.bookgen.core.uniffi.classifyThemeKeywords
import com.bookgen.core.uniffi.listGenres
import com.bookgen.core.uniffi.parseMarkupBlocks
import com.bookgen.core.uniffi.sanitizeSectionOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExportAndMarkupUniFFITest {
    @Test
    fun genresSanitizerAndAstParserWorkAcrossUniFFI() {
        val genres = listGenres()
        assertEquals(12, genres.size)
        val matched = classifyThemeKeywords("Rust + Jetpack Compose 設計")
        assertEquals("tech", matched.key)

        val cleaned = sanitizeSectionOutput(
            "```markdown\n## Intro\n```kotlin\nval x = 1\n```\n```",
            MarkupFormat.MARKDOWN,
        )
        assertEquals("## Intro\n```kotlin\nval x = 1\n```", cleaned)

        val blocks = parseMarkupBlocks(cleaned, MarkupFormat.MARKDOWN)
        assertTrue(blocks.any { it is DocumentBlock.Heading })
        assertTrue(blocks.any { it is DocumentBlock.CodeBlock })
    }

    @Test
    fun exporterStreamsProgressToDoneAndHandlesPreCancel() {
        val store = BookStore.open(":memory:")
        val summary = store.createBook(
            NewBookDraft(
                id = null,
                title = "UniFFI Export Test",
                themePrompt = "Android publishing",
                genreKey = "tech",
                genreName = "Technical",
                targetAudience = "Devs",
                tone = "Clear",
                targetCharCount = 8000L,
                overview = "Overview text",
            ),
        )
        val detail = store.saveBookOutline(
            summary.id,
            listOf(
                NewChapterPlan(
                    chapterNumber = 1u,
                    title = "Basics",
                    focusArea = "FFI",
                    sections = listOf(
                        NewSectionPlan(1u, "Sec 1", "Intro"),
                    ),
                ),
            ),
        )
        val secId = detail.chapters.first().sections.first().id
        store.upsertSectionContent(secId, "Hello EPUB & PDF.", MarkupFormat.MARKDOWN, "test", "")

        val tempDir = Files.createTempDirectory("bookgen-export-test").toFile()
        try {
            val epubFile = tempDir.resolve("out.epub")
            val exporter = BookExporter()
            val stages = mutableListOf<ProgressStage>()
            val listener = object : ExportProgressListener {
                override fun onProgress(event: ProgressEvent) {
                    stages.add(event.stage)
                }
            }
            exporter.reserveJob("j-epub")
            val res = exporter.exportBook(
                store,
                ExportRequest(
                    jobId = "j-epub",
                    bookId = summary.id,
                    format = ExportFormat.EPUB,
                    outputPath = epubFile.absolutePath,
                    includeToc = true,
                    includeIncompleteNotice = true,
                ),
                listener,
            )
            assertTrue(res.bytesWritten > 100uL)
            assertEquals(ProgressStage.DONE, stages.last())
            assertTrue(epubFile.exists())

            // Pre-cancel test
            val cancelFile = tempDir.resolve("cancelled.pdf")
            exporter.reserveJob("j-cancel")
            exporter.cancelJob("j-cancel")
            val cancelled = runCatching {
                exporter.exportBook(
                    store,
                    ExportRequest(
                        jobId = "j-cancel",
                        bookId = summary.id,
                        format = ExportFormat.PDF,
                        outputPath = cancelFile.absolutePath,
                        includeToc = true,
                        includeIncompleteNotice = true,
                    ),
                    null,
                )
            }
            assertTrue(cancelled.exceptionOrNull() is BookGenException.Cancelled)
            assertTrue(!cancelFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
