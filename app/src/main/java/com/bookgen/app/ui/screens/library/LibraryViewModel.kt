package com.bookgen.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.BookSummary
import com.bookgen.app.domain.model.LegacyImportSummary
import com.bookgen.app.domain.model.SearchHit
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.domain.repository.BookRepository
import com.bookgen.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("すべて"),
    WRITING("執筆中"),
    NEEDS_RESUME("要再開"),
    COMPLETED("完了"),
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<BookSummary> = emptyList(),
    val filteredBooks: List<BookSummary> = emptyList(),
    val heroBook: BookSummary? = null,
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val importStatusMessage: String? = null,
    val isImporting: Boolean = false,
)

class LibraryViewModel(
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    val themePreference: StateFlow<ThemePreference>,
    private val onThemeChange: (ThemePreference) -> Unit,
) : ViewModel() {

    val aiSettings: StateFlow<AiSettings> = settingsRepository.aiSettings

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeLibraryRefresh().collect {
                loadLibrary()
            }
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.books.isEmpty(), errorMessage = null) }
            try {
                val all = repository.listBooks()
                _uiState.update { cur ->
                    val filtered = applyFilter(all, cur.activeFilter)
                    val hero = pickHeroBook(all)
                    cur.copy(
                        isLoading = false,
                        books = all,
                        filteredBooks = filtered,
                        heroBook = hero,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "書籍一覧の読み込みに失敗しました",
                    )
                }
            }
        }
    }

    fun onFilterSelected(filter: LibraryFilter) {
        _uiState.update { cur ->
            cur.copy(
                activeFilter = filter,
                filteredBooks = applyFilter(cur.books, filter),
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchHits = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(180)
            try {
                val hits = repository.search(trimmed, bookIdFilter = null, limit = 40)
                _uiState.update { it.copy(searchHits = hits, isSearching = false, errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = e.message ?: "検索に失敗しました",
                    )
                }
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            try {
                repository.deleteBook(bookId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "削除に失敗しました")
                }
            }
        }
    }

    fun importLegacyDirectory(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importStatusMessage = null, errorMessage = null) }
            try {
                val report: LegacyImportSummary = repository.importLegacyBookgen(path.trim())
                val imported = report.dbBooksImported + report.fsBooksImported
                val summaryMsg = buildString {
                    append("${imported}冊を取り込みました（${report.sectionsImported}節）")
                    if (report.emptySectionsFlagged > 0) {
                        append("・空応答 ${report.emptySectionsFlagged}節を要再開に設定")
                    }
                    if (report.booksSkippedExisting > 0) {
                        append("・既存スキップ ${report.booksSkippedExisting}冊")
                    }
                }
                _uiState.update { it.copy(isImporting = false, importStatusMessage = summaryMsg) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = e.message ?: "インポートに失敗しました",
                    )
                }
            }
        }
    }

    fun updateThemePreference(preference: ThemePreference) {
        onThemeChange(preference)
    }

    fun saveAiSettings(settings: AiSettings) {
        viewModelScope.launch {
            runCatching { settingsRepository.updateAiSettings(settings) }
                .onSuccess {
                    _uiState.update { it.copy(importStatusMessage = "AI設定とAPIキーを暗号化保存しました") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message ?: "設定の保存に失敗しました") }
                }
        }
    }

    fun clearAiKeys() {
        viewModelScope.launch {
            runCatching { settingsRepository.clearApiKeys() }
                .onSuccess {
                    _uiState.update { it.copy(importStatusMessage = "APIキーを削除しました") }
                }
        }
    }

    fun clearBannerMessages() {
        _uiState.update { it.copy(errorMessage = null, importStatusMessage = null) }
    }

    private fun applyFilter(books: List<BookSummary>, filter: LibraryFilter): List<BookSummary> {
        return when (filter) {
            LibraryFilter.ALL -> books
            LibraryFilter.WRITING -> books.filter {
                it.status == BookStatus.GENERATING || it.status == BookStatus.OUTLINED || it.status == BookStatus.DRAFT
            }
            LibraryFilter.NEEDS_RESUME -> books.filter {
                it.status == BookStatus.PARTIAL || it.status == BookStatus.FAILED || it.emptyOrFailedSections > 0
            }
            LibraryFilter.COMPLETED -> books.filter { it.status == BookStatus.COMPLETED }
        }
    }

    private fun pickHeroBook(books: List<BookSummary>): BookSummary? {
        return books.firstOrNull {
            it.status == BookStatus.GENERATING ||
                it.status == BookStatus.PARTIAL ||
                it.status == BookStatus.FAILED ||
                it.emptyOrFailedSections > 0
        } ?: books.firstOrNull()
    }
}
