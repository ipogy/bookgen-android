package com.bookgen.app.domain.repository

import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.BookMetadataPatch
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.BookSummary
import com.bookgen.app.domain.model.ChapterPlanDraft
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.ExportEvent
import com.bookgen.app.domain.model.ExportFormatType
import com.bookgen.app.domain.model.GenreInfo
import com.bookgen.app.domain.model.GenreMatch
import com.bookgen.app.domain.model.IncompleteSection
import com.bookgen.app.domain.model.LegacyImportSummary
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.NewBookRequest
import com.bookgen.app.domain.model.ReadingProgress
import com.bookgen.app.domain.model.SearchHit
import com.bookgen.app.domain.model.SectionRecord
import com.bookgen.app.domain.model.SectionRevision
import com.bookgen.app.domain.model.SectionStatus
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeLibraryRefresh(): Flow<Long>

    suspend fun listBooks(
        genreFilter: String? = null,
        statusFilter: BookStatus? = null,
    ): List<BookSummary>

    suspend fun getBook(bookId: String): BookDetail

    suspend fun createBook(request: NewBookRequest): BookSummary

    suspend fun updateBookMetadata(patch: BookMetadataPatch): BookSummary

    suspend fun saveOutline(bookId: String, chapters: List<ChapterPlanDraft>): BookDetail

    suspend fun addChapter(bookId: String, title: String, focusArea: String): ChapterWithSections

    suspend fun updateChapterMeta(chapterId: String, title: String, focusArea: String): ChapterWithSections

    suspend fun addSection(chapterId: String, title: String, keyFocus: String): SectionRecord

    suspend fun deleteSection(sectionId: String): BookDetail

    suspend fun upsertSectionContent(
        sectionId: String,
        content: String,
        format: MarkupFormat,
        source: String,
        note: String,
    ): SectionRecord

    suspend fun updateSectionMeta(sectionId: String, title: String, keyFocus: String): SectionRecord

    suspend fun setSectionStatus(
        sectionId: String,
        status: SectionStatus,
        errorMessage: String? = null,
    ): SectionRecord

    suspend fun reorderChapters(bookId: String, orderedChapterIds: List<String>): BookDetail

    suspend fun reorderSections(chapterId: String, orderedSectionIds: List<String>): ChapterWithSections

    suspend fun listSectionRevisions(sectionId: String): List<SectionRevision>

    suspend fun restoreSectionRevision(sectionId: String, revisionId: Long): SectionRecord

    suspend fun listIncompleteSections(bookId: String): List<IncompleteSection>

    suspend fun saveReadingProgress(
        bookId: String,
        chapterId: String,
        sectionId: String?,
        scrollOffset: Double,
    ): ReadingProgress

    suspend fun getReadingProgress(bookId: String): ReadingProgress?

    suspend fun search(query: String, bookIdFilter: String? = null, limit: Int = 40): List<SearchHit>

    suspend fun deleteBook(bookId: String)

    suspend fun importLegacyBookgen(legacyBooksDir: String): LegacyImportSummary

    suspend fun listGenres(): List<GenreInfo>

    suspend fun getGenre(genreKey: String): GenreInfo

    suspend fun classifyThemeByKeywords(theme: String): GenreInfo

    suspend fun classifyEmbedding(vector: List<Float>): List<GenreMatch>

    fun sanitizeOutput(raw: String, format: MarkupFormat): String

    fun parseAstBlocks(content: String, format: MarkupFormat): List<DocBlock>

    fun exportBookStream(
        jobId: String,
        bookId: String,
        format: ExportFormatType,
        outputPath: String,
        includeToc: Boolean,
        includeIncompleteNotice: Boolean,
    ): Flow<ExportEvent>
}
