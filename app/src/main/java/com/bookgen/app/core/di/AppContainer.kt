package com.bookgen.app.core.di

import android.content.Context
import com.bookgen.app.data.repository.RustBookRepository
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.domain.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Process-scoped container owning singleton Rust SQLite + Exporter bridge and app preferences. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val databaseFile: File = File(appContext.filesDir, "bookgen.db")

    val exportOutputDir: File = File(appContext.filesDir, "exports").apply {
        if (!exists()) mkdirs()
    }

    val bookRepository: BookRepository by lazy {
        RustBookRepository(dbPath = databaseFile.absolutePath)
    }

    private val _themePreference = MutableStateFlow(ThemePreference.SYSTEM)
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    fun setThemePreference(preference: ThemePreference) {
        _themePreference.value = preference
    }
}
