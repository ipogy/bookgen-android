# Changelog

All notable changes to `bookgen-android` will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2026-11-25

### Fixed
- **Markup Parser & Legacy Format Recovery (`bookgen-core`)**: Improved `detect_markup_format_for_text` and unified `importer.rs` format classification to recognize headless AsciiDoc sections (`[stem]`, `++++`, `stem:[...]`, `[stem]+...+`, `|===`, `[cols=]`, `EXAMPLE:`, `TIP:`). Fixed `[stem]` / `[latexmath]` block splitting across inline paragraph boundaries, added Markdown `\[ ... \]` and indented/same-line `$$ ... $$` math parsing, added `.Title` block caption extraction, and fixed multi-line / blank-line-separated AsciiDoc `|===` table cell grouping. Also added `resolve_section_format` in `ReaderViewModel` and EPUB/PDF/Text exporters so previously imported sections auto-recover.
- **Native Math Renderer & Inline Rich Text Formatting (`Compose`)**: Added `InlineRichTextFormatter` to render AsciiDoc `*bold*` (including Japanese unconstrained boundaries like `*デュレーション分析*` and `後の*アラート閾値設定*`), Markdown `**bold**` / `_italic_`, inline code, and inline math macros (`[stem]+...+`, `[stem:[...]]`, `stem:[...]`, `\( ... \)`, guarded `$...$`). Added `NativeMathBlockView` recursive LaTeX AST renderer supporting 2D stacked `\frac`, summation/limit operators, `\sqrt`, `\begin{cases}`, Unicode math symbols, `BaselineShift` sub/superscripts, and hybrid prose/math `[stem]` blocks.

## [0.1.0] - 2026-11-25

### Added
- **Project Scaffold & UniFFI Pipeline (PR #9, Issue #1)**: Configured Gradle (`Kotlin 2.3.20`, Compose BOM `2026.03.01`, Navigation 3 `1.0.1`, WorkManager `2.12.0`), Rust `bookgen-core` workspace with Mozilla UniFFI `0.32.2`, `cargo-ndk` multi-ABI builds (`arm64-v8a`, `armeabi-v7a`, `x86_64`) with 16 KiB ELF `PT_LOAD` alignment, and `scripts/verify_native_libs.py`.
- **SQLite WAL, FTS5 Search & Legacy Importer (PR #10, Issue #2)**: Implemented `BookStore` in Rust with WAL persistence, trigram FTS5 + short-CJK `LIKE` fallback search, per-section revision history/rollback, incomplete section tracking, and idempotent migration of legacy `app_data.db` / `.adoc` / `.md` books.
- **Parser, Genre Classifier & Offline Exporters (PR #11, Issue #3)**: Bundled 12-genre catalog and `3072`-dim float vectors, Markdown/AsciiDoc AST block parser & wrapper sanitizer, and cancellable `BookExporter` supporting EPUB 3, PDF 1.4 (`WinAnsi` + `UniJIS-UTF16-H`), Markdown, and AsciiDoc.
- **Rust Panic Safety & Unit/Integration Suite (PR #12, Issue #4)**: Verified `guard_ffi` unwind boundaries (`panic = "unwind"` on `android-jni`), lock poison recovery, mid-export cancellation cleanup, and FTS5 edge cases (22 Rust unit/integration tests).
- **Material 3 Theme, Clean Architecture & 4 Core Screens (PR #13, Issue #5)**: Extracted `design/DESIGN.md`, built Light/Dark/Sepia color/shape/typography themes, `RustBookRepository`, native AST block renderer, Navigation 3 host, and `Library`, `Wizard`, `Workspace`, and `Reader` screens.
- **BYOK Vault, Gemini/OpenRouter SSE & WorkManager Pipeline (PR #14, Issue #6)**: Implemented `AndroidKeyStore` AES-256-GCM encryption (`KeystoreSecretCipher`), Gemini (`embedContent`, `generateContent`, `streamGenerateContent?alt=sse`) & OpenRouter (`chat/completions`) clients, jittered exponential backoff + `Retry-After`, JSON outline repair, `BookPromptBuilder`, and `BookGenerationWorker` foreground service with pause/resume/deep-linking.
- **Partial Regeneration, Reader Customization & Sharing (PR #15, Issue #7)**: Added chapter/section `RegenerateBottomSheet` with tone presets, persistent `ThemePreference` & `ReaderAppearance`, normalized `[0.0, 1.0]` scroll persistence, `FileProvider` share sheet, SAF device save, and Android 11+ SAF legacy import staging.
- **Phase 5 Hardening, Audit & Documentation (Issue #8)**: Enforced explicit `uniffiEnsureInitialized()` at repository startup, moved `WavyProgressTrack` to `drawWithCache` + `Path.rewind()` in Draw phase with conditional infinite transition, added `contentType` across all `LazyColumn` items, eliminated `Pair` list allocations in `ReaderScreen`, added `WizardViewModelTest` and Compose UI test suite (`BookGenComposeUiTest`), and published `README.md` and `CHANGELOG.md`.
