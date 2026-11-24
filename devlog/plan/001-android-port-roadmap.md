# PLAN-001: BookGen Android (Kotlin + Rust/UniFFI) 移植ロードマップ

Status: In Progress

Release: Unreleased

Related: [ADR-001](../adr/001-rust-uniffi-core-architecture.md), [ADR-002](../adr/002-byok-llm-client-and-resumable-pipeline.md), [DIG-001](../dig/001-legacy-bookgen-forensic-audit.md), [GAP-001](../gap/001-penpot-mcp-not-connected.md)

## Goal

既存の PHP + 単一HTML Web アプリ `bookgen/`（Co-Author Studio）を解析・刷新し、**Kotlin (Jetpack Compose / Material 3 Expressive / MVVM)** ＋ **Rust (`rust-core` via Mozilla UniFFI)** による高速・オフライン対応・途中再開可能な Android ネイティブ書籍執筆・読書アプリ `bookgen-android/` を構築する。

## Phase 1: 既存Web解析・改善提案・仕様策定・デザイン設計

Status: Complete (Phase 1 承認ゲート確認待ち)

- **やったこと**:
  - `bookgen/` の全ソース（`index.html`, `api.php`, `genres.php`, `config_example.php`, `config.php` メタ情報, `.htaccess`）および `books/app_data.db`, `books/*/book.json`, `books/*/chapter_*.{md,adoc}`, `genre_embeddings.json` を解析し、[DIG-001](../dig/001-legacy-bookgen-forensic-audit.md) に記録。
  - [spec.md](../../spec.md) を作成し、Web版の欠陥（節欠損・空文字保存・`update_chapter` による節粒度破壊・`genres.php` ガイド未活用・XSS・CDN依存）と具体的な改善提案、Mermaid構成図・画面遷移図、Kotlin/Rust責務分担を確定。
  - Penpot MCP 接続確認後（[GAP-001](../gap/001-penpot-mcp-not-connected.md) 解決）、実ファイル `新しいファイル 1` (`fileId: 29883df9-fd36-8190-8008-feabca1cbdab`, `pageId: 29883df9-fd36-8190-8008-feabca1cfe0a`) 上にトークン 5 セット（`primitives` 72件, `semantic` 28件, `modes/light` 23件, `modes/dark` 23件, `modes/sepia` 23件）を構築し、4 画面（`01-Library`, `02-Wizard`, `03-OutlineWorkspace`, `04-ReaderAndExport`）を作成。
- **詰まった点と解決策**:
  - 初回実行時の Penpot MCP 未ロード問題は再ロード後に解消（[GAP-001](../gap/001-penpot-mcp-not-connected.md)）。
  - 構築中のビジュアル目視チェックにより、`02-Wizard` の行コンテナ高さ崩壊・`03-OutlineWorkspace` 第4章見出しの縦クリップ・`04-ReaderAndExport` ヘッダー横あふれと本文テキスト重なり・4px グリッド外ギャップ（2/3/6/10/14px）を検出。`vSize: "fix"`＋明示的テキスト枠高さ＋`waitForLayoutUpdate()`＋4pxスナップで全修正し、全4画面で自動品質・Anti-Slop監査（`noLayout=0, rawFills=0, offGrid=0, defaultFonts=0`）に合格。
- **次のステップ**:
  - ユーザーによる Penpot デザイン確認・Phase 1 承認を得た後、Phase 2（`bookgen-android` リポジトリへの GitHub Issue 起票、Android + Rust/UniFFI プロジェクト雛形構築）へ進む。

## Phase 2: プロジェクト基盤構築と GitHub Issue 起票

Status: Done (PR #1 — Issue #1)
- GitHub Repository `ipogy/bookgen-android` created and initial Phase 1 baseline pushed to `main`.
- Issues `#1`–`#8` opened with concrete acceptance criteria covering Phases 2–5.
- Branch `feat/1-scaffold-android-rust-uniffi` scaffolded `rust-core/` (`bookgen-core` + `uniffi-bindgen` v0.32.2) and `app/` (AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01, Nav3 1.0.1, Work 2.12.0), passing `cargo test`, `./gradlew testDebugUnitTest assembleDebug`, `scripts/verify_native_libs.py` (16 KiB `arm64-v8a` / `x86_64` ELF check), and `zipalign -c -P 16`.

## Phase 3: Rust コアロジック層の実装

Status: Done (PRs #10, #11, #12 — Issues #2–#4)
- Issue #2 (`feat/2-rust-sqlite-fts5-importer`): Bundled SQLite WAL + FTS5 trigram store, per-section edits & revision rollback, incomplete/empty section tracking, and idempotent legacy `app_data.db`/`.adoc`/`.md` importer ([PR devlog](../pr/002-issue2-rust-sqlite-fts5-importer.md)).
- Issue #3 (`feat/3-rust-parser-genre-exporter`): AsciiDoc/Markdown AST parser & wrapper sanitizer, bundled 12-genre metadata & 3072-dim cosine classifier, and streaming/cancellable EPUB 3 / PDF / Markdown / AsciiDoc exporters ([PR devlog](../pr/003-issue3-rust-parser-genre-exporter.md)).
- Issue #4 (`test/4-rust-core-unit-tests`): Exhaustive unit & integration test suite across SQLite WAL/FTS5, AST edge cases, determinism, mid-export cancel/listener errors, lock poison recovery, and `guard_ffi` unwind safety ([PR devlog](../pr/004-issue4-rust-unit-tests-and-panic-safety.md)).

## Phase 4: Kotlin (Jetpack Compose) UI と AI生成エンジンの実装

Status: Not Started

## Phase 5: 結合テスト・最適化・仕上げ

Status: Not Started

## Findings

- Web版 `app_data.db` の実データ検証により、7冊中4冊で節欠損・3件で0バイト空節・1件で節番号飛び（71〜74）を確認。Android版では節ステータス管理・空出力検証・指数バックオフリトライ・WorkManagerによる途中再開が必須要件となる（[DIG-001](../dig/001-legacy-bookgen-forensic-audit.md)）。
