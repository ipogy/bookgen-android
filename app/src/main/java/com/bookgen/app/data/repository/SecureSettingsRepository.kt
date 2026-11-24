package com.bookgen.app.data.repository

import android.content.SharedPreferences
import com.bookgen.app.data.security.SecretCipher
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SecureSettingsRepository(
    private val prefs: SharedPreferences,
    private val cipher: SecretCipher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {

    private val _aiSettings = MutableStateFlow(readSnapshot())
    override val aiSettings: StateFlow<AiSettings> = _aiSettings.asStateFlow()

    override suspend fun updateAiSettings(settings: AiSettings) = withContext(ioDispatcher) {
        val normalized = settings.copy(
            geminiApiKey = settings.geminiApiKey.trim(),
            openRouterApiKey = settings.openRouterApiKey.trim(),
            embeddingModel = settings.embeddingModel.trim().ifEmpty { AiSettings.DEFAULT_EMBEDDING_MODEL },
            plannerModel = settings.plannerModel.trim().ifEmpty { AiSettings.DEFAULT_PLANNER_MODEL },
            writerModel = settings.writerModel.trim().ifEmpty { AiSettings.DEFAULT_WRITER_MODEL },
            openRouterProviders = settings.openRouterProviders.trim(),
            maxConcurrency = settings.maxConcurrency.coerceIn(1, 4),
        )
        val encGemini = if (normalized.geminiApiKey.isEmpty()) "" else cipher.encrypt(normalized.geminiApiKey)
        val encOpenRouter = if (normalized.openRouterApiKey.isEmpty()) "" else cipher.encrypt(normalized.openRouterApiKey)

        prefs.edit()
            .putString(KEY_ENC_GEMINI, encGemini)
            .putString(KEY_ENC_OPENROUTER, encOpenRouter)
            .putString(KEY_EMBEDDING_MODEL, normalized.embeddingModel)
            .putString(KEY_PLANNER_MODEL, normalized.plannerModel)
            .putString(KEY_WRITER_MODEL, normalized.writerModel)
            .putString(KEY_OPENROUTER_PROVIDERS, normalized.openRouterProviders)
            .putInt(KEY_MAX_CONCURRENCY, normalized.maxConcurrency)
            .apply()

        _aiSettings.value = normalized
    }

    override suspend fun clearApiKeys() = withContext(ioDispatcher) {
        prefs.edit()
            .remove(KEY_ENC_GEMINI)
            .remove(KEY_ENC_OPENROUTER)
            .apply()
        _aiSettings.value = _aiSettings.value.copy(
            geminiApiKey = "",
            openRouterApiKey = "",
        )
    }

    override fun getPreferredMarkupFormat(bookId: String): MarkupFormat {
        val raw = prefs.getString("$KEY_BOOK_FORMAT_PREFIX$bookId", null) ?: return MarkupFormat.MARKDOWN
        return runCatching { MarkupFormat.valueOf(raw) }.getOrDefault(MarkupFormat.MARKDOWN)
    }

    override fun setPreferredMarkupFormat(bookId: String, format: MarkupFormat) {
        prefs.edit().putString("$KEY_BOOK_FORMAT_PREFIX$bookId", format.name).apply()
    }

    private fun readSnapshot(): AiSettings {
        val gemini = safeDecrypt(prefs.getString(KEY_ENC_GEMINI, null))
        val openRouter = safeDecrypt(prefs.getString(KEY_ENC_OPENROUTER, null))
        return AiSettings(
            geminiApiKey = gemini,
            openRouterApiKey = openRouter,
            embeddingModel = prefs.getString(KEY_EMBEDDING_MODEL, null)
                ?.ifBlank { null } ?: AiSettings.DEFAULT_EMBEDDING_MODEL,
            plannerModel = prefs.getString(KEY_PLANNER_MODEL, null)
                ?.ifBlank { null } ?: AiSettings.DEFAULT_PLANNER_MODEL,
            writerModel = prefs.getString(KEY_WRITER_MODEL, null)
                ?.ifBlank { null } ?: AiSettings.DEFAULT_WRITER_MODEL,
            openRouterProviders = prefs.getString(KEY_OPENROUTER_PROVIDERS, null)
                ?: AiSettings.DEFAULT_OPENROUTER_PROVIDER,
            maxConcurrency = prefs.getInt(KEY_MAX_CONCURRENCY, AiSettings.DEFAULT_CONCURRENCY).coerceIn(1, 4),
        )
    }

    private fun safeDecrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return runCatching { cipher.decrypt(encoded) }.getOrDefault("")
    }

    private companion object {
        const val KEY_ENC_GEMINI = "enc_gemini_api_key"
        const val KEY_ENC_OPENROUTER = "enc_openrouter_api_key"
        const val KEY_EMBEDDING_MODEL = "embedding_model"
        const val KEY_PLANNER_MODEL = "planner_model"
        const val KEY_WRITER_MODEL = "writer_model"
        const val KEY_OPENROUTER_PROVIDERS = "openrouter_providers"
        const val KEY_MAX_CONCURRENCY = "max_concurrency"
        const val KEY_BOOK_FORMAT_PREFIX = "book_format_"
    }
}
