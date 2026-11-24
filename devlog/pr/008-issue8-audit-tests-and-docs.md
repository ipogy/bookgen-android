# PR #8: Security/UniFFI Audit, Compose Performance Optimization, Full Verification & Docs (Issue #8)

- **Date**: 2026-11-25
- **Issue**: #8 (`Audit FFI security, Compose performance, full test suite, and release docs`)
- **Branch**: `chore/8-audit-tests-and-docs`
- **PR**: Pending creation (links `Closes #8`)

## What Was Audited & Built

1. **UniFFI Packaging & Rust FFI Security Audit (`uniffi-packaging-versioning`, `android-jni-security`, `rust-review`)**
   - Added explicit `com.bookgen.core.uniffi.uniffiEnsureInitialized()` startup check inside `RustBookRepository` before opening `BookStore`, ensuring UniFFI 0.32.2 API checksum verification runs on single-crate startup.
   - Verified zero production `unsafe` blocks, zero `.unwrap()` / `.expect()` / `panic!` calls outside `#[cfg(test)]` unwind/poison tests, and verified `panic="unwind"` on the `arm64-v8a` (`aarch64-linux-android`, `--profile android-jni`) target via `cargo ndk -t arm64-v8a rustc --locked -p bookgen-core --profile android-jni -- --print cfg`.
   - Verified `AndroidManifest.xml` (`allowBackup="false"`, `FileProvider` `exported="false"`, `grantUriPermissions="true"`), `proguard-rules.pro` UniFFI/JNA keep rules, and `KeystoreSecretCipher` AES-256-GCM key protection.

2. **Jetpack Compose Performance Optimization (`jetpack-compose-performance`)**
   - Refactored `WavyProgressTrack` in `EditorialBadges.kt` from a recomposing `Canvas` + per-frame `Path()` allocation to `Box` + `Modifier.drawWithCache` + `Path.rewind()` with Draw-phase `State<Float>` reads and conditional `rememberInfiniteTransition` (only allocated when `isActive && progress < 1f`).
   - Replaced `sec.blocks.mapIndexed { ... to ... }` `Pair` allocation in `ReaderScreen.kt` with `itemsIndexed(items = sec.blocks, key = ..., contentType = { _, block -> block::class })`.
   - Added explicit `contentType` across all `LazyColumn` items in `LibraryScreen.kt`, `WizardScreen.kt`, `WorkspaceScreen.kt`, and `ReaderScreen.kt`, and replaced the 4-item `LazyRow` in `LibraryScreen` with `Row` + `horizontalScroll`.

3. **Tests & Documentation (`compose-ui-testing-patterns`, `changelog-automation`)**
   - Added `WizardViewModelTest.kt` (AI outline creation + AsciiDoc format persistence + offline skeleton fallback).
   - Added plain-UI semantic/callback Compose tests in `app/src/androidTest/java/com/bookgen/app/ui/BookGenComposeUiTest.kt` covering all 4 screens (`LibraryScreen`, `WizardScreen`, `WorkspaceScreen`, `ReaderScreen`).
   - Authored repository `README.md` and Keep-a-Changelog `CHANGELOG.md` (`v0.1.0`).

## Verification

- **Rust Core (`rust-core/`)**:
  - `cargo fmt --check`: passed (exit 0)
  - `cargo clippy --locked --all-targets -- -D warnings`: 0 warnings (exit 0)
  - `cargo test --locked`: **22/22 passed** (16 unit + 3 `parser_and_genres` + 3 `store_and_importer`, 0 failed, 0 skipped)
- **Android / Kotlin / APK**:
  - `.\gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin assembleDebugAndroidTest --no-daemon` (with user-approved `$env:JAVA_HOME` JDK 17 override): **19/19 Kotlin JVM unit tests passed** (9 test suites, 0 failed, 0 skipped), zero Kotlin warnings, generated `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
  - Note on instrumented UI tests: `BookGenComposeUiTest` compiled and packaged cleanly into `app-debug-androidTest.apk`; on-device execution (`connectedDebugAndroidTest`) did not run because `adb devices` had 0 attached devices/emulators.
  - `python scripts/verify_native_libs.py app/build/generated/rustJniLibs`: passed 3 ABIs (`arm64-v8a` `0x4000`, `x86_64` `0x4000`, `armeabi-v7a` `0x1000`).
  - `zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk`: passed (exit 0).
