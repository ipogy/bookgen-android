# PR: Implement Rust Markup Parser, Genre Classifier, and EPUB/PDF/MD/ADOC Exporters

Date: 2026-11-24
Issue: [#3](https://github.com/ipogy/bookgen-android/issues/3)
Branch: `feat/3-rust-parser-genre-exporter`

## What Was Built
- **`bookgen_core::markup` ([markup.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/markup.rs))**:
  - AST parser (`parse_markup_blocks`) for both Markdown and AsciiDoc producing `DocumentBlock` variants (`Heading`, `Paragraph`, `CodeBlock`, `Table`, `Admonition`, `MathBlock`, `BulletList`, `NumberedList`, `Quote`, `Divider`) with format detection (`detect_markup_format_for_text`) and cross-format renderers (`blocks_to_markdown`, `blocks_to_asciidoc`).
  - `sanitize_section_output`: strips only an outermost wrapper fence (`` ```markdown ``, `` ```asciidoc ``, etc.) when fence depth closes at the EOF line, fixing the legacy `api.php:300` bug where valid inner code blocks were stripped.
- **`bookgen_core::genres` ([genres.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/genres.rs))**:
  - Bundled all 12 Web genres (`genres_catalog.json`) with full `seed_text`, `editor_guide`, `element_label`, `author_style`, and IEEE-754 `f32` 3072-dim embeddings (`genre_embeddings.bin`, 147,456 bytes).
  - Exposed `list_genres`, `get_genre`, `classify_embedding` (`f64`-accumulated cosine similarity), and offline `classify_theme_keywords` fallback.
- **`bookgen_core::exporter` ([mod.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/exporter/mod.rs), [epub.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/exporter/epub.rs), [pdf.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/exporter/pdf.rs), [text.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/exporter/text.rs))**:
  - Built `BookExporter` with bounded `JobRegistry` (`MAX_ACTIVE_JOBS = 64`, `MAX_JOB_ID_BYTES = 128`, 30s startup reservation TTL, poison-safe `cancel_job`), foreign `ExportProgressListener` streaming `ProgressStage`, cooperative cancel checkpoints, and `PartialOutput` exclusive `.part` temp files removed cleanly on cancel/failure.
  - Implemented pure-Rust EPUB 3 ZIP assembly (`zip 8.6.0` with `zlib-rs` and Stored `mimetype`), multi-page PDF 1.4 generator supporting mixed Latin WinAnsi + Japanese/CJK UTF-16BE (`HeiseiKakuGo-W5` / `UniJIS-UTF16-H`), and unified Markdown / AsciiDoc book writers.
- **Kotlin Integration Test**: Added [ExportAndMarkupUniFFITest.kt](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/app/src/test/java/com/bookgen/app/ExportAndMarkupUniFFITest.kt) covering genre classification, AST parsing, EPUB progress to `Done`, and pre-cancellation across UniFFI JNA bindings.

## Design Decisions & Gotchas
1. **Japanese Text in Pure-Rust PDF 1.4**: Splitting runs into WinAnsi Helvetica/Courier segments and Type 0 `UniJIS-UTF16-H` `<UTF16BE>` segments ensures Japanese kanji/kana and ASCII code blocks both render cleanly in Android Pdfium/standard viewers without bundling multi-megabyte font payloads inside `libbookgen_core.so`.
2. **Pre-Cancel + Direct Export Compatibility**: `BookExporter::register_or_start` honors pre-reserved/pre-cancelled `job_id` entries (`reserve_job` -> `cancel_job` -> `export_book`) while keeping `cancel_job` on unreserved IDs strictly no-op and panic-free.

## Verification
- `cargo test --locked`: 12 passed, 0 failed.
- `cargo clippy --locked --all-targets -- -D warnings`: 0 errors, 0 warnings.
- `cargo fmt --check`: clean.
- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`: `BUILD SUCCESSFUL`.
- `scripts/verify_native_libs.py` & `zipalign -c -P 16 -v 4`: passed cleanly across all 3 ABIs.

## Next Step
- Merge PR and close Issue #3, then complete **Issue #4**: exhaustive Rust unit & edge-case tests (`cargo test`, `clippy`, `fmt`) wrapping up Phase 3.
