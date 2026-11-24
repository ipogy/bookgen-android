# Co-Author Studio (`bookgen-android`)

Android port and native redesign of the legacy Web application `bookgen/` using **Kotlin (Jetpack Compose / Material 3 / Clean Architecture / Navigation 3 / WorkManager)** paired with a **Rust (`bookgen-core`)** core exposed through **Mozilla UniFFI 0.32.2** and bundled SQLite WAL + FTS5.

## Key Improvements Over Legacy Web Version

- **Offline-First & Resumable Section Pipeline**: Tracks explicit `PENDING`, `GENERATING`, `COMPLETED`, `FAILED`, and `EMPTY` section statuses in SQLite WAL (`rusqlite 0.39`). WorkManager (`BookGenerationWorker`) runs with a foreground progress notification (`dataSync`), pause/cancel control, notification deep-linking (`MainActivity.EXTRA_BOOK_ID`), and safe section-by-section resume.
- **Section Granularity Preserved**: Edits and revisions maintain stable `(book_id, chapter_id, section_id)` UUIDs with full `section_revisions` rollback history (fixing the legacy `update_chapter` section collapse defect).
- **Genre & Context Prompt Injection**: Uses bundled 12-genre metadata (`editor_guide`, `element_label`, `author_style`) and 3072-dim genre embeddings in Rust alongside previous/next section context and chapter/section partial regeneration presets (`RegenerateBottomSheet`).
- **BYOK Security**: Encrypts user-supplied Gemini & OpenRouter API keys via `AndroidKeyStore` (`AES/GCM/NoPadding`, `KeystoreSecretCipher`) with redacted `AiSettings.toString()`, `x-goog-api-key` headers, and zero key persistence in SQLite.
- **Native AST Reader & Multi-Format Export**: Parses Markdown and AsciiDoc into typed `DocBlock` nodes rendered with Material 3 Light (`#FAF8F5`), Dark (`#131417`), and Sepia (`#F4ECD8`) palettes (`640dp` reading measure), persistent appearance/scroll memory (`[0.0, 1.0]`), and offline **EPUB 3**, **PDF 1.4**, **Markdown**, and **AsciiDoc** exports shared via `FileProvider` or saved via SAF.
- **Legacy Migration**: Supports Android 11+ Storage Access Framework (`OpenDocumentTree` / `OpenDocument`) staging via `LegacySafImporter` into Rust `BookStore.import_legacy_bookgen`.

## Architecture

```
+-------------------------------------------------------------------+
| app/src/main/java/com/bookgen/app (Kotlin / Compose M3)           |
|  - ui/screens/{library,wizard,workspace,reader} + navigation      |
|  - worker/BookGenerationWorker + data/pipeline/GenerationPipeline |
|  - data/remote (Gemini & OpenRouter SSE, backoff retry, JSON fix) |
|  - data/security (AndroidKeyStore AES-256-GCM SecretCipher)       |
|  - data/repository/RustBookRepository (Adapter isolating UniFFI)  |
+-------------------------------------------------------------------+
                                  |
                        Mozilla UniFFI 0.32.2
                                  |
+-------------------------------------------------------------------+
| rust-core/bookgen-core (Rust)                                     |
|  - store/: SQLite WAL + FTS5 trigram search + legacy DB/FS import |
|  - genres.rs: 12-genre catalog + 3072-dim cosine classifier       |
|  - markup.rs: Markdown / AsciiDoc AST + output sanitizer          |
|  - exporter/: EPUB 3 (zip), PDF 1.4 (WinAnsi + UniJIS), MD, ADOC  |
|  - error.rs: guard_ffi unwind boundary + structured BookGenError  |
+-------------------------------------------------------------------+
```

## Prerequisites

- **JDK**: 17 (e.g. Eclipse Adoptium JDK 17) pointed to by `JAVA_HOME`
- **Android SDK**: `compileSdk = 36`, `minSdk = 26`, NDK `28.2.13676358`
- **Rust Toolchain**: `rustc` / `cargo` + Android targets (`aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`) and `cargo-ndk`

## Verification Commands

### Rust Core Tests & Lint

```powershell
cd rust-core
cargo fmt --check
cargo clippy --locked --all-targets -- -D warnings
cargo test --locked
```

### Android Unit Tests, Compose UI Tests & Debug APK

```powershell
# Note: ensure JAVA_HOME points to JDK 17 before running gradlew.bat
.\gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin assembleDebugAndroidTest --no-daemon
```

### 16 KiB Native ELF & APK Page Alignment Check

```powershell
python scripts/verify_native_libs.py app/build/generated/rustJniLibs
zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
```

## Documentation & Engineering Log

- [Specification (`spec.md`)](spec.md)
- [Design System (`design/DESIGN.md`)](design/DESIGN.md)
- [Changelog (`CHANGELOG.md`)](CHANGELOG.md)
- [Development Log & ADRs (`devlog/`)](devlog/plan/001-android-port-roadmap.md)
