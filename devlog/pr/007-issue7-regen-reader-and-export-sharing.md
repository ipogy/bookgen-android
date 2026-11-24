# PR #7: Partial Regeneration & Tone Adjustment, Persisted Reader Customization, and Export Sharing / SAF Import (Issue #7)

- **Date**: 2026-11-25
- **Issue**: #7 (`Wire partial regeneration, tone adjustment, reader customization, and export sharing`)
- **Branch**: `feat/7-regen-reader-and-export-sharing`
- **PR**: Pending creation (links `Closes #7`)

## What Was Built

1. **Chapter & Section Partial Regeneration with Tone Adjustment (`ui/components/RegenerateBottomSheet.kt`, `WorkspaceViewModel.kt`, `WorkspaceScreen.kt`)**
   - Added `RegenerateBottomSheet` offering tone/refinement preset `FilterChip`s and freeform instruction editing for both chapter-wide regeneration (header button) and single-section regeneration (row action).
   - `WorkspaceViewModel.confirmPartialRegeneration` validates BYOK API key availability, marks ONLY the target sections `PENDING` in Rust SQLite (preserving section IDs and prior revisions), and dispatches `BookGenerationWorker` with `targetSectionIds` and `customInstruction`.

2. **Persistent Reader Customization & Normalized Reading Position (`SettingsRepository`, `ReaderViewModel`, `ReaderScreen`)**
   - Persisted app-wide `ThemePreference` and `ReaderAppearance` (`Light`/`Dark`/`Sepia`, `Serif`/`Sans`, font size `14sp–24sp`, line height `1.4x–2.1x`, horizontal padding `12–36dp`) inside `SecureSettingsRepository` + `AppContainer`.
   - Added debounced scroll tracking (`snapshotFlow { firstVisibleItemIndex }.debounce(600L)`) normalized to `[0.0, 1.0]` matching Rust `save_reading_progress_tx`'s `[0.0, 1.0]` clamp, restoring the exact normalized scroll position when reopening a book.

3. **`FileProvider` ShareSheet, SAF Export Save, and Legacy Data SAF Import (`file_paths.xml`, `ExportShareHelper.kt`, `LegacySafImporter.kt`)**
   - Configured `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`) serving generated files from `files/exports/`.
   - Wired Android `Intent.ACTION_SEND` share sheet and `CreateTypedDocumentContract` SAF device save (`EPUB 3` / `PDF` / `Markdown` / `AsciiDoc`) inside `ReaderScreen`'s `ExportBottomSheetContent`.
   - Built `LegacySafImporter` using Android `DocumentsContract` (`OpenDocumentTree` for `books/` directories and `OpenDocument` for single `app_data.db`/`.adoc`/`.md` files) copying selected files to an isolated cache staging directory for Rust `importLegacyBookgen`, with deterministic `finally` cleanup.

## Obstacles & Fixes

- **`scroll_offset` Contract Mismatch Caught by Unit Test**: Initial `WorkspaceAndReaderViewModelTest` saved raw list index `4`, which Rust SQLite's `save_reading_progress_tx` clamped to `1.0` (`expected:<4> but was:<1>`). Aligned Kotlin UI/ViewModel scroll persistence to `[0.0, 1.0]` normalized chapter fractions so scroll positions survive across varying screen widths and font sizes.

## Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`: 17/17 Kotlin JVM unit tests passed (0 failures, 0 skipped), zero Kotlin compiler warnings.
- `zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk`: verified.

## Next Steps

- Phase 5 / Issue #8 (`chore/8-audit-tests-and-docs`): Security/JNI audit (`android-jni-security`, `rust-review`, `panic="unwind"` verification), Compose UI tests (`compose-ui-testing-patterns`) & performance check, `README.md`, and `CHANGELOG.md`.
