package com.bookgen.app.data.remote

import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.BookSummary
import com.bookgen.app.domain.model.ChapterRecord
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.GenreInfo
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.SectionRecord
import com.bookgen.app.domain.model.SectionStatus
import com.bookgen.app.domain.service.BookPromptBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class LlmJsonRepairAndPromptTest {

    @Test
    fun repairsWrappedThinkingAndTrailingCommaOutlineJson() {
        val raw = """
            <think>内部思考プロセス {無視される}</think>
            ```json
            {
              "book_title": "Kotlin と Rust 実践設計",
              "target_audience": "Android 開発者",
              "overview": "UniFFI の設計指針。",
              "chapters": [
                {
                  "chapter_number": 1,
                  "title": "基礎構造",
                  "focus_area": "FFI 境界",
                  "sections": [
                    {"section_number": 1, "title": "パニック安全性", "key_focus": "catch_unwind",},
                  ],
                },
              ],
            }
            ```
        """.trimIndent()

        val parsed = LlmJsonRepair.parseOutlineJson(raw)
        assertEquals("Kotlin と Rust 実践設計", parsed.bookTitle)
        assertEquals(1, parsed.chapters.size)
        val plans = LlmJsonRepair.toChapterPlanDrafts(parsed)
        assertEquals(1, plans.size)
        assertEquals("パニック安全性", plans[0].sections[0].title)
    }

    @Test
    fun parsesGeminiAndOpenRouterSseStreams() = runTest {
        val geminiSse = """
            : keepalive
            data: {"candidates":[{"content":{"parts":[{"text":"## 節タイトル\n"}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"本文チャンク"}]}}]}

            data: [DONE]
        """.trimIndent()

        val geminiChunks = mutableListOf<String>()
        SseEventParser.consumeSseStream(
            reader = BufferedReader(StringReader(geminiSse)),
            isOpenRouter = false,
            isCancelled = { false },
            onDelta = { geminiChunks += it },
        )
        assertEquals("## 節タイトル\n本文チャンク", geminiChunks.joinToString(""))

        val openRouterSse = """
            data: {"choices":[{"delta":{"content":"OpenRouter "}}]}

            data: {"choices":[{"delta":{"content":"Streaming"}}]}

            data: [DONE]
        """.trimIndent()

        val orChunks = mutableListOf<String>()
        SseEventParser.consumeSseStream(
            reader = BufferedReader(StringReader(openRouterSse)),
            isOpenRouter = true,
            isCancelled = { false },
            onDelta = { orChunks += it },
        )
        assertEquals("OpenRouter Streaming", orChunks.joinToString(""))
    }

    @Test
    fun sectionPromptInjectsGenreGuidesAndAdjacentSections() {
        val genre = GenreInfo(
            key = "tech",
            name = "IT技術・プログラミング",
            seedText = "Kotlin Rust",
            editorGuide = "アンチパターンまで解説する実務書",
            elementLabel = "具体的なコード例とトレードオフ分析",
            authorStyle = "論理的かつ厳密に解説",
        )
        val prevSec = SectionRecord(
            id = "s1",
            chapterId = "c1",
            bookId = "b1",
            chapterNumber = 1,
            sectionNumber = 1,
            position = 0,
            title = "境界定義",
            keyFocus = "UDL vs proc-macro",
            format = MarkupFormat.MARKDOWN,
            content = "前節ではproc-macroの利点を確認した。",
            status = SectionStatus.COMPLETED,
            errorMessage = null,
            wordCount = 10,
            charCount = 24,
            revisionCount = 1,
            updatedAt = "",
        )
        val curSec = prevSec.copy(id = "s2", sectionNumber = 2, position = 1, title = "エラー変換", keyFocus = "BookGenError", content = "", status = SectionStatus.PENDING)
        val nextSec = prevSec.copy(id = "s3", sectionNumber = 3, position = 2, title = "キャンセル制御", keyFocus = "CancellationToken", content = "", status = SectionStatus.PENDING)
        val ch = ChapterWithSections(
            chapter = ChapterRecord("c1", "b1", 1, 0, "UniFFI 基礎", "安全性"),
            sections = listOf(prevSec, curSec, nextSec),
        )
        val detail = BookDetail(
            summary = BookSummary(
                id = "b1",
                title = "実践 UniFFI",
                themePrompt = "Kotlin Rust",
                genreKey = "tech",
                genreName = genre.name,
                targetAudience = "Android 開発者",
                tone = "厳密・論理",
                targetCharCount = 30000L,
                overview = "安全なネイティブ連携設計",
                status = BookStatus.PARTIAL,
                totalChapters = 1,
                totalSections = 3,
                completedSections = 1,
                emptyOrFailedSections = 2,
                wordCount = 10,
                charCount = 24,
                lastReadChapterId = null,
                lastReadSectionId = null,
                lastReadScrollOffset = 0.0,
                createdAt = "",
                updatedAt = "",
            ),
            chapters = listOf(ch),
        )

        val prompts = BookPromptBuilder.buildSectionPrompts(
            detail = detail,
            chapter = ch,
            section = curSec,
            genre = genre,
            previousSection = prevSec,
            nextSection = nextSec,
            format = MarkupFormat.MARKDOWN,
            targetSectionChars = 1500,
            customInstruction = "コード例を2つ含める",
        )

        assertTrue(prompts.systemInstruction.contains("アンチパターンまで解説する実務書"))
        assertTrue(prompts.systemInstruction.contains("具体的なコード例とトレードオフ分析"))
        assertTrue(prompts.userPrompt.contains("proc-macroの利点を確認した"))
        assertTrue(prompts.userPrompt.contains("キャンセル制御"))
        assertTrue(prompts.userPrompt.contains("コード例を2つ含める"))
    }
}
