package com.bookgen.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.BookSummary
import com.bookgen.app.domain.model.ChapterRecord
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.GenreInfo
import com.bookgen.app.domain.model.IncompleteSection
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.ReaderAppearance
import com.bookgen.app.domain.model.ReaderFontChoice
import com.bookgen.app.domain.model.SectionRecord
import com.bookgen.app.domain.model.SectionStatus
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.ui.screens.library.LibraryScreen
import com.bookgen.app.ui.screens.library.LibraryUiState
import com.bookgen.app.ui.screens.reader.ParsedSectionBlocks
import com.bookgen.app.ui.screens.reader.ReaderScreen
import com.bookgen.app.ui.screens.reader.ReaderUiState
import com.bookgen.app.ui.screens.wizard.WizardScreen
import com.bookgen.app.ui.screens.wizard.WizardUiState
import com.bookgen.app.ui.screens.workspace.WorkspaceScreen
import com.bookgen.app.ui.screens.workspace.WorkspaceUiState
import com.bookgen.app.ui.theme.BookGenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookGenComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryScreenDisplaysHeroCardAndTriggersNewBookCallback() {
        val sampleBook = sampleSummary()
        var newBookClicked = false
        var resumedId: String? = null

        composeRule.setContent {
            BookGenTheme {
                LibraryScreen(
                    state = LibraryUiState(
                        isLoading = false,
                        books = listOf(sampleBook),
                        filteredBooks = listOf(sampleBook),
                        heroBook = sampleBook,
                    ),
                    currentTheme = ThemePreference.LIGHT,
                    aiSettings = AiSettings(),
                    onNewBookClick = { newBookClicked = true },
                    onOpenWorkspace = { resumedId = it },
                    onOpenReader = { _, _ -> },
                    onFilterSelected = {},
                    onSearchQueryChanged = {},
                    onDeleteBook = {},
                    onImportDirectory = {},
                    onThemeSelected = {},
                    onSaveAiSettings = {},
                    onClearAiKeys = {},
                    onDismissMessages = {},
                )
            }
        }

        composeRule.onNodeWithText("Co-Author Studio").assertIsDisplayed()
        composeRule.onNodeWithText("続きから執筆").assertIsDisplayed().performClick()
        assertEquals("book-1", resumedId)

        composeRule.onNodeWithText("新しい本を書く").performClick()
        assertTrue(newBookClicked)
    }

    @Test
    fun wizardScreenTriggersSubmitAndFormatSelection() {
        var submitted = false
        var chosenFormat: MarkupFormat? = null

        composeRule.setContent {
            BookGenTheme {
                WizardScreen(
                    state = WizardUiState(
                        themePrompt = "Kotlin と Rust 設計ガイド",
                        genres = listOf(
                            GenreInfo(
                                key = "tech",
                                name = "IT技術・プログラミング",
                                seedText = "Kotlin",
                                editorGuide = "実務ガイド",
                                elementLabel = "実装コードとトレードオフ",
                                authorStyle = "明快",
                            ),
                        ),
                        autoDetectGenre = false,
                    ),
                    aiSettings = AiSettings(),
                    onBack = {},
                    onThemeChanged = {},
                    onTitleChanged = {},
                    onAudienceChanged = {},
                    onToggleAutoDetect = {},
                    onSelectGenre = {},
                    onSelectTemplate = {},
                    onChapterCountChanged = {},
                    onSectionsPerChapterChanged = {},
                    onFormatSelected = { chosenFormat = it },
                    onToneSelected = {},
                    onToggleUseAiOutline = {},
                    onSaveAiSettings = {},
                    onClearAiKeys = {},
                    onSubmit = { submitted = true },
                )
            }
        }

        composeRule.onNodeWithText("AsciiDoc").performClick()
        assertEquals(MarkupFormat.ASCIIDOC, chosenFormat)

        composeRule.onNodeWithText("構成案を生成する").performClick()
        assertTrue(submitted)
    }

    @Test
    fun workspaceScreenTogglesPauseAndOpensRegenerationSheet() {
        var paused = false
        var chapterRegenOpened = false
        val detail = sampleDetail()

        composeRule.setContent {
            BookGenTheme {
                WorkspaceScreen(
                    state = WorkspaceUiState(
                        isLoading = false,
                        detail = detail,
                        incompleteSections = listOf(
                            IncompleteSection(
                                bookId = "book-1",
                                chapterId = "ch-1",
                                chapterNumber = 1,
                                chapterTitle = "設計の基盤",
                                sectionId = "sec-1",
                                sectionNumber = 1,
                                sectionTitle = "UniFFI の導入",
                                keyFocus = "境界設計",
                                status = SectionStatus.PENDING,
                                errorMessage = null,
                            ),
                        ),
                        expandedChapterIds = setOf("ch-1"),
                        isGenerating = true,
                    ),
                    aiSettings = AiSettings(),
                    onBack = {},
                    onOpenReader = {},
                    onToggleChapter = {},
                    onToggleStreamDrawer = {},
                    onMoveChapterUp = {},
                    onMoveChapterDown = {},
                    onMoveSectionUp = { _, _ -> },
                    onMoveSectionDown = { _, _ -> },
                    onAddChapter = { _, _ -> },
                    onEditChapterMeta = { _, _, _ -> },
                    onAddSection = { _, _, _ -> },
                    onDeleteSection = {},
                    onEditSectionClick = {},
                    onDismissSectionEditor = {},
                    onSaveSection = { _, _, _, _ -> },
                    onRestoreRevision = { _, _ -> },
                    onRegenerateSection = {},
                    onRegenerateChapter = { chapterRegenOpened = true },
                    onDismissRegenSheet = {},
                    onConfirmRegen = { _, _ -> },
                    onStartOrResumePipeline = {},
                    onPausePipeline = { paused = true },
                    onOpenApiSettings = {},
                    onDismissApiSettings = {},
                    onSaveAiSettings = {},
                    onClearAiKeys = {},
                    onDismissSnackbar = {},
                )
            }
        }

        composeRule.onNodeWithText("一時停止").performClick()
        assertTrue(paused)

        composeRule.onNodeWithContentDescription("この章を再生成・トーン調整").performClick()
        assertTrue(chapterRegenOpened)
    }

    @Test
    fun readerScreenRendersAstBlocksAndOpensExportSheet() {
        var exportStarted = false
        val detail = sampleDetail()

        composeRule.setContent {
            BookGenTheme(preference = ThemePreference.SEPIA) {
                ReaderScreen(
                    state = ReaderUiState(
                        isLoading = false,
                        detail = detail,
                        currentChapterIndex = 0,
                        parsedSections = listOf(
                            ParsedSectionBlocks(
                                sectionId = "sec-1",
                                title = "UniFFI の導入",
                                chapterNumber = 1,
                                sectionNumber = 1,
                                charCount = 42,
                                isCompleted = true,
                                blocks = listOf(
                                    DocBlock.Heading(level = 2, text = "安全な FFI 設計"),
                                    DocBlock.Paragraph("Rust と Kotlin を安全に連携します。"),
                                    DocBlock.CodeBlock(language = "rust", code = "pub fn ok() -> u32 { 42 }"),
                                ),
                            ),
                        ),
                        appearance = ReaderAppearance(
                            theme = ThemePreference.SEPIA,
                            fontChoice = ReaderFontChoice.SERIF,
                        ),
                    ),
                    onBack = {},
                    onOpenWorkspace = {},
                    onSelectChapter = {},
                    onScrollFractionChanged = {},
                    onToggleChrome = {},
                    onThemeSelected = {},
                    onFontSelected = {},
                    onFontSizeChanged = {},
                    onLineHeightChanged = {},
                    onPaddingChanged = {},
                    onSelectExportFormat = {},
                    onToggleIncludeToc = {},
                    onToggleIncludeIncomplete = {},
                    onStartExport = { exportStarted = true },
                    onCancelExport = {},
                    onShareExportedFile = null,
                    onSaveExportedToDevice = null,
                    onDismissSnackbar = {},
                )
            }
        }

        composeRule.onNodeWithText("安全な FFI 設計").assertIsDisplayed()
        composeRule.onNodeWithText("pub fn ok() -> u32 { 42 }").assertIsDisplayed()
        composeRule.onNodeWithText("書出し").performClick()
        composeRule.onNodeWithText("EPUB を生成").performClick()
        assertTrue(exportStarted)
    }

    private fun sampleSummary() = BookSummary(
        id = "book-1",
        title = "Kotlin と Rust 実践設計",
        themePrompt = "Android アプリ高速化",
        genreKey = "tech",
        genreName = "IT技術",
        targetAudience = "モバイル開発者",
        tone = "論理的",
        targetCharCount = 30_000L,
        overview = "概要",
        status = BookStatus.PARTIAL,
        totalChapters = 1,
        totalSections = 1,
        completedSections = 0,
        emptyOrFailedSections = 1,
        wordCount = 10,
        charCount = 42,
        lastReadChapterId = null,
        lastReadSectionId = null,
        lastReadScrollOffset = 0.0,
        createdAt = "",
        updatedAt = "",
    )

    private fun sampleDetail(): BookDetail {
        val summary = sampleSummary()
        return BookDetail(
            summary = summary,
            chapters = listOf(
                ChapterWithSections(
                    chapter = ChapterRecord("ch-1", "book-1", 1, 0, "設計の基盤", "UniFFI"),
                    sections = listOf(
                        SectionRecord(
                            id = "sec-1",
                            chapterId = "ch-1",
                            bookId = "book-1",
                            chapterNumber = 1,
                            sectionNumber = 1,
                            position = 0,
                            title = "UniFFI の導入",
                            keyFocus = "境界設計",
                            format = MarkupFormat.MARKDOWN,
                            content = "## 安全な FFI 設計",
                            status = SectionStatus.PENDING,
                            errorMessage = null,
                            wordCount = 10,
                            charCount = 42,
                            revisionCount = 0,
                            updatedAt = "",
                        ),
                    ),
                ),
            ),
        )
    }
}
