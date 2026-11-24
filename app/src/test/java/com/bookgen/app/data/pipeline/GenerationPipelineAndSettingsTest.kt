package com.bookgen.app.data.pipeline

import android.content.SharedPreferences
import com.bookgen.app.data.remote.LlmClient
import com.bookgen.app.data.remote.LlmHttpException
import com.bookgen.app.data.repository.RustBookRepository
import com.bookgen.app.data.repository.SecureSettingsRepository
import com.bookgen.app.data.security.SecretCipher
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.NewBookRequest
import com.bookgen.app.domain.model.SectionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationPipelineAndSettingsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun settingsVaultRedactsKeysAndStoresEncryptedValues() = runTest {
        val prefs = InMemorySharedPreferences()
        val cipher = ReversingBase64Cipher()
        val repo = SecureSettingsRepository(
            prefs = prefs,
            cipher = cipher,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val secretGemini = "AIzaSyTestSecretKey987654"
        val secretOpenRouter = "sk-or-v1-secret-abc-999"
        repo.updateAiSettings(
            AiSettings(
                geminiApiKey = secretGemini,
                openRouterApiKey = secretOpenRouter,
                maxConcurrency = 3,
            ),
        )

        val storedRawValues = prefs.snapshot().values.joinToString("|")
        assertFalse("Plaintext Gemini key must not sit in prefs", storedRawValues.contains(secretGemini))
        assertFalse("Plaintext OpenRouter key must not sit in prefs", storedRawValues.contains(secretOpenRouter))

        val loaded = SecureSettingsRepository(
            prefs = prefs,
            cipher = cipher,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        assertEquals(secretGemini, loaded.aiSettings.value.geminiApiKey)
        assertEquals(secretOpenRouter, loaded.aiSettings.value.openRouterApiKey)
        assertEquals(3, loaded.aiSettings.value.maxConcurrency)
        assertFalse("AiSettings.toString must redact secrets", loaded.aiSettings.value.toString().contains("987654"))

        loaded.setPreferredMarkupFormat("book-42", MarkupFormat.ASCIIDOC)
        assertEquals(MarkupFormat.ASCIIDOC, loaded.getPreferredMarkupFormat("book-42"))
    }

    @Test
    fun pipelinePersistsIncrementalSectionsAndResumesOnlyFailedSection() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dbPath = File(tempFolder.root, "pipeline_test.db").absolutePath
        val bookRepo = RustBookRepository(dbPath = dbPath, ioDispatcher = dispatcher)
        val settingsRepo = SecureSettingsRepository(
            prefs = InMemorySharedPreferences(),
            cipher = ReversingBase64Cipher(),
            ioDispatcher = dispatcher,
        )
        settingsRepo.updateAiSettings(
            AiSettings(
                geminiApiKey = "AIzaFakeKey",
                openRouterApiKey = "sk-or-FakeKey",
                maxConcurrency = 2,
            ),
        )

        val fakeClient = FakeLlmClient()
        val bus = GenerationProgressBus()
        val pipeline = GenerationPipeline(bookRepo, settingsRepo, fakeClient, bus)

        val planned = pipeline.planOutlineWithAi(
            theme = "Rust と Android NDK 高速化",
            targetAudience = "モバイルエンジニア",
            tone = "厳密・論理",
            chapterCount = 1,
            sectionsPerChapter = 2,
            preferredGenre = null,
            autoDetectGenre = true,
        )
        assertEquals(1, planned.chapters.size)

        val summary = bookRepo.createBook(
            NewBookRequest(
                title = planned.draft.bookTitle,
                themePrompt = "Rust と Android NDK 高速化",
                genreKey = planned.genre.key,
                genreName = planned.genre.name,
                targetAudience = planned.draft.targetAudience,
                tone = "厳密・論理",
                targetCharCount = 12_000L,
                overview = planned.draft.overview,
            ),
        )
        bookRepo.saveOutline(summary.id, planned.chapters)

        // First run: Section 2 fails deliberately while Section 1 succeeds and persists
        fakeClient.failSectionTitleSubstring = "担当節: 第2節"
        val firstRun = pipeline.executeBookGeneration(summary.id)
        assertEquals(2, firstRun.totalTargets)
        assertEquals(1, firstRun.succeeded)
        assertEquals(1, firstRun.failed)

        val afterFirst = bookRepo.getBook(summary.id)
        val sec1 = afterFirst.chapters[0].sections[0]
        val sec2 = afterFirst.chapters[0].sections[1]
        assertEquals(SectionStatus.COMPLETED, sec1.status)
        assertEquals(SectionStatus.FAILED, sec2.status)
        assertTrue(bus.get(summary.id)?.isRunning == false)

        // Resume run: ONLY Section 2 must be requested!
        fakeClient.failSectionTitleSubstring = null
        fakeClient.streamCalls.clear()
        val resumeRun = pipeline.executeBookGeneration(summary.id)
        assertEquals(1, resumeRun.totalTargets)
        assertEquals(1, resumeRun.succeeded)
        assertEquals(0, resumeRun.failed)
        assertEquals(1, fakeClient.streamCalls.size)

        val afterResume = bookRepo.getBook(summary.id)
        assertTrue(afterResume.chapters[0].sections.all { it.status == SectionStatus.COMPLETED })
    }

    private class FakeLlmClient : LlmClient {
        var failSectionTitleSubstring: String? = null
        val streamCalls = mutableListOf<String>()

        override suspend fun embedText(model: String, text: String, settings: AiSettings): List<Float> =
            FloatArray(3072) { if (it % 2 == 0) 0.5f else 0.1f }.toList()

        override suspend fun generateText(
            model: String,
            systemInstruction: String,
            prompt: String,
            temperature: Double,
            settings: AiSettings,
        ): String = """
            {
              "book_title": "実践 Rust Android",
              "target_audience": "Android 開発者",
              "overview": "UniFFI の設計。",
              "chapters": [
                {
                  "chapter_number": 1,
                  "title": "基盤設計",
                  "focus_area": "NDK と UniFFI",
                  "sections": [
                    {"section_number": 1, "title": "第1節 ABI 設定", "key_focus": "16KB ページ境界"},
                    {"section_number": 2, "title": "第2節 パニック安全", "key_focus": "catch_unwind"}
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
        ): Flow<String> = flow {
            streamCalls += prompt
            val failSub = failSectionTitleSubstring
            if (failSub != null && prompt.contains(failSub)) {
                throw LlmHttpException(500, "Simulated upstream 500 error")
            }
            emit("## 解説セクション\n")
            emit("Android と Rust を UniFFI で安全に結合するには、パニックを境界内に閉じ込め、`Result<T, E>` として返却することが不可欠です。さらに WAL モードで即座に永続化します。")
        }
    }

    private class ReversingBase64Cipher : SecretCipher {
        override fun encrypt(plaintext: String): String =
            Base64.getEncoder().encodeToString(plaintext.reversed().toByteArray(Charsets.UTF_8))

        override fun decrypt(encodedCiphertext: String): String =
            String(Base64.getDecoder().decode(encodedCiphertext), Charsets.UTF_8).reversed()
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        fun snapshot(): Map<String, Any?> = data.toMap()

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
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in pending) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
