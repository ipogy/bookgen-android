package com.bookgen.app.data.rust

import com.bookgen.app.domain.model.AdmonitionKind as DomainAdmonitionKind
import com.bookgen.app.domain.model.AppDomainException
import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.BookMetadataPatch
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.BookSummary
import com.bookgen.app.domain.model.ChapterPlanDraft
import com.bookgen.app.domain.model.ChapterRecord
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.ExportFormatType
import com.bookgen.app.domain.model.ExportStage
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
import com.bookgen.core.uniffi.AdmonitionKind as RustAdmonitionKind
import com.bookgen.core.uniffi.BookDetail as RustBookDetail
import com.bookgen.core.uniffi.BookGenException
import com.bookgen.core.uniffi.BookMetadataUpdate
import com.bookgen.core.uniffi.BookStatus as RustBookStatus
import com.bookgen.core.uniffi.BookSummary as RustBookSummary
import com.bookgen.core.uniffi.ChapterRecord as RustChapterRecord
import com.bookgen.core.uniffi.ChapterWithSections as RustChapterWithSections
import com.bookgen.core.uniffi.DocumentBlock as RustDocumentBlock
import com.bookgen.core.uniffi.ExportFormat as RustExportFormat
import com.bookgen.core.uniffi.GenreInfo as RustGenreInfo
import com.bookgen.core.uniffi.GenreMatch as RustGenreMatch
import com.bookgen.core.uniffi.IncompleteSection as RustIncompleteSection
import com.bookgen.core.uniffi.LegacyImportReport
import com.bookgen.core.uniffi.MarkupFormat as RustMarkupFormat
import com.bookgen.core.uniffi.NewBookDraft
import com.bookgen.core.uniffi.NewChapterPlan
import com.bookgen.core.uniffi.NewSectionPlan
import com.bookgen.core.uniffi.ProgressStage
import com.bookgen.core.uniffi.ReadingProgress as RustReadingProgress
import com.bookgen.core.uniffi.SearchHit as RustSearchHit
import com.bookgen.core.uniffi.SectionRecord as RustSectionRecord
import com.bookgen.core.uniffi.SectionRevision as RustSectionRevision
import com.bookgen.core.uniffi.SectionStatus as RustSectionStatus
import kotlinx.coroutines.CancellationException

/** Maps Rust UniFFI boundary exceptions into platform cancellation or UI-safe domain errors. */
internal fun BookGenException.toDomain(): Throwable = when (this) {
    is BookGenException.Cancelled -> CancellationException("operation cancelled")
    is BookGenException.InvalidInput -> AppDomainException.RecoverableUser("invalid_input", detail)
    is BookGenException.Conflict -> AppDomainException.RecoverableUser("conflict", detail)
    is BookGenException.NotFound -> AppDomainException.RecoverableData("not_found", detail)
    is BookGenException.Storage -> AppDomainException.RecoverableData("storage", detail)
    is BookGenException.IoFailed -> AppDomainException.RecoverableData("io_failed", detail)
    is BookGenException.Parse -> AppDomainException.RecoverableData("parse", detail)
    is BookGenException.Export -> AppDomainException.EngineBug("書き出し処理に失敗しました")
    is BookGenException.InternalPanic -> AppDomainException.EngineBug("内部エンジンエラーが発生しました")
}

internal inline fun <T> runRustGuarded(block: () -> T): T {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: BookGenException) {
        throw e.toDomain()
    } catch (_: Exception) {
        throw AppDomainException.EngineBug("コア処理中に予期しないエラーが発生しました")
    }
}

internal fun BookStatus.toRust(): RustBookStatus = when (this) {
    BookStatus.DRAFT -> RustBookStatus.DRAFT
    BookStatus.OUTLINED -> RustBookStatus.OUTLINED
    BookStatus.GENERATING -> RustBookStatus.GENERATING
    BookStatus.COMPLETED -> RustBookStatus.COMPLETED
    BookStatus.PARTIAL -> RustBookStatus.PARTIAL
    BookStatus.FAILED -> RustBookStatus.FAILED
}

internal fun RustBookStatus.toDomain(): BookStatus = when (this) {
    RustBookStatus.DRAFT -> BookStatus.DRAFT
    RustBookStatus.OUTLINED -> BookStatus.OUTLINED
    RustBookStatus.GENERATING -> BookStatus.GENERATING
    RustBookStatus.COMPLETED -> BookStatus.COMPLETED
    RustBookStatus.PARTIAL -> BookStatus.PARTIAL
    RustBookStatus.FAILED -> BookStatus.FAILED
}

internal fun SectionStatus.toRust(): RustSectionStatus = when (this) {
    SectionStatus.PENDING -> RustSectionStatus.PENDING
    SectionStatus.GENERATING -> RustSectionStatus.GENERATING
    SectionStatus.COMPLETED -> RustSectionStatus.COMPLETED
    SectionStatus.FAILED -> RustSectionStatus.FAILED
    SectionStatus.EMPTY -> RustSectionStatus.EMPTY
}

internal fun RustSectionStatus.toDomain(): SectionStatus = when (this) {
    RustSectionStatus.PENDING -> SectionStatus.PENDING
    RustSectionStatus.GENERATING -> SectionStatus.GENERATING
    RustSectionStatus.COMPLETED -> SectionStatus.COMPLETED
    RustSectionStatus.FAILED -> SectionStatus.FAILED
    RustSectionStatus.EMPTY -> SectionStatus.EMPTY
}

internal fun MarkupFormat.toRust(): RustMarkupFormat = when (this) {
    MarkupFormat.MARKDOWN -> RustMarkupFormat.MARKDOWN
    MarkupFormat.ASCIIDOC -> RustMarkupFormat.ASCII_DOC
}

internal fun RustMarkupFormat.toDomain(): MarkupFormat = when (this) {
    RustMarkupFormat.MARKDOWN -> MarkupFormat.MARKDOWN
    RustMarkupFormat.ASCII_DOC -> MarkupFormat.ASCIIDOC
}

internal fun RustBookSummary.toDomain() = BookSummary(
    id = id,
    title = title,
    themePrompt = themePrompt,
    genreKey = genreKey,
    genreName = genreName,
    targetAudience = targetAudience,
    tone = tone,
    targetCharCount = targetCharCount,
    overview = overview,
    status = status.toDomain(),
    totalChapters = totalChapters.toInt(),
    totalSections = totalSections.toInt(),
    completedSections = completedSections.toInt(),
    emptyOrFailedSections = emptyOrFailedSections.toInt(),
    wordCount = wordCount.toLong(),
    charCount = charCount.toLong(),
    lastReadChapterId = lastReadChapterId,
    lastReadSectionId = lastReadSectionId,
    lastReadScrollOffset = lastReadScrollOffset,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun RustChapterRecord.toDomain() = ChapterRecord(
    id = id,
    bookId = bookId,
    chapterNumber = chapterNumber.toInt(),
    position = position.toInt(),
    title = title,
    focusArea = focusArea,
)

internal fun RustSectionRecord.toDomain() = SectionRecord(
    id = id,
    chapterId = chapterId,
    bookId = bookId,
    chapterNumber = chapterNumber.toInt(),
    sectionNumber = sectionNumber.toInt(),
    position = position.toInt(),
    title = title,
    keyFocus = keyFocus,
    format = format.toDomain(),
    content = content,
    status = status.toDomain(),
    errorMessage = errorMessage,
    wordCount = wordCount.toLong(),
    charCount = charCount.toLong(),
    revisionCount = revisionCount.toInt(),
    updatedAt = updatedAt,
)

internal fun RustChapterWithSections.toDomain() = ChapterWithSections(
    chapter = chapter.toDomain(),
    sections = sections.map { it.toDomain() },
)

internal fun RustBookDetail.toDomain() = BookDetail(
    summary = summary.toDomain(),
    chapters = chapters.map { it.toDomain() },
)

internal fun RustSectionRevision.toDomain() = SectionRevision(
    id = id,
    sectionId = sectionId,
    revisionNumber = revisionNumber.toInt(),
    content = content,
    source = source,
    note = note,
    createdAt = createdAt,
)

internal fun RustReadingProgress.toDomain() = ReadingProgress(
    bookId = bookId,
    chapterId = chapterId,
    sectionId = sectionId,
    scrollOffset = scrollOffset,
    updatedAt = updatedAt,
)

internal fun RustSearchHit.toDomain() = SearchHit(
    bookId = bookId,
    bookTitle = bookTitle,
    chapterId = chapterId,
    chapterNumber = chapterNumber.toInt(),
    chapterTitle = chapterTitle,
    sectionId = sectionId,
    sectionNumber = sectionNumber.toInt(),
    sectionTitle = sectionTitle,
    snippet = snippet,
    rank = rank,
)

internal fun RustIncompleteSection.toDomain() = IncompleteSection(
    bookId = bookId,
    chapterId = chapterId,
    chapterNumber = chapterNumber.toInt(),
    chapterTitle = chapterTitle,
    sectionId = sectionId,
    sectionNumber = sectionNumber.toInt(),
    sectionTitle = sectionTitle,
    keyFocus = keyFocus,
    status = status.toDomain(),
    errorMessage = errorMessage,
)

internal fun RustGenreInfo.toDomain() = GenreInfo(
    key = key,
    name = name,
    seedText = seedText,
    editorGuide = editorGuide,
    elementLabel = elementLabel,
    authorStyle = authorStyle,
)

internal fun RustGenreMatch.toDomain() = GenreMatch(
    genre = genre.toDomain(),
    similarity = similarity,
)

internal fun RustAdmonitionKind.toDomain(): DomainAdmonitionKind = when (this) {
    RustAdmonitionKind.NOTE -> DomainAdmonitionKind.NOTE
    RustAdmonitionKind.TIP -> DomainAdmonitionKind.TIP
    RustAdmonitionKind.IMPORTANT -> DomainAdmonitionKind.IMPORTANT
    RustAdmonitionKind.WARNING -> DomainAdmonitionKind.WARNING
    RustAdmonitionKind.CAUTION -> DomainAdmonitionKind.CAUTION
}

internal fun RustDocumentBlock.toDomain(): DocBlock = when (this) {
    is RustDocumentBlock.Heading -> DocBlock.Heading(level.toInt(), text)
    is RustDocumentBlock.Paragraph -> DocBlock.Paragraph(text)
    is RustDocumentBlock.CodeBlock -> DocBlock.CodeBlock(language, code)
    is RustDocumentBlock.Table -> DocBlock.Table(headers, rows)
    is RustDocumentBlock.Admonition -> DocBlock.Admonition(kind.toDomain(), title, body)
    is RustDocumentBlock.MathBlock -> DocBlock.MathBlock(latex)
    is RustDocumentBlock.BulletList -> DocBlock.BulletList(items)
    is RustDocumentBlock.NumberedList -> DocBlock.NumberedList(items)
    is RustDocumentBlock.Quote -> DocBlock.Quote(text, attribution)
    is RustDocumentBlock.Divider -> DocBlock.Divider
}

internal fun LegacyImportReport.toDomain() = LegacyImportSummary(
    dbBooksImported = dbBooksImported.toInt(),
    fsBooksImported = fsBooksImported.toInt(),
    booksSkippedExisting = booksSkippedExisting.toInt(),
    chaptersImported = chaptersImported.toInt(),
    sectionsImported = sectionsImported.toInt(),
    emptySectionsFlagged = emptySectionsFlagged.toInt(),
    normalizedSectionNumbers = normalizedSectionNumbers.toInt(),
    warnings = warnings,
)

internal fun NewBookRequest.toRust() = NewBookDraft(
    id = null,
    title = title,
    themePrompt = themePrompt,
    genreKey = genreKey,
    genreName = genreName,
    targetAudience = targetAudience,
    tone = tone,
    targetCharCount = targetCharCount,
    overview = overview,
)

internal fun BookMetadataPatch.toRust() = BookMetadataUpdate(
    bookId = bookId,
    title = title,
    overview = overview,
    genreKey = genreKey,
    genreName = genreName,
    targetAudience = targetAudience,
    tone = tone,
)

internal fun ChapterPlanDraft.toRust() = NewChapterPlan(
    chapterNumber = chapterNumber.coerceAtLeast(1).toUInt(),
    title = title,
    focusArea = focusArea,
    sections = sections.map { sec ->
        NewSectionPlan(
            sectionNumber = sec.sectionNumber.coerceAtLeast(1).toUInt(),
            title = sec.title,
            keyFocus = sec.keyFocus,
        )
    },
)

internal fun ExportFormatType.toRust(): RustExportFormat = when (this) {
    ExportFormatType.EPUB -> RustExportFormat.EPUB
    ExportFormatType.PDF -> RustExportFormat.PDF
    ExportFormatType.MARKDOWN -> RustExportFormat.MARKDOWN
    ExportFormatType.ASCIIDOC -> RustExportFormat.ASCII_DOC
}

internal fun ProgressStage.toDomain(): ExportStage = when (this) {
    ProgressStage.VALIDATING -> ExportStage.VALIDATING
    ProgressStage.LOADING -> ExportStage.LOADING
    ProgressStage.PROCESSING -> ExportStage.PROCESSING
    ProgressStage.BUILDING -> ExportStage.BUILDING
    ProgressStage.ENCODING -> ExportStage.ENCODING
    ProgressStage.WRITING -> ExportStage.WRITING
    ProgressStage.DONE -> ExportStage.DONE
}
