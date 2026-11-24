package com.bookgen.app.ui.screens

import android.content.SharedPreferences
import com.bookgen.app.data.pipeline.GenerationPipeline
import com.bookgen.app.data.pipeline.GenerationProgressBus
import com.bookgen.app.data.remote.LlmClient
import com.bookgen.app.data.repository.RustBookRepository
import com.bookgen.app.data.repository.SecureSettingsRepository
import com.bookgen.app.data.security.SecretCipher
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.ui.screens.wizard.StructureTemplate
import com.bookgen.app.ui.screens.wizard.WizardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class WizardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun wizardCreatesAiOutlineAndStoresPreferredMarkupFormat() = runTest {
        val dbPath = File(tempFolder.root, "wizard_ai.db").absolutePath
        val repo = RustBookRepository(dbPath = dbPath, ioDispatcher = testDispatcher)
        val settingsRepo = SecureSettingsRepository(
            prefs = InMemoryPrefs(),
            cipher = PassThroughBase64Cipher(),
            ioDispatcher = testDispatcher,
        )
        settingsRepo.updateAiSettings(
            AiSettings(
                geminiApiKey = "AIzaTestKey1234",
                openRouterApiKey = "sk-or-test-1234",
            ),
        )

        val pipeline = GenerationPipeline(
            repository = repo,
            settingsRepository = settingsRepo,
            llmClient = StubPlannerClient(),
            progressBus = GenerationProgressBus(),
        )
        val vm = WizardViewModel(repo, settingsRepo, pipeline)

        assertTrue(vm.uiState.value.genres.size >= 12)
        assertTrue(vm.uiState.value.hasPlannerKey)

        vm.onThemeChanged("Kotlin と Rust UniFFI で作る堅牢モバイル基盤")
        vm.onSelectTemplate(StructureTemplate.FOCUSED)
        vm.onFormatSelected(MarkupFormat.ASCIIDOC)

        var createdId: String? = null
        vm.createPlannedBook { createdId = it }

        val bookId = requireNotNull(createdId)
        val detail = repo.getBook(bookId)
        assertEquals("AI 設計による UniFFI 入門", detail.summary.title)
        assertEquals(MarkupFormat.ASCIIDOC, settingsRepo.getPreferredMarkupFormat(bookId))
        assertEquals(1, detail.chapters.size)
        assertEquals(2, detail.chapters.first().sections.size)
    }

    @Test
    fun wizardFallsBackToOfflineSkeletonWithoutApiKey() = runTest {
        val dbPath = File(tempFolder.root, "wizard_offline.db").absolutePath
        val repo = RustBookRepository(dbPath = dbPath, ioDispatcher = testDispatcher)
        val settingsRepo = SecureSettingsRepository(
            prefs = InMemoryPrefs(),
            cipher = PassThroughBase64Cipher(),
            ioDispatcher = testDispatcher,
        )
        val pipeline = GenerationPipeline(repo, settingsRepo, StubPlannerClient(), GenerationProgressBus())
        val vm = WizardViewModel(repo, settingsRepo, pipeline)

        assertFalse(vm.uiState.value.hasPlannerKey)
        vm.onThemeChanged("オフライン構成テスト")
        vm.onChapterCountChanged(3)
        vm.onSectionsPerChapterChanged(2)

        var createdId: String? = null
        vm.createPlannedBook { createdId = it }

        assertNotNull(createdId)
        val detail = repo.getBook(createdId!!)
        assertEquals(3, detail.chapters.size)
        assertEquals(6, detail.summary.totalSections)
    }

    private class StubPlannerClient : LlmClient {
        override suspend fun embedText(model: String, text: String, settings: AiSettings): List<Float> =
            FloatArray(3072) { 0.25f }.toList()

        override suspend fun generateText(
            model: String,
            systemInstruction: String,
            prompt: String,
            temperature: Double,
            settings: AiSettings,
        ): String = """
            {
              "book_title": "AI 設計による UniFFI 入門",
              "target_audience": "Android / Rust エンジニア",
              "overview": "実践的な FFI アーキテクチャを解説。",
              "chapters": [
                {
                  "chapter_number": 1,
                  "title": "境界設計の原則",
                  "focus_area": "型変換とエラー",
                  "sections": [
                    {"section_number": 1, "title": "Rust 側の設計", "key_focus": "guard_ffi"},
                    {"section_number": 2, "title": "Kotlin 側の受け口", "key_focus": "AppDomainException"}
                  ]
                }
              ]
            }
        """.trimIndent()

        override fun streamText(
            model: String,
            systemInstruction: String,
            prompt: String,
            temperature: Double,
            settings: AiSettings,
        ): Flow<String> = emptyFlow()
    }

    private class PassThroughBase64Cipher : SecretCipher {
        override fun encrypt(plaintext: String): String =
            Base64.getEncoder().encodeToString(plaintext.toByteArray())

        override fun decrypt(encodedCiphertext: String): String =
            String(Base64.getDecoder().decode(encodedCiphertext))
    }

    private class InMemoryPrefs : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            data[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var cleared = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { cleared = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (cleared) data.clear()
                for ((k, v) in pending) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
