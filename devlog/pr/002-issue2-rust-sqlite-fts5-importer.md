# PR: Implement Rust SQLite + FTS5 Store, Models, and Legacy Importer

Date: 2026-11-24
Issue: [#2](https://github.com/ipogy/bookgen-android/issues/2)
Branch: `feat/2-rust-sqlite-fts5-importer`

## What Was Built
- **`bookgen_core::error` ([error.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/error.rs))**:
  - Expanded typed UniFFI error taxonomy (`InvalidInput`, `NotFound`, `Conflict`, `Storage`, `IoFailed`, `Parse`, `Export`, `Cancelled`, `InternalPanic`) with `rusqlite`, `serde_json`, and `io` conversions free of raw secrets or local paths.
  - Added `guard_ffi` (`catch_unwind` + `ManuallyDrop` double-panic payload disposal helper) around FFI entry points.
- **`bookgen_core::models` ([models.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/models.rs))**:
  - Added `BookStatus`, `SectionStatus`, `MarkupFormat`, `BookSummary`, `ChapterRecord`, `SectionRecord`, `ChapterWithSections`, `BookDetail`, `SectionRevision`, `ReadingProgress`, `SearchHit`, `IncompleteSection`, `NewBookDraft`, `BookMetadataUpdate`, `NewChapterPlan`, `NewSectionPlan`, `LegacyImportReport`, plus CJK-aware word/char counters and derived book status rules.
- **`bookgen_core::store` ([mod.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/store/mod.rs), [schema.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/store/schema.rs), [ops.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/store/ops.rs), [importer.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/store/importer.rs))**:
  - Configured bundled SQLite WAL mode (`journal_mode = WAL`, `foreign_keys = ON`, `busy_timeout = 5000`) and atomic `schema_migrations` (`v1`).
  - Normalized `books`, `chapters`, `sections`, `section_revisions`, `reading_progress`, and `sections_fts` (`tokenize = 'trigram'`).
  - Preserved per-section integrity on edits and reorders (eliminating Web `update_chapter`'s destructive section wipe bug from `DIG-001`), automatically flagging whitespace-only content as `SectionStatus::Empty` and indexing `list_incomplete_sections`.
  - Implemented FTS5 `trigram` search (`snippet(..., '«', '»', '…')`) for >= 3-char queries plus escaped `LIKE` fallback with contextual snippets for 1–2 char CJK kanji queries.
  - Built idempotent legacy `bookgen` importer for `app_data.db` (`books`, `chapter_sections`, legacy `chapters`) and `books/<id>/book.json` + `chapter_*.{adoc,md}`, recovering prefixed section numbers (e.g. `11..14`, `71..74`) cleanly.
- **Kotlin Integration Test**: Added [BookStoreUniFFITest.kt](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/app/src/test/java/com/bookgen/app/BookStoreUniFFITest.kt) verifying `BookStore` CRUD, FTS5 search, empty-section detection, and typed `NotFound` exceptions over UniFFI JNA bindings.

## Blockers & Design Notes
1. **Japanese Substring Search in SQLite FTS5**: Standard `unicode61` does not split unspaced CJK text; bundled SQLite `trigram` handles >= 3-character Japanese/English substrings cleanly, paired with an escaped `LIKE` fallback for 1–2 kanji queries.
2. **Legacy Anomalous Section Numbers**: Confirmed in `app_data.db` (`chapter_sections` rows numbered `11..14` and `71..74` under chapters 1 and 7); `importer.rs` matches both local (`1..N`) and `chapter_number * 10 + k` numbers before normalizing positions cleanly.

## Verification
- `cargo test --locked`: 6 passed, 0 failed (`bookgen-core`).
- `cargo clippy --locked --all-targets -- -D warnings`: 0 errors, 0 warnings.
- `cargo fmt --check`: clean.
- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`: `BUILD SUCCESSFUL in 3m 23s` (47 tasks; `libsqlite3-sys v0.37.0` built across `arm64-v8a`, `armeabi-v7a`, `x86_64`).
- `scripts/verify_native_libs.py` & `zipalign -c -P 16 -v 4`: all 3 ABIs verified (`0x4000` alignment on `arm64-v8a`/`x86_64`) and APK alignment passed.

## Next Step
- Close Issue #2 upon PR merge, then start **Issue #3**: markup AST parser (`AsciiDoc`/`Markdown`), bundled 12-genre cosine classifier, and streaming/cancellable EPUB 3 / PDF / Markdown / AsciiDoc exporters.
