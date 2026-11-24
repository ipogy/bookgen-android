package com.bookgen.app

import com.bookgen.core.uniffi.BookGenException
import com.bookgen.core.uniffi.BookStatus
import com.bookgen.core.uniffi.BookStore
import com.bookgen.core.uniffi.MarkupFormat
import com.bookgen.core.uniffi.NewBookDraft
import com.bookgen.core.uniffi.NewChapterPlan
import com.bookgen.core.uniffi.NewSectionPlan
import com.bookgen.core.uniffi.SectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookStoreUniFFITest {
    @Test
    fun bookStoreCrudFtsSearchAndRevisionsWorkAcrossUniFFI() {
        val store = BookStore.open(":memory:")
        val summary = store.createBook(
            NewBookDraft(
                id = null,
                title = "Compose with Rust Core",
                themePrompt = "Android offline book generator",
                genreKey = "tech",
                genreName = "Technical & Engineering",
                targetAudience = "Android engineers",
                tone = "Practical",
                targetCharCount = 12000L,
                overview = "Architecture guide",
            ),
        )
        assertEquals(BookStatus.DRAFT, summary.status)

        val detail = store.saveBookOutline(
            summary.id,
            listOf(
                NewChapterPlan(
                    chapterNumber = 1u,
                    title = "Foundation",
                    focusArea = "UniFFI",
                    sections = listOf(
                        NewSectionPlan(
                            sectionNumber = 1u,
                            title = "SQLite WAL",
                            keyFocus = "Transactions",
                        ),
                        NewSectionPlan(
                            sectionNumber = 2u,
                            title = "FTS5 Search",
                            keyFocus = "Trigram",
                        ),
                    ),
                ),
            ),
        )
        assertEquals(BookStatus.OUTLINED, detail.summary.status)

        val sections = detail.chapters.first().sections
        val sec1 = sections[0]
        val sec2 = sections[1]

        store.upsertSectionContent(
            sec1.id,
            "SQLite WAL ensures atomic offline persistence on mobile.",
            MarkupFormat.MARKDOWN,
            "ai_generate",
            "Initial",
        )
        store.upsertSectionContent(
            sec2.id,
            "   ",
            MarkupFormat.MARKDOWN,
            "ai_generate",
            "Blank output",
        )

        val incomplete = store.listIncompleteSections(summary.id)
        assertEquals(1, incomplete.size)
        assertEquals(SectionStatus.EMPTY, incomplete.first().status)

        val hits = store.search("persistence", summary.id, 10u)
        assertEquals(1, hits.size)
        assertTrue(hits.first().snippet.contains("persistence"))
    }

    @Test(expected = BookGenException.NotFound::class)
    fun getMissingBookThrowsTypedNotFoundException() {
        val store = BookStore.open(":memory:")
        store.getBook("does-not-exist")
    }
}
