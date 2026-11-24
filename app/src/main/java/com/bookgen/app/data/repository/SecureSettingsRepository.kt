package com.bookgen.app.data.repository

import android.content.SharedPreferences
import com.bookgen.app.data.security.SecretCipher
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.ReaderAppearance
import com.bookgen.app.domain.model.ReaderFontChoice
import com.bookgen.app.domain.model.ThemePreference
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

    override fun getThemePreference(): ThemePreference {
        val raw = prefs.getString(KEY_THEME_PREF, null) ?: return ThemePreference.SYSTEM
        return runCatching { ThemePreference.valueOf(raw) }.getOrDefault(ThemePreference.SYSTEM)
    }

    override fun setThemePreference(preference: ThemePreference) {
        prefs.edit().putString(KEY_THEME_PREF, preference.name).apply()
    }

    override fun getReaderAppearance(): ReaderAppearance {
        val themeRaw = prefs.getString(KEY_READER_THEME, null)
        val theme = themeRaw?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ThemePreference.SYSTEM }
            ?: when (getThemePreference()) {
                ThemePreference.DARK -> ThemePreference.DARK
                ThemePreference.SEPIA -> ThemePreference.SEPIA
                ThemePreference.LIGHT, ThemePreference.SYSTEM -> ThemePreference.LIGHT
            }
        val fontRaw = prefs.getString(KEY_READER_FONT, null)
        val font = fontRaw?.let { runCatching { ReaderFontChoice.valueOf(it) }.getOrNull() }
            ?: ReaderFontChoice.SERIF
        val sizeSp = prefs.getFloat(KEY_READER_FONT_SIZE, 17f).coerceIn(14f, 24f)
        val lineHeight = prefs.getFloat(KEY_READER_LINE_HEIGHT, 1.75f).coerceIn(1.4f, 2.1f)
        val paddingDp = prefs.getInt(KEY_READER_PADDING_DP, 20).coerceIn(12, 36)
        return ReaderAppearance(
            theme = theme,
            fontChoice = font,
            fontSizeSp = sizeSp,
            lineHeightMultiplier = lineHeight,
            horizontalPaddingDp = paddingDp,
        )
    }

    override fun setReaderAppearance(appearance: ReaderAppearance) {
        prefs.edit()
            .putString(KEY_READER_THEME, appearance.theme.name)
            .putString(KEY_READER_FONT, appearance.fontChoice.name)
            .putFloat(KEY_READER_FONT_SIZE, appearance.fontSizeSp.coerceIn(14f, 24f))
            .putFloat(KEY_READER_LINE_HEIGHT, appearance.lineHeightMultiplier.coerceIn(1.4f, 2.1f))
            .putInt(KEY_READER_PADDING_DP, appearance.horizontalPaddingDp.coerceIn(12, 36))
            .apply()
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
        const val KEY_THEME_PREF = "app_theme_pref"
        const val KEY_READER_THEME = "reader_theme"
        const val KEY_READER_FONT = "reader_font"
        const val KEY_READER_FONT_SIZE = "reader_font_size"
        const val KEY_READER_LINE_HEIGHT = "reader_line_height"
        const val KEY_READER_PADDING_DP = "reader_padding_dp"
    }
}
