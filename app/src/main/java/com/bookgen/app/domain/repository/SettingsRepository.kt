package com.bookgen.app.domain.repository

import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.MarkupFormat
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val aiSettings: StateFlow<AiSettings>

    suspend fun updateAiSettings(settings: AiSettings)

    suspend fun clearApiKeys()

    fun getPreferredMarkupFormat(bookId: String): MarkupFormat

    fun setPreferredMarkupFormat(bookId: String, format: MarkupFormat)
}
