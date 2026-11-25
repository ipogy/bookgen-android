package com.bookgen.app.ui.screens

import android.content.SharedPreferences
import com.bookgen.app.data.pipeline.GenerationProgressBus
import com.bookgen.app.data.repository.RustBookRepository
import com.bookgen.app.data.repository.SecureSettingsRepository
import com.bookgen.app.data.security.SecretCipher
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.ChapterPlanDraft
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.NewBookRequest
import com.bookgen.app.domain.model.ReaderFontChoice
import com.bookgen.app.domain.model.SectionPlanDraft
import com.bookgen.app.domain.model.SectionStatus
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.ui.screens.reader.ReaderViewModel
import com.bookgen.app.ui.screens.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceAndReaderViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun workspacePartialRegenPassesChapterTargetsAndInstruction() = runTest {
        val dbPath = File(tempFolder.root, "ws_test.db").absolutePath
        val repo = RustBookRepository(dbPath = dbPath, ioDispatcher = testDispatcher)
        val settingsRepo = SecureSettingsRepository(
            prefs = FakePrefs(),
            cipher = FakeCipher(),
            ioDispatcher = testDispatcher,
        )
        settingsRepo.updateAiSettings(AiSettings(openRouterApiKey = "sk-or-test-key"))

        val book = repo.createBook(
            NewBookRequest(
                title = "部分再生成テスト",
                themePrompt = "Android Compose",
                genreKey = "tech",
                genreName = "IT技術",
                targetAudience = "開発者",
                tone = "論理",
                targetCharCount = 10_000L,
                overview = "概要",
            ),
        )
        repo.saveOutline(
            book.id,
            listOf(
                ChapterPlanDraft(
                    chapterNumber = 1,
                    title = "第1章",
                    focusArea = "基礎",
                    sections = listOf(
                        SectionPlanDraft(1, "第1節", "要点A"),
                        SectionPlanDraft(2, "第2節", "要点B"),
                    ),
                ),
            ),
        )
        val detail = repo.getBook(book.id)
        val secIds = detail.chapters.first().sections.map { it.id }

        var capturedSections: List<String>? = null
        var capturedInstruction: String? = null

        val vm = WorkspaceViewModel(
            bookId = book.id,
            repository = repo,
            settingsRepository = settingsRepo,
            progressBus = GenerationProgressBus(),
            isWorkRunningFlow = flowOf(false),
            onStartWork = { _, _, targetIds, instr ->
                capturedSections = targetIds
                capturedInstruction = instr
            },
            onCancelWork = {},
        )

        val chapter = vm.uiState.value.detail?.chapters?.first()
        assertNotNull(chapter)
        vm.openChapterRegenSheet(chapter!!)
        val target = vm.uiState.value.regenTarget
        assertNotNull(target)
        assertEquals(2, target!!.sectionIds.size)

        vm.confirmPartialRegeneration(target, "具体例と実装コードを増やす")

        assertEquals(secIds, capturedSections)
        assertEquals("具体例と実装コードを増やす", capturedInstruction)
        assertTrue(vm.uiState.value.regenTarget == null)
        assertEquals(SectionStatus.PENDING, repo.getBook(book.id).chapters.first().sections.first().status)
    }

    @Test
    fun readerPersistsAppearanceAndScrollPositionAcrossViewModels() = runTest {
        val dbPath = File(tempFolder.root, "reader_test.db").absolutePath
        val exportDir = tempFolder.newFolder("exports")
        val repo = RustBookRepository(dbPath = dbPath, ioDispatcher = testDispatcher)
        val settingsRepo = SecureSettingsRepository(
            prefs = FakePrefs(),
            cipher = FakeCipher(),
            ioDispatcher = testDispatcher,
        )

        val book = repo.createBook(
            NewBookRequest(
                title = "リーダー永続化テスト",
                themePrompt = "テスト",
                genreKey = "tech",
                genreName = "IT技術",
                targetAudience = "読者",
                tone = "明快",
                targetCharCount = 5_000L,
                overview = "テスト本",
            ),
        )
        val detail = repo.saveOutline(
            book.id,
            listOf(
                ChapterPlanDraft(1, "1章", "焦点", listOf(SectionPlanDraft(1, "1節", "概要"))),
                ChapterPlanDraft(2, "2章", "焦点2", listOf(SectionPlanDraft(1, "2-1節", "概要2"))),
            ),
        )
        val secId = detail.chapters[1].sections.first().id
        repo.upsertSectionContent(secId, "## 第2章\n本文テスト", MarkupFormat.MARKDOWN, "manual", "test")

        val vm1 = ReaderViewModel(
            bookId = book.id,
            initialChapterIndex = 0,
            exportDir = exportDir,
            repository = repo,
            settingsRepository = settingsRepo,
        )
        vm1.setTheme(ThemePreference.SEPIA)
        vm1.setFontChoice(ReaderFontChoice.SANS)
        vm1.setFontSizeSp(20f)
        vm1.selectChapter(1)
        vm1.persistScrollFraction(0.625)

        val vm2 = ReaderViewModel(
            bookId = book.id,
            initialChapterIndex = 0,
            exportDir = exportDir,
            repository = repo,
            settingsRepository = settingsRepo,
        )
        assertEquals(ThemePreference.SEPIA, vm2.uiState.value.appearance.theme)
        assertEquals(ReaderFontChoice.SANS, vm2.uiState.value.appearance.fontChoice)
        assertEquals(20f, vm2.uiState.value.appearance.fontSizeSp)
        assertEquals(1, vm2.uiState.value.currentChapterIndex)
        assertEquals(0.625, vm2.uiState.value.savedScrollFraction, 0.001)
    }

    @Test
    fun readerAutoUpgradesMislabeledMarkdownSectionToAsciiDocAndExtractsMathBlock() = runTest {
        val dbPath = File(tempFolder.root, "reader_upgrade_test.db").absolutePath
        val exportDir = tempFolder.newFolder("exports_upgrade")
        val repo = RustBookRepository(dbPath = dbPath, ioDispatcher = testDispatcher)
        val settingsRepo = SecureSettingsRepository(
            prefs = FakePrefs(),
            cipher = FakeCipher(),
            ioDispatcher = testDispatcher,
        )

        val book = repo.createBook(
            NewBookRequest(
                title = "金融数式テスト",
                themePrompt = "債券",
                genreKey = "tech",
                genreName = "IT技術",
                targetAudience = "読者",
                tone = "明快",
                targetCharCount = 5_000L,
                overview = "数式テスト",
            ),
        )
        val detail = repo.saveOutline(
            book.id,
            listOf(ChapterPlanDraft(1, "デュレーション", "債券", listOf(SectionPlanDraft(1, "概念", "数式")))),
        )
        val secId = detail.chapters.first().sections.first().id
        val asciidocContent = """
            *デュレーション分析* では次の式を使う。
            [stem]
            ++++
            D_{mac} = \frac{C}{P}
            ++++
        """.trimIndent()
        // Intentionally store as MARKDOWN to mimic legacy v0.1.0 DB row misclassification
        repo.upsertSectionContent(secId, asciidocContent, MarkupFormat.MARKDOWN, "legacy_import", "test")

        val vm = ReaderViewModel(
            bookId = book.id,
            initialChapterIndex = 0,
            exportDir = exportDir,
            repository = repo,
            settingsRepository = settingsRepo,
        )

        val parsed = vm.uiState.value.parsedSections.first()
        assertEquals(MarkupFormat.ASCIIDOC, parsed.format)
        assertTrue(parsed.blocks.any { it is com.bookgen.app.domain.model.DocBlock.MathBlock })
        assertTrue(
            parsed.blocks.filterIsInstance<com.bookgen.app.domain.model.DocBlock.Paragraph>()
                .none { it.text.contains("[stem]") || it.text.contains("++++") },
        )
    }

    private class FakeCipher : SecretCipher {
        override fun encrypt(plaintext: String): String =
            Base64.getEncoder().encodeToString(plaintext.toByteArray())
        override fun decrypt(encodedCiphertext: String): String =
            String(Base64.getDecoder().decode(encodedCiphertext))
    }

    private class FakePrefs : SharedPreferences {
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
