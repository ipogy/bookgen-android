package com.bookgen.app.domain.model

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
    SEPIA,
}

enum class ReaderFontChoice {
    SANS,
    SERIF,
}

data class ReaderAppearance(
    val theme: ThemePreference = ThemePreference.LIGHT,
    val fontChoice: ReaderFontChoice = ReaderFontChoice.SERIF,
    val fontSizeSp: Float = 17f,
    val lineHeightMultiplier: Float = 1.75f,
    val horizontalPaddingDp: Int = 20,
)

enum class BookStatus {
    DRAFT,
    OUTLINED,
    GENERATING,
    COMPLETED,
    PARTIAL,
    FAILED,
}

enum class SectionStatus {
    PENDING,
    GENERATING,
    COMPLETED,
    FAILED,
    EMPTY,
}

enum class MarkupFormat {
    MARKDOWN,
    ASCIIDOC,
}

data class BookSummary(
    val id: String,
    val title: String,
    val themePrompt: String,
    val genreKey: String,
    val genreName: String,
    val targetAudience: String,
    val tone: String,
    val targetCharCount: Long,
    val overview: String,
    val status: BookStatus,
    val totalChapters: Int,
    val totalSections: Int,
    val completedSections: Int,
    val emptyOrFailedSections: Int,
    val wordCount: Long,
    val charCount: Long,
    val lastReadChapterId: String?,
    val lastReadSectionId: String?,
    val lastReadScrollOffset: Double,
    val createdAt: String,
    val updatedAt: String,
) {
    val progressFraction: Float
        get() = if (totalSections <= 0) 0f else completedSections.toFloat() / totalSections.toFloat()
}

data class ChapterRecord(
    val id: String,
    val bookId: String,
    val chapterNumber: Int,
    val position: Int,
    val title: String,
    val focusArea: String,
)

data class SectionRecord(
    val id: String,
    val chapterId: String,
    val bookId: String,
    val chapterNumber: Int,
    val sectionNumber: Int,
    val position: Int,
    val title: String,
    val keyFocus: String,
    val format: MarkupFormat,
    val content: String,
    val status: SectionStatus,
    val errorMessage: String?,
    val wordCount: Long,
    val charCount: Long,
    val revisionCount: Int,
    val updatedAt: String,
)

data class ChapterWithSections(
    val chapter: ChapterRecord,
    val sections: List<SectionRecord>,
)

data class BookDetail(
    val summary: BookSummary,
    val chapters: List<ChapterWithSections>,
)

data class SectionRevision(
    val id: Long,
    val sectionId: String,
    val revisionNumber: Int,
    val content: String,
    val source: String,
    val note: String,
    val createdAt: String,
)

data class ReadingProgress(
    val bookId: String,
    val chapterId: String,
    val sectionId: String?,
    val scrollOffset: Double,
    val updatedAt: String,
)

data class SearchHit(
    val bookId: String,
    val bookTitle: String,
    val chapterId: String,
    val chapterNumber: Int,
    val chapterTitle: String,
    val sectionId: String,
    val sectionNumber: Int,
    val sectionTitle: String,
    val snippet: String,
    val rank: Double,
)

data class IncompleteSection(
    val bookId: String,
    val chapterId: String,
    val chapterNumber: Int,
    val chapterTitle: String,
    val sectionId: String,
    val sectionNumber: Int,
    val sectionTitle: String,
    val keyFocus: String,
    val status: SectionStatus,
    val errorMessage: String?,
)

data class GenreInfo(
    val key: String,
    val name: String,
    val seedText: String,
    val editorGuide: String,
    val elementLabel: String,
    val authorStyle: String,
)

data class GenreMatch(
    val genre: GenreInfo,
    val similarity: Double,
)

enum class AdmonitionKind {
    NOTE,
    TIP,
    IMPORTANT,
    WARNING,
    CAUTION,
}

sealed interface DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock
    data class Paragraph(val text: String) : DocBlock
    data class CodeBlock(val language: String?, val code: String) : DocBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : DocBlock
    data class Admonition(val kind: AdmonitionKind, val title: String?, val body: String) : DocBlock
    data class MathBlock(val latex: String) : DocBlock
    data class BulletList(val items: List<String>) : DocBlock
    data class NumberedList(val items: List<String>) : DocBlock
    data class Quote(val text: String, val attribution: String?) : DocBlock
    data object Divider : DocBlock
}

data class NewBookRequest(
    val title: String,
    val themePrompt: String,
    val genreKey: String,
    val genreName: String,
    val targetAudience: String,
    val tone: String,
    val targetCharCount: Long,
    val overview: String,
)

data class BookMetadataPatch(
    val bookId: String,
    val title: String,
    val overview: String,
    val genreKey: String,
    val genreName: String,
    val targetAudience: String,
    val tone: String,
)

data class ChapterPlanDraft(
    val chapterNumber: Int,
    val title: String,
    val focusArea: String,
    val sections: List<SectionPlanDraft>,
)

data class SectionPlanDraft(
    val sectionNumber: Int,
    val title: String,
    val keyFocus: String,
)

data class LegacyImportSummary(
    val dbBooksImported: Int,
    val fsBooksImported: Int,
    val booksSkippedExisting: Int,
    val chaptersImported: Int,
    val sectionsImported: Int,
    val emptySectionsFlagged: Int,
    val normalizedSectionNumbers: Int,
    val warnings: List<String>,
)

enum class ExportFormatType(val extension: String, val mimeType: String, val label: String) {
    EPUB("epub", "application/epub+zip", "EPUB 3 電子書籍"),
    PDF("pdf", "application/pdf", "PDF ドキュメント"),
    MARKDOWN("md", "text/markdown", "Markdown (.md)"),
    ASCIIDOC("adoc", "text/asciidoc", "AsciiDoc (.adoc)"),
}

enum class ExportStage {
    VALIDATING,
    LOADING,
    PROCESSING,
    BUILDING,
    ENCODING,
    WRITING,
    DONE,
}

sealed interface ExportEvent {
    data class Progress(val stage: ExportStage, val fraction: Float) : ExportEvent
    data class Completed(
        val format: ExportFormatType,
        val outputPath: String,
        val bytesWritten: Long,
        val chapterCount: Int,
        val sectionCount: Int,
    ) : ExportEvent
}

sealed class AppDomainException(
    val code: String,
    override val message: String,
) : Exception(message) {
    class RecoverableUser(code: String, message: String) : AppDomainException(code, message)
    class RecoverableData(code: String, message: String) : AppDomainException(code, message)
    class EngineBug(message: String) : AppDomainException("engine_bug", message)
}
