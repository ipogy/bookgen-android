package com.bookgen.app.data.pipeline

import com.bookgen.app.data.remote.LlmClient
import com.bookgen.app.data.remote.LlmJsonRepair
import com.bookgen.app.data.remote.PlannedBookDraft
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.ChapterPlanDraft
import com.bookgen.app.domain.model.GenreInfo
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.SectionRecord
import com.bookgen.app.domain.model.SectionStatus
import com.bookgen.app.domain.repository.BookRepository
import com.bookgen.app.domain.repository.SettingsRepository
import com.bookgen.app.domain.service.BookPromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class AiOutlineResult(
    val genre: GenreInfo,
    val draft: PlannedBookDraft,
    val chapters: List<ChapterPlanDraft>,
)

data class GenerationRunSummary(
    val totalTargets: Int,
    val succeeded: Int,
    val failed: Int,
    val firstErrorMessage: String? = null,
)

class GenerationPipeline(
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val llmClient: LlmClient,
    private val progressBus: GenerationProgressBus,
) {
    suspend fun planOutlineWithAi(
        theme: String,
        targetAudience: String,
        tone: String,
        chapterCount: Int,
        sectionsPerChapter: Int,
        preferredGenre: GenreInfo?,
        autoDetectGenre: Boolean,
    ): AiOutlineResult {
        val settings = settingsRepository.aiSettings.value
        ensurePlannerKey(settings)

        val genre = if (autoDetectGenre) {
            resolveGenre(theme, settings) ?: preferredGenre ?: repository.listGenres().first()
        } else {
            preferredGenre ?: resolveGenre(theme, settings) ?: repository.listGenres().first()
        }

        val prompts = BookPromptBuilder.buildOutlinePrompts(
            theme = theme,
            targetAudience = targetAudience,
            tone = tone,
            chapterCount = chapterCount,
            sectionsPerChapter = sectionsPerChapter,
            genre = genre,
        )

        val rawResponse = llmClient.generateText(
            model = settings.plannerModel,
            systemInstruction = prompts.systemInstruction,
            prompt = prompts.userPrompt,
            temperature = 0.4,
            settings = settings,
        )

        val parsed = LlmJsonRepair.parseOutlineJson(rawResponse)
        return AiOutlineResult(
            genre = genre,
            draft = parsed,
            chapters = LlmJsonRepair.toChapterPlanDrafts(parsed),
        )
    }

    suspend fun executeBookGeneration(
        bookId: String,
        targetSectionIds: Set<String>? = null,
        customInstruction: String? = null,
        markupFormatOverride: MarkupFormat? = null,
        onNotificationUpdate: suspend (completed: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): GenerationRunSummary {
        val settings = settingsRepository.aiSettings.value
        ensureWriterKey(settings)

        val initialDetail = repository.getBook(bookId)
        val genre = runCatching { repository.getGenre(initialDetail.summary.genreKey) }.getOrNull()

        val allSectionsOrdered = flattenSections(initialDetail)
        val targetIds = if (targetSectionIds != null) {
            targetSectionIds
        } else {
            repository.listIncompleteSections(bookId).map { it.sectionId }.toSet()
        }

        if (targetIds.isEmpty()) {
            progressBus.update(bookId) {
                it.copy(
                    isRunning = false,
                    completedCount = allSectionsOrdered.size,
                    totalCount = allSectionsOrdered.size,
                    activeSectionId = null,
                    activeLabel = "全${allSectionsOrdered.size}節 執筆済み",
                    lastError = null,
                )
            }
            return GenerationRunSummary(totalTargets = 0, succeeded = 0, failed = 0)
        }

        val jobs = allSectionsOrdered.mapIndexedNotNull { flatIndex, item ->
            if (item.section.id in targetIds) {
                val prev = allSectionsOrdered.getOrNull(flatIndex - 1)?.section
                val next = allSectionsOrdered.getOrNull(flatIndex + 1)?.section
                SectionJobContext(item, prev, next)
            } else {
                null
            }
        }

        val baseCompleted = allSectionsOrdered.count {
            it.section.status == SectionStatus.COMPLETED && it.section.id !in targetIds
        }
        val totalBookSections = allSectionsOrdered.size.coerceAtLeast(jobs.size)
        val totalTargets = jobs.size
        val finishedCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val succeededCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val failedCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val errorLock = Any()
        var firstError: String? = null

        val semaphore = Semaphore(settings.maxConcurrency.coerceIn(1, 4))
        val targetCharsPerSection = ((initialDetail.summary.targetCharCount.takeIf { it > 0 } ?: 36_000L) /
            totalBookSections.coerceAtLeast(1)).toInt().coerceIn(900, 3200)

        progressBus.update(bookId) {
            it.copy(
                isRunning = true,
                completedCount = baseCompleted,
                totalCount = totalBookSections,
                activeLabel = "執筆パイプライン開始 (残り${totalTargets}節 / 並列${settings.maxConcurrency})",
                livePreview = "",
                lastError = null,
            )
        }
        onNotificationUpdate(baseCompleted, totalBookSections, "執筆を開始しています…")

        try {
            coroutineScope {
                val deferreds = jobs.map { job ->
                    async {
                        semaphore.withPermit {
                            val sec = job.item.section
                            val ch = job.item.chapter
                            val label = "第${ch.chapter.chapterNumber}章 第${sec.sectionNumber}節「${sec.title}」"
                            val format = markupFormatOverride ?: sec.format

                            repository.setSectionStatus(sec.id, SectionStatus.GENERATING, null)
                            progressBus.update(bookId) {
                                it.copy(
                                    activeSectionId = sec.id,
                                    activeLabel = "${label}を執筆中…",
                                )
                            }
                            onNotificationUpdate(
                                baseCompleted + finishedCounter.get(),
                                totalBookSections,
                                label,
                            )

                            try {
                                // Re-read adjacent previous section from DB in case it just finished earlier in this run.
                                val latestPrev = job.previousSection?.let { prev ->
                                    runCatching { repository.getBook(bookId) }
                                        .getOrNull()
                                        ?.chapters
                                        ?.flatMap { it.sections }
                                        ?.firstOrNull { it.id == prev.id }
                                } ?: job.previousSection

                                val prompts = BookPromptBuilder.buildSectionPrompts(
                                    detail = initialDetail,
                                    chapter = ch,
                                    section = sec,
                                    genre = genre,
                                    previousSection = latestPrev,
                                    nextSection = job.nextSection,
                                    format = format,
                                    targetSectionChars = targetCharsPerSection,
                                    customInstruction = customInstruction,
                                )

                                val buffer = StringBuilder()
                                var lastUiFlushMs = 0L
                                llmClient.streamText(
                                    model = settings.writerModel,
                                    systemInstruction = prompts.systemInstruction,
                                    prompt = prompts.userPrompt,
                                    temperature = 0.7,
                                    settings = settings,
                                ).collect { delta ->
                                    buffer.append(delta)
                                    val now = System.currentTimeMillis()
                                    if (now - lastUiFlushMs >= 160L) {
                                        lastUiFlushMs = now
                                        val tail = buffer.toString().takeLast(600)
                                        progressBus.update(bookId) {
                                            it.copy(
                                                activeSectionId = sec.id,
                                                activeLabel = "$label (${buffer.length}字生成中)",
                                                livePreview = tail,
                                            )
                                        }
                                    }
                                }

                                val sanitized = repository.sanitizeOutput(buffer.toString(), format)
                                val nonWsCount = sanitized.count { !it.isWhitespace() }
                                if (nonWsCount < MIN_SECTION_NON_WS_CHARS) {
                                    throw IllegalStateException("生成本文が極端に短いため保存を拒否しました (${nonWsCount}字)")
                                }

                                repository.upsertSectionContent(
                                    sectionId = sec.id,
                                    content = sanitized,
                                    format = format,
                                    source = if (customInstruction.isNullOrBlank()) "ai_stream" else "ai_regen",
                                    note = customInstruction?.take(60) ?: "AI自動生成 (${settings.writerModel})",
                                )
                                succeededCounter.incrementAndGet()
                                val doneNow = finishedCounter.incrementAndGet()
                                progressBus.update(bookId) {
                                    it.copy(
                                        completedCount = baseCompleted + doneNow,
                                        livePreview = sanitized.takeLast(500),
                                        activeLabel = "$label 完了",
                                    )
                                }
                                onNotificationUpdate(
                                    baseCompleted + doneNow,
                                    totalBookSections,
                                    "$label 完了",
                                )
                            } catch (ce: CancellationException) {
                                // Restore non-completed generating section back to PENDING/EMPTY safely
                                runCatching {
                                    val fallbackStatus = if (sec.content.isBlank()) SectionStatus.EMPTY else SectionStatus.PENDING
                                    repository.setSectionStatus(sec.id, fallbackStatus, "ユーザー操作により一時停止")
                                }
                                throw ce
                            } catch (e: Exception) {
                                val errMsg = e.message?.take(240) ?: "節生成エラー"
                                runCatching {
                                    repository.setSectionStatus(sec.id, SectionStatus.FAILED, errMsg)
                                }
                                failedCounter.incrementAndGet()
                                finishedCounter.incrementAndGet()
                                synchronized(errorLock) {
                                    if (firstError == null) firstError = errMsg
                                }
                            }
                        }
                    }
                }
                deferreds.awaitAll()
            }
        } finally {
            val succeeded = succeededCounter.get()
            val failed = failedCounter.get()
            val errSnapshot = synchronized(errorLock) { firstError }
            val doneLabel = when {
                failed > 0 -> "一部失敗あり（成功 $succeeded / 失敗 $failed）"
                succeeded > 0 -> "執筆完了（${succeeded}節を保存）"
                else -> "一時停止しました"
            }
            progressBus.update(bookId) {
                it.copy(
                    isRunning = false,
                    activeSectionId = null,
                    activeLabel = doneLabel,
                    lastError = errSnapshot,
                )
            }
        }

        val finalErr = synchronized(errorLock) { firstError }
        return GenerationRunSummary(
            totalTargets = totalTargets,
            succeeded = succeededCounter.get(),
            failed = failedCounter.get(),
            firstErrorMessage = finalErr,
        )
    }

    private suspend fun resolveGenre(theme: String, settings: AiSettings): GenreInfo? {
        if (settings.hasGeminiKey()) {
            val match = runCatching {
                val vec = llmClient.embedText(settings.embeddingModel, theme, settings)
                repository.classifyEmbedding(vec).firstOrNull()?.genre
            }.getOrNull()
            if (match != null) return match
        }
        return runCatching { repository.classifyThemeByKeywords(theme) }.getOrNull()
    }

    private fun ensurePlannerKey(settings: AiSettings) {
        if (!settings.hasKeyForModel(settings.plannerModel)) {
            throw IllegalStateException("構成案生成に必要な API キーが未設定です。ライブラリ右上の設定からキーを登録してください。")
        }
    }

    private fun ensureWriterKey(settings: AiSettings) {
        if (!settings.hasKeyForModel(settings.writerModel)) {
            throw IllegalStateException("執筆生成に必要な API キーが未設定です。設定から Gemini または OpenRouter キーを登録してください。")
        }
    }

    private fun flattenSections(detail: BookDetail): List<FlatSectionRef> {
        val list = mutableListOf<FlatSectionRef>()
        for (ch in detail.chapters) {
            for (sec in ch.sections) {
                list += FlatSectionRef(chapter = ch, section = sec)
            }
        }
        return list
    }

    private data class FlatSectionRef(
        val chapter: com.bookgen.app.domain.model.ChapterWithSections,
        val section: SectionRecord,
    )

    private data class SectionJobContext(
        val item: FlatSectionRef,
        val previousSection: SectionRecord?,
        val nextSection: SectionRecord?,
    )

    private companion object {
        const val MIN_SECTION_NON_WS_CHARS = 40
    }
}
