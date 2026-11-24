package com.bookgen.app.domain.service

import com.bookgen.app.domain.model.BookDetail
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.GenreInfo
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.SectionRecord

data class PromptPair(
    val systemInstruction: String,
    val userPrompt: String,
)

object BookPromptBuilder {

    fun buildOutlinePrompts(
        theme: String,
        targetAudience: String,
        tone: String,
        chapterCount: Int,
        sectionsPerChapter: Int,
        genre: GenreInfo?,
    ): PromptPair {
        val genreName = genre?.name ?: "専門書"
        val guideBlock = if (genre != null) {
            """
            【ジャンル企画指針: $genreName】
            - 編集方針: ${genre.editorGuide}
            - 各節に必ず盛り込む要素: ${genre.elementLabel}
            - 文体・表現基準: ${genre.authorStyle}
            """.trimIndent()
        } else {
            ""
        }

        val systemInstruction = """
            あなたは商業出版の統括編集長です。
            ジャンル【$genreName】に基づき、全${chapterCount}章構成（各章${sectionsPerChapter}節前後）の書籍企画を策定してください。
            前置きや挨拶、マークダウンコードブロック（```）は出力禁止です。
            最初の文字は必ず { 、最後の文字は必ず } にし、有効なJSONオブジェクトのみを出力してください。
            $guideBlock
        """.trimIndent()

        val userPrompt = """
            以下のテーマで専門書籍の企画・目次構成を立案してください。

            テーマ: $theme
            想定読者: ${targetAudience.ifBlank { "中級実務者" }}
            文体トーン: $tone

            【出力必須フォーマット】
            {
              "book_title": "書籍タイトル",
              "target_audience": "想定読者層",
              "overview": "書籍全体の狙いと概要（200〜400字）",
              "chapters": [
                {
                  "chapter_number": 1,
                  "title": "第1章タイトル",
                  "focus_area": "この章で扱う専門領域",
                  "sections": [
                    {"section_number": 1, "title": "第1節タイトル", "key_focus": "必ず扱う具体的解説事項"}
                  ]
                }
              ]
            }

            ※章数は${chapterCount}章、各章に${sectionsPerChapter}節を含めてください。JSON以外のテキストは絶対に出力しないでください。
        """.trimIndent()

        return PromptPair(systemInstruction = systemInstruction, userPrompt = userPrompt)
    }

    fun buildSectionPrompts(
        detail: BookDetail,
        chapter: ChapterWithSections,
        section: SectionRecord,
        genre: GenreInfo?,
        previousSection: SectionRecord?,
        nextSection: SectionRecord?,
        format: MarkupFormat,
        targetSectionChars: Int,
        customInstruction: String? = null,
    ): PromptPair {
        val summary = detail.summary
        val formatRules = when (format) {
            MarkupFormat.MARKDOWN -> """
                【Markdown構文】
                - 節見出し（第${section.sectionNumber}節）は `## ${section.title}` で開始し、小見出しは `### `
                - 箇条書きは `- `、手順は `1. `、コードは ```lang ... ```
                - テーブルは GFM パイプテーブル、重要な補足は `> NOTE:` を使用
                - 数式は `$$ ... $$` を使用
                - 全体を外側の ```markdown フェンスで囲まないこと
            """.trimIndent()

            MarkupFormat.ASCIIDOC -> """
                【AsciiDoc構文】
                - 節見出し（第${section.sectionNumber}節）は `== ${section.title}` で開始し、小見出しは `=== `
                - 太字は `*太字*`（前後半角スペース）、コードは `[source,lang]` と `----`
                - 箇条書きは空行後に `* `、テーブルは `|===`、数式は `[stem]` と `++++` で囲む
                - Markdown記法（`##` や ```）は混在させないこと
            """.trimIndent()
        }

        val genreGuidance = if (genre != null) {
            """
            【ジャンル執筆基準: ${genre.name}】
            - 編集ガイド: ${genre.editorGuide}
            - 必須構成要素: ${genre.elementLabel}
            - 推奨文体: ${genre.authorStyle}
            """.trimIndent()
        } else {
            ""
        }

        val prevContext = if (previousSection != null) {
            val tailExcerpt = previousSection.content.trim().takeLast(320)
            buildString {
                append("前節: 第${previousSection.sectionNumber}節「${previousSection.title}」\n")
                if (tailExcerpt.isNotEmpty()) {
                    append("前節末尾の文脈（重複説明を避け自然に接続すること）:\n\"\"\"\n$tailExcerpt\n\"\"\"")
                }
            }
        } else {
            "前節: なし（この章の導入節です）"
        }

        val nextContext = if (nextSection != null) {
            "次節予定: 第${nextSection.sectionNumber}節「${nextSection.title}」（領域: ${nextSection.keyFocus}）— この話題に踏み込みすぎず次節へ橋渡ししてください。"
        } else {
            "次節予定: 章末の節です。本章の重要論点を整理して締めくくってください。"
        }

        val customBlock = if (!customInstruction.isNullOrBlank()) {
            "\n【再生成・トーン調整指示（最優先）】\n$customInstruction\n"
        } else {
            ""
        }

        val systemInstruction = """
            あなたは書籍『${summary.title}』の専門執筆エージェントです。
            【絶対禁止】自己紹介・挨拶・メタコメント（例:「以下に執筆します」）・前置きは禁止。本文のみ出力してください。
            $genreGuidance
            $formatRules
            - 目標分量は約${targetSectionChars.coerceIn(700, 4000)}字。
            - 今回担当する【第${chapter.chapter.chapterNumber}章 第${section.sectionNumber}節】の内容のみを深く具体的に執筆してください。
        """.trimIndent()

        val userPrompt = """
            書籍概要: ${summary.overview.ifBlank { summary.themePrompt }}
            想定読者: ${summary.targetAudience}
            文体トーン: ${summary.tone}

            担当章: 第${chapter.chapter.chapterNumber}章「${chapter.chapter.title}」（焦点: ${chapter.chapter.focusArea}）
            担当節: 第${section.sectionNumber}節「${section.title}」
            重点解説事項: ${section.keyFocus.ifBlank { section.title }}

            $prevContext
            $nextContext
            $customBlock
            上記条件に従って第${section.sectionNumber}節の本編のみを執筆してください。
        """.trimIndent()

        return PromptPair(systemInstruction = systemInstruction, userPrompt = userPrompt)
    }
}
