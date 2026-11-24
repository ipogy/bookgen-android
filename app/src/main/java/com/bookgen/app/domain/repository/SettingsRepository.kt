package com.bookgen.app.domain.repository

import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.ReaderAppearance
import com.bookgen.app.domain.model.ThemePreference
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val aiSettings: StateFlow<AiSettings>

    suspend fun updateAiSettings(settings: AiSettings)

    suspend fun clearApiKeys()

    fun getPreferredMarkupFormat(bookId: String): MarkupFormat

    fun setPreferredMarkupFormat(bookId: String, format: MarkupFormat)

    fun getThemePreference(): ThemePreference

    fun setThemePreference(preference: ThemePreference)

    fun getReaderAppearance(): ReaderAppearance

    fun setReaderAppearance(appearance: ReaderAppearance)
}
