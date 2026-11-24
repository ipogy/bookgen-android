package com.bookgen.app.data.repository

import com.bookgen.app.data.rust.runRustGuarded
import com.bookgen.app.data.rust.toDomain
import com.bookgen.app.data.rust.toRust
import com.bookgen.app.domain.model.AppDomainException
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
import com.bookgen.app.domain.repository.BookRepository
import com.bookgen.core.uniffi.BookExporter
import com.bookgen.core.uniffi.BookGenException
import com.bookgen.core.uniffi.BookStore
import com.bookgen.core.uniffi.ExportProgressListener
import com.bookgen.core.uniffi.ExportRequest
import com.bookgen.core.uniffi.ProgressEvent
import com.bookgen.core.uniffi.classifyEmbedding as rustClassifyEmbedding
import com.bookgen.core.uniffi.classifyThemeKeywords as rustClassifyThemeKeywords
import com.bookgen.core.uniffi.getGenre as rustGetGenre
import com.bookgen.core.uniffi.listGenres as rustListGenres
import com.bookgen.core.uniffi.parseMarkupBlocks as rustParseMarkupBlocks
import com.bookgen.core.uniffi.sanitizeSectionOutput as rustSanitizeSectionOutput
import com.bookgen.core.uniffi.uniffiEnsureInitialized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single Kotlin-to-Rust UniFFI bridge implementing [BookRepository].
 * Keeps all generated `com.bookgen.core.uniffi.*` types confined to the data layer.
 */
class RustBookRepository(
    dbPath: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BookRepository {

    private val store: BookStore = runRustGuarded {
        // Explicit startup contract check per UniFFI 0.32.x single-crate guidelines.
        uniffiEnsureInitialized()
        BookStore.open(dbPath)
    }
    private val exporter: BookExporter = BookExporter()
    private val refreshCounter = MutableStateFlow(0L)

    override fun observeLibraryRefresh(): Flow<Long> = refreshCounter.asStateFlow()

    private fun notifyMutated() {
        refreshCounter.update { it + 1L }
    }

    override suspend fun listBooks(
        genreFilter: String?,
        statusFilter: BookStatus?,
    ): List<BookSummary> = withContext(ioDispatcher) {
        runRustGuarded {
            store.listBooks(genreFilter, statusFilter?.toRust()).map { it.toDomain() }
        }
    }

    override suspend fun getBook(bookId: String): BookDetail = withContext(ioDispatcher) {
        runRustGuarded {
            store.getBook(bookId).toDomain()
        }
    }

    override suspend fun createBook(request: NewBookRequest): BookSummary = withContext(ioDispatcher) {
        runRustGuarded {
            store.createBook(request.toRust()).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun updateBookMetadata(patch: BookMetadataPatch): BookSummary =
        withContext(ioDispatcher) {
            runRustGuarded {
                store.updateBookMetadata(patch.toRust()).toDomain()
            }.also { notifyMutated() }
        }

    override suspend fun saveOutline(
        bookId: String,
        chapters: List<ChapterPlanDraft>,
    ): BookDetail = withContext(ioDispatcher) {
        runRustGuarded {
            store.saveBookOutline(bookId, chapters.map { it.toRust() }).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun addChapter(
        bookId: String,
        title: String,
        focusArea: String,
    ): ChapterWithSections = withContext(ioDispatcher) {
        runRustGuarded {
            store.addChapter(bookId, title, focusArea).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun updateChapterMeta(
        chapterId: String,
        title: String,
        focusArea: String,
    ): ChapterWithSections = withContext(ioDispatcher) {
        runRustGuarded {
            store.updateChapterMeta(chapterId, title, focusArea).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun addSection(
        chapterId: String,
        title: String,
        keyFocus: String,
    ): SectionRecord = withContext(ioDispatcher) {
        runRustGuarded {
            store.addSection(chapterId, title, keyFocus).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun deleteSection(sectionId: String): BookDetail = withContext(ioDispatcher) {
        runRustGuarded {
            store.deleteSection(sectionId).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun upsertSectionContent(
        sectionId: String,
        content: String,
        format: MarkupFormat,
        source: String,
        note: String,
    ): SectionRecord = withContext(ioDispatcher) {
        runRustGuarded {
            store.upsertSectionContent(
                sectionId,
                content,
                format.toRust(),
                source,
                note,
            ).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun updateSectionMeta(
        sectionId: String,
        title: String,
        keyFocus: String,
    ): SectionRecord = withContext(ioDispatcher) {
        runRustGuarded {
            store.updateSectionMeta(sectionId, title, keyFocus).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun setSectionStatus(
        sectionId: String,
        status: SectionStatus,
        errorMessage: String?,
    ): SectionRecord = withContext(ioDispatcher) {
        runRustGuarded {
            store.setSectionStatus(sectionId, status.toRust(), errorMessage).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun reorderChapters(
        bookId: String,
        orderedChapterIds: List<String>,
    ): BookDetail = withContext(ioDispatcher) {
        runRustGuarded {
            store.reorderChapters(bookId, orderedChapterIds).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun reorderSections(
        chapterId: String,
        orderedSectionIds: List<String>,
    ): ChapterWithSections = withContext(ioDispatcher) {
        runRustGuarded {
            store.reorderSections(chapterId, orderedSectionIds).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun listSectionRevisions(sectionId: String): List<SectionRevision> =
        withContext(ioDispatcher) {
            runRustGuarded {
                store.listSectionRevisions(sectionId).map { it.toDomain() }
            }
        }

    override suspend fun restoreSectionRevision(
        sectionId: String,
        revisionId: Long,
    ): SectionRecord = withContext(ioDispatcher) {
        runRustGuarded {
            store.restoreSectionRevision(sectionId, revisionId).toDomain()
        }.also { notifyMutated() }
    }

    override suspend fun listIncompleteSections(bookId: String): List<IncompleteSection> =
        withContext(ioDispatcher) {
            runRustGuarded {
                store.listIncompleteSections(bookId).map { it.toDomain() }
            }
        }

    override suspend fun saveReadingProgress(
        bookId: String,
        chapterId: String,
        sectionId: String?,
        scrollOffset: Double,
    ): ReadingProgress = withContext(ioDispatcher) {
        runRustGuarded {
            store.saveReadingProgress(bookId, chapterId, sectionId, scrollOffset).toDomain()
        }
    }

    override suspend fun getReadingProgress(bookId: String): ReadingProgress? =
        withContext(ioDispatcher) {
            runRustGuarded {
                store.getReadingProgress(bookId)?.toDomain()
            }
        }

    override suspend fun search(
        query: String,
        bookIdFilter: String?,
        limit: Int,
    ): List<SearchHit> = withContext(ioDispatcher) {
        runRustGuarded {
            store.search(query, bookIdFilter, limit.coerceAtLeast(1).toUInt()).map { it.toDomain() }
        }
    }

    override suspend fun deleteBook(bookId: String) = withContext(ioDispatcher) {
        runRustGuarded {
            store.deleteBook(bookId)
        }
        notifyMutated()
    }

    override suspend fun importLegacyBookgen(legacyBooksDir: String): LegacyImportSummary =
        withContext(ioDispatcher) {
            runRustGuarded {
                store.importLegacyBookgen(legacyBooksDir).toDomain()
            }.also { notifyMutated() }
        }

    override suspend fun listGenres(): List<GenreInfo> = withContext(ioDispatcher) {
        runRustGuarded {
            rustListGenres().map { it.toDomain() }
        }
    }

    override suspend fun getGenre(genreKey: String): GenreInfo = withContext(ioDispatcher) {
        runRustGuarded {
            rustGetGenre(genreKey).toDomain()
        }
    }

    override suspend fun classifyThemeByKeywords(theme: String): GenreInfo =
        withContext(ioDispatcher) {
            runRustGuarded {
                rustClassifyThemeKeywords(theme).toDomain()
            }
        }

    override suspend fun classifyEmbedding(vector: List<Float>): List<GenreMatch> =
        withContext(ioDispatcher) {
            runRustGuarded {
                rustClassifyEmbedding(vector).map { it.toDomain() }
            }
        }

    override fun sanitizeOutput(raw: String, format: MarkupFormat): String =
        rustSanitizeSectionOutput(raw, format.toRust())

    override fun parseAstBlocks(content: String, format: MarkupFormat): List<DocBlock> =
        rustParseMarkupBlocks(content, format.toRust()).map { it.toDomain() }

    override fun exportBookStream(
        jobId: String,
        bookId: String,
        format: ExportFormatType,
        outputPath: String,
        includeToc: Boolean,
        includeIncompleteNotice: Boolean,
    ): Flow<ExportEvent> = callbackFlow {
        // Reserve synchronously before launch so early collector cancel is picked up cleanly.
        try {
            exporter.reserveJob(jobId)
        } catch (e: BookGenException) {
            throw e.toDomain()
        } catch (_: Exception) {
            throw AppDomainException.EngineBug("書き出しジョブの初期化に失敗しました")
        }

        val listener = object : ExportProgressListener {
            override fun onProgress(event: ProgressEvent) {
                trySend(
                    ExportEvent.Progress(
                        stage = event.stage.toDomain(),
                        fraction = event.fraction.toFloat().coerceIn(0f, 1f),
                    ),
                )
            }
        }

        launch(ioDispatcher) {
            try {
                val res = exporter.exportBook(
                    store = store,
                    request = ExportRequest(
                        jobId = jobId,
                        bookId = bookId,
                        format = format.toRust(),
                        outputPath = outputPath,
                        includeToc = includeToc,
                        includeIncompleteNotice = includeIncompleteNotice,
                    ),
                    listener = listener,
                )
                trySend(
                    ExportEvent.Completed(
                        format = format,
                        outputPath = res.outputPath,
                        bytesWritten = res.bytesWritten.toLong(),
                        chapterCount = res.chapterCount.toInt(),
                        sectionCount = res.sectionCount.toInt(),
                    ),
                )
                close()
            } catch (e: BookGenException) {
                close(e.toDomain())
            } catch (_: Exception) {
                close(AppDomainException.EngineBug("書き出し処理中に内部エラーが発生しました"))
            }
        }

        awaitClose { exporter.cancelJob(jobId) }
    }.flowOn(ioDispatcher)
}
