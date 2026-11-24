package com.bookgen.app.ui.screens.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookgen.app.data.pipeline.GenerationPipeline
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.ChapterPlanDraft
import com.bookgen.app.domain.model.GenreInfo
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.NewBookRequest
import com.bookgen.app.domain.model.SectionPlanDraft
import com.bookgen.app.domain.repository.BookRepository
import com.bookgen.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StructureTemplate(
    val label: String,
    val chapters: Int,
    val sectionsPerChapter: Int,
) {
    STANDARD("標準専門書 6章", 6, 4),
    FOCUSED("短期集中 4章", 4, 3),
    EXAM("試験対策 8章", 8, 4),
}

data class WizardUiState(
    val themePrompt: String = "",
    val bookTitle: String = "",
    val targetAudience: String = "実務エンジニア・中級者",
    val autoDetectGenre: Boolean = true,
    val genres: List<GenreInfo> = emptyList(),
    val selectedGenre: GenreInfo? = null,
    val template: StructureTemplate = StructureTemplate.STANDARD,
    val chapterCount: Int = 6,
    val sectionsPerChapter: Int = 4,
    val targetCharCount: Long = 36000L,
    val markupFormat: MarkupFormat = MarkupFormat.MARKDOWN,
    val tone: String = "厳密・論理",
    val useAiOutline: Boolean = true,
    val hasPlannerKey: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class WizardViewModel(
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val generationPipeline: GenerationPipeline,
) : ViewModel() {

    val aiSettings: StateFlow<AiSettings> = settingsRepository.aiSettings

    private val _uiState = MutableStateFlow(
        WizardUiState(
            hasPlannerKey = settingsRepository.aiSettings.value.let {
                it.hasKeyForModel(it.plannerModel)
            },
        ),
    )
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    private var classifyJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repository.listGenres() }
                .onSuccess { list ->
                    _uiState.update { cur ->
                        cur.copy(
                            genres = list,
                            selectedGenre = cur.selectedGenre ?: list.firstOrNull(),
                        )
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.aiSettings.collect { s ->
                _uiState.update { it.copy(hasPlannerKey = s.hasKeyForModel(s.plannerModel)) }
            }
        }
    }

    fun onThemeChanged(text: String) {
        _uiState.update { it.copy(themePrompt = text, errorMessage = null) }
        if (!_uiState.value.autoDetectGenre || text.isBlank()) return
        classifyJob?.cancel()
        classifyJob = viewModelScope.launch {
            delay(200)
            runCatching { repository.classifyThemeByKeywords(text) }
                .onSuccess { genre ->
                    _uiState.update { it.copy(selectedGenre = genre) }
                }
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.update { it.copy(bookTitle = title) }
    }

    fun onAudienceChanged(audience: String) {
        _uiState.update { it.copy(targetAudience = audience) }
    }

    fun onToggleAutoDetect(auto: Boolean) {
        _uiState.update { it.copy(autoDetectGenre = auto) }
        if (auto && _uiState.value.themePrompt.isNotBlank()) {
            onThemeChanged(_uiState.value.themePrompt)
        }
    }

    fun onSelectGenre(genre: GenreInfo) {
        _uiState.update { it.copy(selectedGenre = genre, autoDetectGenre = false) }
    }

    fun onSelectTemplate(template: StructureTemplate) {
        val totalChars = (template.chapters * template.sectionsPerChapter * 1500L)
        _uiState.update {
            it.copy(
                template = template,
                chapterCount = template.chapters,
                sectionsPerChapter = template.sectionsPerChapter,
                targetCharCount = totalChars,
            )
        }
    }

    fun onChapterCountChanged(count: Int) {
        val clamped = count.coerceIn(2, 12)
        _uiState.update {
            it.copy(
                chapterCount = clamped,
                targetCharCount = clamped * it.sectionsPerChapter * 1500L,
            )
        }
    }

    fun onSectionsPerChapterChanged(count: Int) {
        val clamped = count.coerceIn(2, 6)
        _uiState.update {
            it.copy(
                sectionsPerChapter = clamped,
                targetCharCount = it.chapterCount * clamped * 1500L,
            )
        }
    }

    fun onFormatSelected(format: MarkupFormat) {
        _uiState.update { it.copy(markupFormat = format) }
    }

    fun onToneSelected(tone: String) {
        _uiState.update { it.copy(tone = tone) }
    }

    fun onToggleUseAiOutline(useAi: Boolean) {
        _uiState.update { it.copy(useAiOutline = useAi, errorMessage = null) }
    }

    fun saveAiSettings(settings: AiSettings) {
        viewModelScope.launch {
            settingsRepository.updateAiSettings(settings)
        }
    }

    fun clearAiKeys() {
        viewModelScope.launch {
            settingsRepository.clearApiKeys()
        }
    }

    fun createPlannedBook(onCreated: (String) -> Unit) {
        val cur = _uiState.value
        val theme = cur.themePrompt.trim()
        if (theme.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "書籍のテーマ・企画メモを入力してください") }
            return
        }

        val initialTitle = cur.bookTitle.trim()
        val fallbackGenre = cur.selectedGenre ?: cur.genres.firstOrNull()

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val aiPlan = if (cur.useAiOutline && cur.hasPlannerKey) {
                    generationPipeline.planOutlineWithAi(
                        theme = theme,
                        targetAudience = cur.targetAudience,
                        tone = cur.tone,
                        chapterCount = cur.chapterCount,
                        sectionsPerChapter = cur.sectionsPerChapter,
                        preferredGenre = cur.selectedGenre,
                        autoDetectGenre = cur.autoDetectGenre,
                    )
                } else {
                    null
                }

                val effectiveTitle = initialTitle.ifEmpty {
                    aiPlan?.draft?.bookTitle?.trim()?.ifEmpty { null } ?: theme.take(48)
                }
                val resolvedGenre = aiPlan?.genre ?: fallbackGenre
                val genreKey = resolvedGenre?.key ?: "tech"
                val genreName = resolvedGenre?.name ?: "IT技術・プログラミング・インフラ"
                val effectiveAudience = cur.targetAudience.trim().ifEmpty {
                    aiPlan?.draft?.targetAudience?.trim()?.ifEmpty { null } ?: "一般読者"
                }
                val overview = aiPlan?.draft?.overview?.trim()?.ifEmpty { null }
                    ?: "「${effectiveTitle}」— ${genreName}。${resolvedGenre?.elementLabel ?: ""}"

                val created = repository.createBook(
                    NewBookRequest(
                        title = effectiveTitle,
                        themePrompt = theme,
                        genreKey = genreKey,
                        genreName = genreName,
                        targetAudience = effectiveAudience,
                        tone = cur.tone,
                        targetCharCount = cur.targetCharCount,
                        overview = overview,
                    ),
                )
                settingsRepository.setPreferredMarkupFormat(created.id, cur.markupFormat)

                val outline = aiPlan?.chapters ?: buildStructuredSkeleton(
                    title = effectiveTitle,
                    chapters = cur.chapterCount,
                    sectionsPerChapter = cur.sectionsPerChapter,
                    elementLabel = resolvedGenre?.elementLabel ?: "具体例と実践手順",
                )
                repository.saveOutline(created.id, outline)
                _uiState.update { it.copy(isSubmitting = false) }
                onCreated(created.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = e.message ?: "書籍の作成に失敗しました",
                    )
                }
            }
        }
    }

    private fun buildStructuredSkeleton(
        title: String,
        chapters: Int,
        sectionsPerChapter: Int,
        elementLabel: String,
    ): List<ChapterPlanDraft> {
        return (1..chapters).map { chNum ->
            val chLabel = when (chNum) {
                1 -> "全体像と基礎概念"
                chapters -> "実践応用とまとめ"
                else -> "第${chNum}章: コアテーマの深掘り"
            }
            ChapterPlanDraft(
                chapterNumber = chNum,
                title = chLabel,
                focusArea = "${title}に関する章構成 ($elementLabel)",
                sections = (1..sectionsPerChapter).map { secNum ->
                    SectionPlanDraft(
                        sectionNumber = secNum,
                        title = "節 $chNum.$secNum: 要点解説と応用",
                        keyFocus = elementLabel,
                    )
                },
            )
        }
    }
}
