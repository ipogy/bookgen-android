package com.bookgen.app.data.repository

import com.bookgen.app.domain.model.ChapterPlanDraft
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.ExportEvent
import com.bookgen.app.domain.model.ExportFormatType
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.NewBookRequest
import com.bookgen.app.domain.model.SectionPlanDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RustBookRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun repositoryBridgesCrudSearchAstAndStreamingExport() = runTest {
        val dbPath = File(tempFolder.root, "repo_test.db").absolutePath
        val outDir = tempFolder.newFolder("exports")
        val repository = RustBookRepository(
            dbPath = dbPath,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val genres = repository.listGenres()
        assertTrue("Expected bundled genres", genres.size >= 12)

        val detectedGenre = repository.classifyThemeByKeywords("Rust と Kotlin で作る設計ガイド")
        assertTrue(detectedGenre.key.isNotBlank())

        val created = repository.createBook(
            NewBookRequest(
                title = "実践 Kotlin & Rust",
                themePrompt = "Android アプリのネイティブ高速化手法",
                genreKey = detectedGenre.key,
                genreName = detectedGenre.name,
                targetAudience = "モバイルエンジニア",
                tone = "論理的・明快",
                targetCharCount = 30000L,
                overview = "UniFFI を活用したアーキテクチャ設計",
            ),
        )
        assertEquals("実践 Kotlin & Rust", created.title)

        val detail = repository.saveOutline(
            bookId = created.id,
            chapters = listOf(
                ChapterPlanDraft(
                    chapterNumber = 1,
                    title = "設計の基盤",
                    focusArea = "Clean Architecture",
                    sections = listOf(
                        SectionPlanDraft(
                            sectionNumber = 1,
                            title = "UniFFI の導入",
                            keyFocus = "境界設計とエラー処理",
                        ),
                        SectionPlanDraft(
                            sectionNumber = 2,
                            title = "FTS5 検索",
                            keyFocus = "オフライン高速検索",
                        ),
                    ),
                ),
            ),
        )
        assertEquals(1, detail.chapters.size)
        val firstSectionId = detail.chapters.first().sections.first().id

        val rawModelOutput = """
            ```markdown
            ## UniFFI の境界設計
            安全な **FFI** 設計の解説です。

            > NOTE: パニックを境界外へ漏らさないでください。

            ```rust
            pub fn safe_call() -> u32 { 42 }
            ```
            ```
        """.trimIndent()
        val sanitized = repository.sanitizeOutput(rawModelOutput, MarkupFormat.MARKDOWN)
        val blocks = repository.parseAstBlocks(sanitized, MarkupFormat.MARKDOWN)
        assertTrue(blocks.any { it is DocBlock.Heading })
        assertTrue(blocks.any { it is DocBlock.CodeBlock })

        repository.upsertSectionContent(
            sectionId = firstSectionId,
            content = "安全な FFI 設計の初版テキストです。",
            format = MarkupFormat.MARKDOWN,
            source = "ai_initial",
            note = "初稿",
        )
        repository.upsertSectionContent(
            sectionId = firstSectionId,
            content = sanitized,
            format = MarkupFormat.MARKDOWN,
            source = "user_edit",
            note = "改訂",
        )

        val revisions = repository.listSectionRevisions(firstSectionId)
        assertEquals(2, revisions.size)

        val hits = repository.search("境界設計", bookIdFilter = created.id)
        assertTrue("Expected FTS5 search hit", hits.isNotEmpty())

        val epubFile = File(outDir, "book_export.epub")
        val events = repository.exportBookStream(
            jobId = "repo-test-epub-1",
            bookId = created.id,
            format = ExportFormatType.EPUB,
            outputPath = epubFile.absolutePath,
            includeToc = true,
            includeIncompleteNotice = true,
        ).toList()

        assertTrue(events.any { it is ExportEvent.Progress })
        val completed = events.last() as ExportEvent.Completed
        assertTrue(completed.bytesWritten > 0L)
        assertTrue(epubFile.exists() && epubFile.length() > 0L)
    }
}
