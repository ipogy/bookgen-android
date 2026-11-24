package com.bookgen.app.data.remote

import com.bookgen.app.domain.model.ChapterPlanDraft
import com.bookgen.app.domain.model.SectionPlanDraft
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlannedBookDraft(
    @SerialName("book_title") val bookTitle: String = "",
    @SerialName("target_audience") val targetAudience: String = "",
    val overview: String = "",
    val chapters: List<PlannedChapterJson> = emptyList(),
)

@Serializable
data class PlannedChapterJson(
    @SerialName("chapter_number") val chapterNumber: Int = 1,
    val title: String = "",
    @SerialName("focus_area") val focusArea: String = "",
    val sections: List<PlannedSectionJson> = emptyList(),
)

@Serializable
data class PlannedSectionJson(
    @SerialName("section_number") val sectionNumber: Int = 1,
    val title: String = "",
    @SerialName("key_focus") val keyFocus: String = "",
)

object LlmJsonRepair {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val thinkBlockRegex = Regex("(?is)<think>.*?</think>")
    private val trailingCommaRegex = Regex(",\\s*([}\\]])")

    fun parseOutlineJson(rawResponse: String): PlannedBookDraft {
        val candidate = extractBalancedJsonObject(rawResponse)
            ?: throw EmptyLlmResponseException("構成案 JSON オブジェクトを抽出できませんでした")

        val repaired = repairTrailingCommas(candidate)
        val parsed = runCatching { json.decodeFromString<PlannedBookDraft>(repaired) }
            .getOrElse { ex ->
                throw IllegalStateException("構成案 JSON のパースに失敗しました: ${ex.message}", ex)
            }

        if (parsed.chapters.isEmpty()) {
            throw EmptyLlmResponseException("生成された構成案に章が含まれていません")
        }
        val validChapters = parsed.chapters.filter { ch ->
            ch.title.isNotBlank() && ch.sections.isNotEmpty()
        }
        if (validChapters.isEmpty()) {
            throw EmptyLlmResponseException("有効な章・節を持つ構成案を生成できませんでした")
        }
        return parsed.copy(chapters = validChapters)
    }

    fun toChapterPlanDrafts(draft: PlannedBookDraft): List<ChapterPlanDraft> {
        return draft.chapters.mapIndexed { chIdx, ch ->
            ChapterPlanDraft(
                chapterNumber = if (ch.chapterNumber > 0) ch.chapterNumber else chIdx + 1,
                title = ch.title.trim().ifEmpty { "第${chIdx + 1}章" },
                focusArea = ch.focusArea.trim(),
                sections = ch.sections.mapIndexed { secIdx, sec ->
                    SectionPlanDraft(
                        sectionNumber = if (sec.sectionNumber > 0) sec.sectionNumber else secIdx + 1,
                        title = sec.title.trim().ifEmpty { "第${secIdx + 1}節" },
                        keyFocus = sec.keyFocus.trim(),
                    )
                },
            )
        }
    }

    internal fun extractBalancedJsonObject(raw: String): String? {
        val cleaned = thinkBlockRegex.replace(raw, "").trim()
        val firstBrace = cleaned.indexOf('{')
        if (firstBrace < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in firstBrace until cleaned.length) {
            val ch = cleaned[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') {
                    depth += 1
                } else if (ch == '}') {
                    depth -= 1
                    if (depth == 0) {
                        return cleaned.substring(firstBrace, i + 1)
                    }
                }
            }
        }
        val lastBrace = cleaned.lastIndexOf('}')
        return if (lastBrace > firstBrace) cleaned.substring(firstBrace, lastBrace + 1) else null
    }

    internal fun repairTrailingCommas(jsonText: String): String =
        trailingCommaRegex.replace(jsonText, "$1")
}
