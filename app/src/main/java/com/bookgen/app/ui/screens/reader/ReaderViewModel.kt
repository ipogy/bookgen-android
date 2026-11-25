package com.bookgen.app.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.ExportEvent
import com.bookgen.app.domain.model.ExportFormatType
import com.bookgen.app.domain.model.ExportStage
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.ReaderAppearance
import com.bookgen.app.domain.model.ReaderFontChoice
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.domain.repository.BookRepository
import com.bookgen.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ParsedSectionBlocks(
    val sectionId: String,
    val title: String,
    val chapterNumber: Int,
    val sectionNumber: Int,
    val charCount: Long,
    val isCompleted: Boolean,
    val format: MarkupFormat = MarkupFormat.MARKDOWN,
    val blocks: List<DocBlock>,
)

data class ExportUiState(
    val selectedFormat: ExportFormatType = ExportFormatType.EPUB,
    val includeToc: Boolean = true,
    val includeIncompleteNotice: Boolean = true,
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val stageLabel: String = "",
    val lastCompletedPath: String? = null,
    val lastBytesWritten: Long = 0L,
    val errorMessage: String? = null,
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val detail: BookDetail? = null,
    val currentChapterIndex: Int = 0,
    val savedScrollFraction: Double = 0.0,
    val parsedSections: List<ParsedSectionBlocks> = emptyList(),
    val appearance: ReaderAppearance = ReaderAppearance(),
    val chromeVisible: Boolean = true,
    val exportState: ExportUiState = ExportUiState(),
    val snackbarMessage: String? = null,
)

class ReaderViewModel(
    private val bookId: String,
    private val initialChapterIndex: Int,
    private val exportDir: File,
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            currentChapterIndex = initialChapterIndex,
            appearance = settingsRepository.getReaderAppearance(),
        ),
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var exportJob: Job? = null
    private var chapterParseJob: Job? = null

    init {
        loadBook()
    }

    fun loadBook() {
        viewModelScope.launch {
            try {
                val detail = repository.getBook(bookId)
                val progress = repository.getReadingProgress(bookId)
                val chapterIndex = when {
                    initialChapterIndex > 0 -> initialChapterIndex.coerceIn(0, (detail.chapters.size - 1).coerceAtLeast(0))
                    progress != null -> detail.chapters.indexOfFirst { it.chapter.id == progress.chapterId }.takeIf { it >= 0 } ?: 0
                    else -> 0
                }
                val restoredScrollFraction = if (
                    initialChapterIndex == 0 &&
                    progress != null &&
                    detail.chapters.getOrNull(chapterIndex)?.chapter?.id == progress.chapterId
                ) {
                    progress.scrollOffset.coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                val parsed = withContext(computeDispatcher) { parseChapterSections(detail, chapterIndex) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        detail = detail,
                        currentChapterIndex = chapterIndex,
                        savedScrollFraction = restoredScrollFraction,
                        parsedSections = parsed,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = e.message ?: "書籍の読み込みに失敗しました",
                    )
                }
            }
        }
    }

    fun selectChapter(index: Int) {
        val detail = _uiState.value.detail ?: return
        if (index !in detail.chapters.indices) return
        chapterParseJob?.cancel()
        chapterParseJob = viewModelScope.launch {
            val parsed = withContext(computeDispatcher) { parseChapterSections(detail, index) }
            _uiState.update {
                it.copy(
                    currentChapterIndex = index,
                    savedScrollFraction = 0.0,
                    parsedSections = parsed,
                )
            }
            val chapter = detail.chapters[index].chapter
            runCatching {
                repository.saveReadingProgress(
                    bookId = detail.summary.id,
                    chapterId = chapter.id,
                    sectionId = detail.chapters[index].sections.firstOrNull()?.id,
                    scrollOffset = 0.0,
                )
            }
        }
    }

    fun persistScrollFraction(scrollFraction: Double) {
        val detail = _uiState.value.detail ?: return
        val chapter = detail.chapters.getOrNull(_uiState.value.currentChapterIndex) ?: return
        val normalized = scrollFraction.coerceIn(0.0, 1.0)
        viewModelScope.launch {
            runCatching {
                repository.saveReadingProgress(
                    bookId = detail.summary.id,
                    chapterId = chapter.chapter.id,
                    sectionId = chapter.sections.firstOrNull()?.id,
                    scrollOffset = normalized,
                )
            }
        }
    }

    fun toggleChromeVisibility() {
        _uiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun setTheme(theme: ThemePreference) {
        updateAppearance { it.copy(theme = theme) }
    }

    fun setFontChoice(choice: ReaderFontChoice) {
        updateAppearance { it.copy(fontChoice = choice) }
    }

    fun setFontSizeSp(size: Float) {
        updateAppearance { it.copy(fontSizeSp = size.coerceIn(14f, 24f)) }
    }

    fun setLineHeight(multiplier: Float) {
        updateAppearance { it.copy(lineHeightMultiplier = multiplier.coerceIn(1.4f, 2.1f)) }
    }

    fun setHorizontalPadding(dp: Int) {
        updateAppearance { it.copy(horizontalPaddingDp = dp.coerceIn(12, 36)) }
    }

    private inline fun updateAppearance(transform: (ReaderAppearance) -> ReaderAppearance) {
        _uiState.update { cur ->
            val updated = transform(cur.appearance)
            settingsRepository.setReaderAppearance(updated)
            cur.copy(appearance = updated)
        }
    }

    fun notifyMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun selectExportFormat(format: ExportFormatType) {
        _uiState.update {
            it.copy(exportState = it.exportState.copy(selectedFormat = format, errorMessage = null))
        }
    }

    fun setIncludeToc(enabled: Boolean) {
        _uiState.update {
            it.copy(exportState = it.exportState.copy(includeToc = enabled))
        }
    }

    fun setIncludeIncompleteNotice(enabled: Boolean) {
        _uiState.update {
            it.copy(exportState = it.exportState.copy(includeIncompleteNotice = enabled))
        }
    }

    fun startExport() {
        val detail = _uiState.value.detail ?: return
        val exp = _uiState.value.exportState
        if (exp.isExporting) return

        val safeStem = detail.summary.title
            .replace(Regex("[^a-zA-Z0-9\\u3040-\\u9FFF_-]+"), "_")
            .trim('_')
            .ifBlank { detail.summary.id }
            .take(40)
        val outFile = File(exportDir, "${safeStem}.${exp.selectedFormat.extension}")
        val jobId = "export_${UUID.randomUUID()}"

        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    exportState = it.exportState.copy(
                        isExporting = true,
                        progress = 0f,
                        stageLabel = "検証中…",
                        lastCompletedPath = null,
                        errorMessage = null,
                    ),
                )
            }
            try {
                repository.exportBookStream(
                    jobId = jobId,
                    bookId = detail.summary.id,
                    format = exp.selectedFormat,
                    outputPath = outFile.absolutePath,
                    includeToc = exp.includeToc,
                    includeIncompleteNotice = exp.includeIncompleteNotice,
                ).collect { event ->
                    when (event) {
                        is ExportEvent.Progress -> {
                            _uiState.update {
                                it.copy(
                                    exportState = it.exportState.copy(
                                        progress = event.fraction,
                                        stageLabel = stageLabel(event.stage),
                                    ),
                                )
                            }
                        }
                        is ExportEvent.Completed -> {
                            _uiState.update {
                                it.copy(
                                    exportState = it.exportState.copy(
                                        isExporting = false,
                                        progress = 1f,
                                        stageLabel = "書き出し完了",
                                        lastCompletedPath = event.outputPath,
                                        lastBytesWritten = event.bytesWritten,
                                    ),
                                    snackbarMessage = "${event.format.name} を書き出しました (${event.bytesWritten / 1024} KiB)",
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        exportState = it.exportState.copy(
                            isExporting = false,
                            stageLabel = "キャンセルされました",
                        ),
                    )
                }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportState = it.exportState.copy(
                            isExporting = false,
                            errorMessage = e.message ?: "書き出しに失敗しました",
                        ),
                    )
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun parseChapterSections(detail: BookDetail, index: Int): List<ParsedSectionBlocks> {
        val chapterWithSecs = detail.chapters.getOrNull(index) ?: return emptyList()
        return chapterWithSecs.sections.map { sec ->
            val effectiveFormat = when {
                sec.content.isBlank() -> sec.format
                sec.format == MarkupFormat.MARKDOWN -> repository.detectMarkupFormat(sec.content)
                else -> sec.format
            }
            ParsedSectionBlocks(
                sectionId = sec.id,
                title = sec.title,
                chapterNumber = sec.chapterNumber,
                sectionNumber = sec.sectionNumber,
                charCount = sec.charCount,
                isCompleted = sec.status == com.bookgen.app.domain.model.SectionStatus.COMPLETED && sec.content.isNotBlank(),
                format = effectiveFormat,
                blocks = if (sec.content.isBlank()) emptyList() else repository.parseAstBlocks(sec.content, effectiveFormat),
            )
        }
    }

    private fun stageLabel(stage: ExportStage): String = when (stage) {
        ExportStage.VALIDATING -> "検証中…"
        ExportStage.LOADING -> "データ読込中…"
        ExportStage.PROCESSING -> "本文構成中…"
        ExportStage.BUILDING -> "ドキュメント生成中…"
        ExportStage.ENCODING -> "エンコード中…"
        ExportStage.WRITING -> "保存中…"
        ExportStage.DONE -> "完了"
    }
}
