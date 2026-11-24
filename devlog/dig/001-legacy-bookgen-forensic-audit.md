# DIG-001: 既存 Web 版 `bookgen/` フォレンジック監査

Status: Closed

Release: Unreleased

Related: [PLAN-001](../plan/001-android-port-roadmap.md), [ADR-001](../adr/001-rust-uniffi-core-architecture.md), [ADR-002](../adr/002-byok-llm-client-and-resumable-pipeline.md)

## Question

既存 Web アプリ `bookgen/` の画面構成・API通信仕様・データ保存方式の実態はどうなっており、どの部分に構造的なバグ・UX劣化・ボトルネックが潜んでいるか？

## Approach

1. `bookgen/index.html`, `bookgen/api.php`, `bookgen/genres.php`, `bookgen/config_example.php`, `bookgen/.htaccess` を通読し、全エンドポイント・状態遷移・プロンプト構造を抽出した。
2. `bookgen/config.php` のシークレットをマスクしつつ使用モデルとプロバイダ定義を確認した。
3. Python `sqlite3` で `bookgen/books/app_data.db` のスキーマ・行数・節文字数・欠損状況を全数集計し、`books/*/` の JSON/Markdown/AsciiDoc ファイルおよび `genre_embeddings.json` の次元数を確認した。

## Findings

### 1. 実データ (`app_data.db`) から判明した生成パイプラインの欠陥
- `books` テーブル 7冊中 **4冊で計画節数に対して実際の `chapter_sections` 行数が不足**していた：
  - `f4080426b00c8c32`: 計画28節 / 保存25節（3節欠損）
  - `20cc3d00be55102e`: 計画32節 / 保存29節（3節欠損）
  - `5d2ec412c2506f3b`: 計画28節 / 保存22節（6節欠損）
  - `b904defea506a36f`: 計画28節 / 保存25節（3節欠損）
- `length(trim(content)) == 0` の**空コンテンツ節が3件保存**されていた（`3a0ee74c8ae31334` ch7 sec71, `20cc3d00be55102e` ch3 sec4, `3aab499528c77238` ch6 sec1）。原因は `api.php` の `callOpenRouter()` が空応答時に `""` を返し、`write_section` が空検証なしに UPSERT しているため。
- `3a0ee74c8ae31334` 第7章では、Planner LLM が `section_number: 71..74`（章番号×10＋節番号）を返却し、そのまま DB 主キーとして保存されていた。

### 2. コード上のバグ・設計上の弱点
- **節粒度の破壊 (`api.php:439-459`)**: `get_book` は `chapter_sections` を連結して返すが、手動編集の `update_chapter` はその章の全 `chapter_sections` を `DELETE` し、`section_number = 1` の1行に上書き統合してしまう。
- **ジャンル指針の死蔵 (`api.php:503-509, 600-625` vs `genres.php`)**: `genres.php` には `editor_guide`, `element_label`, `author_style` が詳細定義されているが、`api.php` は `plan` で `name` のみを使い、`write_section` では `$genres` 自体を読み込んでいない。
- **汎用フェンス除去の副作用 (`api.php:300`)**: `callUnifiedLLM()` が JSON と本文の両方に対し先頭・末尾の ```` ``` ```` を一律 strip している。
- **クライアント完結型オーケストレーション (`index.html:2294-2359`)**: ブラウザ側で章直列＋節 `Promise.all` を回しているため、タブ終了・バックグラウンド化・一時的ネットワークエラーで全体が中断し、再開UIもリトライも存在しない。
- **ユーザー隔離欠如 (`api.php:393-473`)**: `books` テーブルに所有者カラムがなく、認証済み全ユーザーが他人の本を参照・削除可能。
- **XSS (`index.html:1794-1805, 1940-2040`)**: 書籍タイトル・概要・章タイトルをエスケープせず `innerHTML` に挿入している。
- **オフライン非対応と出力制約**: CDN版 Asciidoctor.js / KaTeX / Google Fonts 必須。出力は `.adoc` 単一ダウンロードのみで EPUB / PDF / Markdown や全文検索がない。
- **レガシー資産の存在**: `books/*/` には第1世代（`.md` + `author_name`/`key_topics`）4冊と第2世代（`.adoc` + `focus_area`/`sections`）4冊が残り、DB内旧 `chapters` テーブルにも10冊・70行が存在する。`genre_embeddings.json` は12ジャンル×3072次元ベクトルを保持している。

## Sources

- `bookgen/api.php` (lines 1-649)
- `bookgen/index.html` (lines 1-2486)
- `bookgen/genres.php` (lines 1-135)
- `bookgen/config_example.php` / `bookgen/config.php` (keys redacted)
- `bookgen/books/app_data.db` (SQLite schema & table rows inspected via Python `sqlite3`)
- `bookgen/books/genre_embeddings.json` & `bookgen/books/*/book.json`
