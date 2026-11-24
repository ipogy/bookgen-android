# PR #5: Material 3 Theme, Clean Architecture Domain/Repository Bridge, Navigation 3, and 4 Core Screens (Issue #5)

- **Date**: 2026-11-25
- **Issue**: #5 (`[Phase 4] Material 3 Expressive テーマ構築・全4画面 Compose UI と Navigation 3 実装`)
- **Branch**: `feat/5-m3-theme-and-screens`
- **PR**: Pending creation (links `Closes #5`)

## What Was Built

1. **Penpot Design Extraction & Parity (`design/DESIGN.md`)**
   - Attempted live extraction from Penpot MCP (`execute_code` failed with browser tab suspended: `no heartbeat for 40s/54s`).
   - Synthesized `design/DESIGN.md` directly from the authoritative Phase 1 artifacts (`design/tokens.json` and `design/penpot-mcp-blueprint.md`), preserving exact Light (`#FAF8F5` / `#F3F0EA` / `#1E3A5F`), Dark (`#131417` / `#22252B` / `#9EC2F0`), and Sepia (`#F4ECD8` / `#EFE5CE` / `#6F4423`) surface/ink/accent palettes, `4dp` spatial grid, `4dp`/`8dp`/`12dp`/`16dp`/`24dp` corner radii, and `640dp` reader measure cap.

2. **Material 3 Theme & Extended Token System (`ui/theme/`)**
   - Implemented `Color.kt`, `Type.kt` (`tnum` tabular numerals on body/label styles; SansSerif/Serif/Monospace runtime families), `Shape.kt`, and `Theme.kt` (`BookGenTheme` + `BookGenThemeExtras` composition locals + system status/navigation bar contrast handling).

3. **Clean Architecture Domain & UniFFI Boundary (`domain/`, `data/rust/`, `data/repository/`, `core/di/`)**
   - Added pure Kotlin domain models (`BookModels.kt`) and `BookRepository` interface with zero `com.bookgen.core.uniffi.*` leakage above `data/`.
   - Added `RustEngineMappers.kt` (`runRustGuarded`, `BookGenException.toDomain()` mapping into `RecoverableUser` / `RecoverableData` / `EngineBug`) and `RustBookRepository.kt`.
   - Registered `BookGenApplication` (`AndroidManifest.xml`) and `AppContainer` (`AppDatabase` SQLite path + `exports` output dir + reactive theme preference).

4. **4 Core Compose Screens, Reusable Editorial Components & Navigation 3**
   - `ui/components/EditorialBadges.kt`: `EditorialCard`, `GenreBadge`, `BookStatusBadge`, `SectionStatusChip`, `WavyProgressTrack`.
   - `ui/components/NativeAstBlockRenderer.kt`: native Compose renderer for `DocBlock` AST (`Heading`, `Paragraph`, `CodeBlock` with `LocalClipboard` copy, `Table`, `Admonition`, `MathBlock`, `BulletList`, `NumberedList`, `Quote`, `Divider`).
   - `ui/screens/library/`: `LibraryViewModel` & `LibraryScreen` (hero resume card, FTS5 search strip, status filter chips, legacy import trigger placeholder, theme selector dialog, delete confirmation).
   - `ui/screens/wizard/`: `WizardViewModel` & `WizardScreen` (theme input with IME padding, instant keyword genre classifier badge + horizontal genre chip selector, audience/tone/target length controls).
   - `ui/screens/workspace/`: `WorkspaceViewModel` & `WorkspaceScreen` (progress summary, collapsible live stream console drawer, expandable chapter/section outline tree with up/down reordering, section editor dialog, revision history sheet).
   - `ui/screens/reader/`: `ReaderViewModel` & `ReaderScreen` (tap-to-toggle chrome, `640dp` centered reading measure with Light/Dark/Sepia palette override, `AppearanceSheet`, `TocSheet`, `ExportSheet` bound to `RustBookRepository.exportBookStream`).
   - `ui/navigation/AppNavigation.kt` & `MainActivity.kt`: Navigation 3 (`NavDisplay`, `rememberNavBackStack`, `rememberViewModelStoreNavEntryDecorator`, `dropUnlessResumed`, spring transitions) + full edge-to-edge setup (`isNavigationBarContrastEnforced = false` on Android 10+).

## Obstacles & Fixes

- **UniFFI Enum Case Mapping (`ExportFormat` / `MarkupFormat`)**: UniFFI Kotlin generated `ASCII_DOC` instead of `ASCIIDOC`; aligned `RustEngineMappers.kt`.
- **ViewModel Instantiation in Navigation 3**: Used `viewModel(key = ...) { ... }` lambda initializers backed by `rememberViewModelStoreNavEntryDecorator()` for per-destination lifecycle scoping.
- **`WindowInsetsRulers` Unresolved in Compose BOM 2026.03.01**: Replaced with standard `Modifier.consumeWindowInsets(innerPadding).imePadding()` in `WizardScreen.kt`.
- **Deprecated `LocalClipboardManager`**: Updated `NativeAstBlockRenderer.kt` to use `LocalClipboard` + `ClipEntry` + `rememberCoroutineScope`, yielding zero Kotlin compiler warnings.
- **Export Progress Swallowed by `Channel.CONFLATED`**: Caught via new `RustBookRepositoryTest` — removing `.buffer(Channel.CONFLATED)` from `exportBookStream` (preserving `callbackFlow`'s default `Channel.BUFFERED` capacity 64) guarantees `Progress` events are delivered before terminal `Completed`.

## Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`: 7 Kotlin JVM unit tests passed (`RustCoreSmokeTest`, `BookStoreUniFFITest`, `ExportAndMarkupUniFFITest`, `RustBookRepositoryTest`), 0 failures, 0 Kotlin compiler warnings.
- `python scripts/verify_native_libs.py app/build/generated/rustJniLibs`: 16 KiB `PT_LOAD` alignment verified for `arm64-v8a` & `x86_64`, plus `armeabi-v7a`.
- `zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk`: verified.

## Next Steps

- Issue #6 (`feat/6-byok-llm-and-workmanager`): Implement Android Keystore BYOK vault, Gemini/OpenRouter SSE streaming & backoff retry clients, AI outline generator, and `BookGenerationWorker` foreground service with pause/resume/notification deep-link.
