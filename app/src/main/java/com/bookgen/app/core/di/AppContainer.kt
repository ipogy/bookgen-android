package com.bookgen.app.core.di

import android.content.Context
import com.bookgen.app.data.pipeline.GenerationPipeline
import com.bookgen.app.data.pipeline.GenerationProgressBus
import com.bookgen.app.data.remote.LlmClient
import com.bookgen.app.data.remote.UnifiedLlmHttpClient
import com.bookgen.app.data.repository.RustBookRepository
import com.bookgen.app.data.repository.SecureSettingsRepository
import com.bookgen.app.data.security.KeystoreSecretCipher
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.domain.repository.BookRepository
import com.bookgen.app.domain.repository.SettingsRepository
import com.bookgen.app.worker.BookGenerationWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Process-scoped container owning singleton Rust SQLite + Exporter bridge, BYOK vault, and AI pipeline. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val databaseFile: File = File(appContext.filesDir, "bookgen.db")

    val exportOutputDir: File = File(appContext.filesDir, "exports").apply {
        if (!exists()) mkdirs()
    }

    val bookRepository: BookRepository by lazy {
        RustBookRepository(dbPath = databaseFile.absolutePath)
    }

    val settingsRepository: SettingsRepository by lazy {
        SecureSettingsRepository(
            prefs = appContext.getSharedPreferences("bookgen_secure_settings", Context.MODE_PRIVATE),
            cipher = KeystoreSecretCipher(),
        )
    }

    val llmClient: LlmClient by lazy {
        UnifiedLlmHttpClient()
    }

    val progressBus: GenerationProgressBus = GenerationProgressBus()

    val generationPipeline: GenerationPipeline by lazy {
        GenerationPipeline(
            repository = bookRepository,
            settingsRepository = settingsRepository,
            llmClient = llmClient,
            progressBus = progressBus,
        )
    }

    private val _themePreference by lazy {
        MutableStateFlow(settingsRepository.getThemePreference())
    }
    val themePreference: StateFlow<ThemePreference>
        get() = _themePreference.asStateFlow()

    fun setThemePreference(preference: ThemePreference) {
        settingsRepository.setThemePreference(preference)
        _themePreference.value = preference
    }

    fun startBookGeneration(
        bookId: String,
        targetSectionIds: List<String>? = null,
        customInstruction: String? = null,
        markupFormatOverride: MarkupFormat? = null,
    ) {
        BookGenerationWorker.enqueueBookGeneration(
            context = appContext,
            bookId = bookId,
            targetSectionIds = targetSectionIds,
            customInstruction = customInstruction,
            markupFormatOverride = markupFormatOverride,
        )
    }

    fun cancelBookGeneration(bookId: String) {
        BookGenerationWorker.cancelBookGeneration(context = appContext, bookId = bookId)
    }

    fun observeGenerationRunning(bookId: String): Flow<Boolean> =
        BookGenerationWorker.observeIsRunning(context = appContext, bookId = bookId)
}
