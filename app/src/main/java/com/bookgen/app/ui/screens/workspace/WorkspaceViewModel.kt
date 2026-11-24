package com.bookgen.app.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookgen.app.data.pipeline.GenerationProgressBus
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.IncompleteSection
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.SectionRecord
import com.bookgen.app.domain.model.SectionRevision
import com.bookgen.app.domain.model.SectionStatus
import com.bookgen.app.domain.repository.BookRepository
import com.bookgen.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val isLoading: Boolean = true,
    val detail: BookDetail? = null,
    val incompleteSections: List<IncompleteSection> = emptyList(),
    val expandedChapterIds: Set<String> = emptySet(),
    val isGenerating: Boolean = false,
    val activeWritingLabel: String = "待機中（準備完了）",
    val liveStreamPreview: String = "",
    val streamDrawerExpanded: Boolean = false,
    val editingSection: SectionRecord? = null,
    val editingRevisions: List<SectionRevision> = emptyList(),
    val showApiSettingsSheet: Boolean = false,
    val snackbarMessage: String? = null,
)

class WorkspaceViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val progressBus: GenerationProgressBus,
    private val isWorkRunningFlow: Flow<Boolean>,
    private val onStartWork: (bookId: String, formatOverride: MarkupFormat?) -> Unit,
    private val onCancelWork: (bookId: String) -> Unit,
) : ViewModel() {

    val aiSettings: StateFlow<AiSettings> = settingsRepository.aiSettings

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        loadBook()
        viewModelScope.launch {
            repository.observeLibraryRefresh().collect {
                loadBook()
            }
        }
        viewModelScope.launch {
            combine(
                progressBus.states.map { it[bookId] },
                isWorkRunningFlow,
            ) { busState, workerRunning ->
                busState to workerRunning
            }.collect { (busState, workerRunning) ->
                val running = (busState?.isRunning == true) || workerRunning
                _uiState.update { cur ->
                    val nextLabel = busState?.activeLabel
                        ?: if (workerRunning) "バックグラウンド執筆を実行中…" else cur.activeWritingLabel
                    val nextPreview = busState?.livePreview?.takeIf { it.isNotEmpty() } ?: cur.liveStreamPreview
                    cur.copy(
                        isGenerating = running,
                        activeWritingLabel = nextLabel,
                        liveStreamPreview = nextPreview,
                        streamDrawerExpanded = cur.streamDrawerExpanded || running,
                        snackbarMessage = busState?.lastError?.takeIf { !running && cur.isGenerating } ?: cur.snackbarMessage,
                    )
                }
            }
        }
    }

    fun loadBook() {
        viewModelScope.launch {
            try {
                val detail = repository.getBook(bookId)
                val incomplete = repository.listIncompleteSections(bookId)
                _uiState.update { cur ->
                    val expanded = if (cur.expandedChapterIds.isEmpty()) {
                        detail.chapters.firstOrNull()?.chapter?.id?.let(::setOf) ?: emptySet()
                    } else {
                        cur.expandedChapterIds
                    }
                    cur.copy(
                        isLoading = false,
                        detail = detail,
                        incompleteSections = incomplete,
                        expandedChapterIds = expanded,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = e.message ?: "書籍詳細の読み込みに失敗しました",
                    )
                }
            }
        }
    }

    fun toggleChapterExpanded(chapterId: String) {
        _uiState.update { cur ->
            val next = if (chapterId in cur.expandedChapterIds) {
                cur.expandedChapterIds - chapterId
            } else {
                cur.expandedChapterIds + chapterId
            }
            cur.copy(expandedChapterIds = next)
        }
    }

    fun toggleStreamDrawer() {
        _uiState.update { it.copy(streamDrawerExpanded = !it.streamDrawerExpanded) }
    }

    fun moveChapter(chapterIndex: Int, delta: Int) {
        val chapters = _uiState.value.detail?.chapters ?: return
        val targetIndex = chapterIndex + delta
        if (targetIndex !in chapters.indices) return
        val reordered = chapters.map { it.chapter.id }.toMutableList()
        val moved = reordered.removeAt(chapterIndex)
        reordered.add(targetIndex, moved)
        viewModelScope.launch {
            runAction("章を並べ替えました") {
                repository.reorderChapters(bookId, reordered)
            }
        }
    }

    fun moveSection(chapterId: String, sectionIndex: Int, delta: Int) {
        val chapter = _uiState.value.detail?.chapters?.firstOrNull { it.chapter.id == chapterId } ?: return
        val targetIndex = sectionIndex + delta
        if (targetIndex !in chapter.sections.indices) return
        val reordered = chapter.sections.map { it.id }.toMutableList()
        val moved = reordered.removeAt(sectionIndex)
        reordered.add(targetIndex, moved)
        viewModelScope.launch {
            runAction("節の順序を更新しました") {
                repository.reorderSections(chapterId, reordered)
            }
        }
    }

    fun addChapter(title: String, focusArea: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runAction("新しい章を追加しました") {
                val created = repository.addChapter(bookId, title.trim(), focusArea.trim())
                _uiState.update { cur ->
                    cur.copy(expandedChapterIds = cur.expandedChapterIds + created.chapter.id)
                }
            }
        }
    }

    fun updateChapterMeta(chapterId: String, title: String, focusArea: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runAction("章情報を更新しました") {
                repository.updateChapterMeta(chapterId, title.trim(), focusArea.trim())
            }
        }
    }

    fun addSection(chapterId: String, title: String, keyFocus: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runAction("新しい節を追加しました") {
                repository.addSection(chapterId, title.trim(), keyFocus.trim())
            }
        }
    }

    fun deleteSection(sectionId: String) {
        viewModelScope.launch {
            runAction("節を削除しました") {
                repository.deleteSection(sectionId)
            }
        }
    }

    fun openSectionEditor(section: SectionRecord) {
        viewModelScope.launch {
            val revisions = runCatching { repository.listSectionRevisions(section.id) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    editingSection = section,
                    editingRevisions = revisions,
                )
            }
        }
    }

    fun closeSectionEditor() {
        _uiState.update { it.copy(editingSection = null, editingRevisions = emptyList()) }
    }

    fun saveSectionContent(
        sectionId: String,
        title: String,
        content: String,
        format: MarkupFormat,
    ) {
        viewModelScope.launch {
            runAction("節の編集を保存しました（他節は保持）") {
                val current = _uiState.value.editingSection
                if (current != null && title.isNotBlank() && title.trim() != current.title) {
                    repository.updateSectionMeta(sectionId, title.trim(), current.keyFocus)
                }
                repository.upsertSectionContent(
                    sectionId = sectionId,
                    content = content,
                    format = format,
                    source = "manual_edit",
                    note = "Android Studio Workspace edit",
                )
                _uiState.update { it.copy(editingSection = null, editingRevisions = emptyList()) }
            }
        }
    }

    fun restoreRevision(sectionId: String, revisionId: Long) {
        viewModelScope.launch {
            runAction("リビジョンを復元しました") {
                val restored = repository.restoreSectionRevision(sectionId, revisionId)
                val revs = repository.listSectionRevisions(sectionId)
                _uiState.update { it.copy(editingSection = restored, editingRevisions = revs) }
            }
        }
    }

    fun markSectionPendingForRegen(sectionId: String, note: String? = null) {
        viewModelScope.launch {
            runAction("再執筆待ちに設定しました") {
                repository.setSectionStatus(sectionId, SectionStatus.PENDING, note)
            }
        }
    }

    fun setStreamStatusHint(label: String, preview: String) {
        _uiState.update {
            it.copy(
                activeWritingLabel = label,
                liveStreamPreview = preview,
                streamDrawerExpanded = true,
            )
        }
    }

    fun startOrResumeGeneration() {
        val settings = settingsRepository.aiSettings.value
        if (!settings.hasKeyForModel(settings.writerModel)) {
            _uiState.update {
                it.copy(
                    showApiSettingsSheet = true,
                    snackbarMessage = "執筆を開始するには API キーの設定が必要です",
                )
            }
            return
        }
        val preferredFormat = settingsRepository.getPreferredMarkupFormat(bookId)
        onStartWork(bookId, preferredFormat)
        _uiState.update {
            it.copy(
                isGenerating = true,
                streamDrawerExpanded = true,
                activeWritingLabel = "バックグラウンド執筆ワーカーを起動中…",
                snackbarMessage = "バックグラウンド執筆を開始しました",
            )
        }
    }

    fun pauseGeneration() {
        onCancelWork(bookId)
        _uiState.update {
            it.copy(
                isGenerating = false,
                snackbarMessage = "執筆を一時停止しました（未執筆の節は保存済み状態から再開できます）",
            )
        }
    }

    fun openApiSettings() {
        _uiState.update { it.copy(showApiSettingsSheet = true) }
    }

    fun dismissApiSettings() {
        _uiState.update { it.copy(showApiSettingsSheet = false) }
    }

    fun saveAiSettings(settings: AiSettings) {
        viewModelScope.launch {
            settingsRepository.updateAiSettings(settings)
            _uiState.update {
                it.copy(
                    showApiSettingsSheet = false,
                    snackbarMessage = "API設定を保存しました",
                )
            }
        }
    }

    fun clearAiKeys() {
        viewModelScope.launch {
            settingsRepository.clearApiKeys()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private suspend fun runAction(successMessage: String, block: suspend () -> Unit) {
        try {
            block()
            _uiState.update { it.copy(snackbarMessage = successMessage) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(snackbarMessage = e.message ?: "操作に失敗しました")
            }
        }
    }
}
