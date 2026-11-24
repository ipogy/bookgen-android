package com.bookgen.app.data.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BookGenerationLiveState(
    val bookId: String,
    val isRunning: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val activeSectionId: String? = null,
    val activeLabel: String = "待機中（準備完了）",
    val livePreview: String = "",
    val lastError: String? = null,
)

/**
 * In-process reactive bridge so [com.bookgen.app.worker.BookGenerationWorker] can
 * broadcast throttled token streaming snippets to active ViewModels without overloading
 * WorkManager Data serialization (which has a 10 KB cap).
 */
class GenerationProgressBus {
    private val _states = MutableStateFlow<Map<String, BookGenerationLiveState>>(emptyMap())
    val states: StateFlow<Map<String, BookGenerationLiveState>> = _states.asStateFlow()

    fun update(bookId: String, transform: (BookGenerationLiveState) -> BookGenerationLiveState) {
        _states.update { map ->
            val current = map[bookId] ?: BookGenerationLiveState(bookId = bookId)
            map + (bookId to transform(current))
        }
    }

    fun get(bookId: String): BookGenerationLiveState? = _states.value[bookId]
}
