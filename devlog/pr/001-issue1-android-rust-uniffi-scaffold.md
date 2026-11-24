# PR #1: Scaffold Android Compose Project and Rust UniFFI Pipeline

Date: 2026-11-24
Issue: [#1](https://github.com/ipogy/bookgen-android/issues/1)
Branch: `feat/1-scaffold-android-rust-uniffi`

## What Was Built
- Created `rust-core/` Cargo workspace containing:
  - `bookgen-core`: proc-macro UniFFI `0.32.2` crate exporting `BookGenError`, `CoreHealthStatus`, and `core_health_check` with `#![forbid(unsafe_code)]` and `#![deny(clippy::unwrap_used, clippy::panic, clippy::unreachable, clippy::indexing_slicing)]`.
  - `uniffi-bindgen`: workspace CLI binary pinned to `uniffi = "0.32.2"` (`cli` feature) so Gradle never picks up a mismatched global bindgen binary.
  - `.cargo/config.toml`: configures `--build-id=sha1` and `-Wl,-z,max-page-size=16384` across Android targets (`aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`).
  - `Cargo.toml` profiles: `android-jni-dev` and `android-jni` with `panic = "unwind"` and `strip = "none"` so AGP extracts unstripped symbols cleanly without breaking JVM diagnostics.
- Created `app/` Android module (AGP `9.0.1`, Gradle `9.1.0`, Kotlin `2.3.20`, Compose BOM `2026.03.01`, Nav3 `1.0.1`, WorkManager `2.12.0`, JNA `5.19.1`) wired with custom Gradle tasks:
  - `:app:buildRustHost` (builds host `bookgen_core.dll` for UniFFI metadata inspection and Robolectric/JVM unit tests)
  - `:app:generateUniffiBindings` (generates Kotlin bindings into `app/build/generated/uniffi/kotlin`)
  - `:app:buildRustAndroidDebugJni` (runs `cargo-ndk` across `arm64-v8a`, `armeabi-v7a`, `x86_64`)
- Added `scripts/verify_native_libs.py` to verify ELF machine, `PT_LOAD` page alignment (`0x4000` for 64-bit ABIs), `DT_NEEDED`, and `NT_GNU_BUILD_ID` across all packaged `.so` files.
- Added `RustCoreSmokeTest.kt` to exercise Kotlin-to-Rust roundtrips over UniFFI JNA bindings.

## Blockers Encountered & Solutions
1. **Kotlin `Throwable.message` Overload Collision**: Naming an enum error field `message` in `BookGenError` caused Kotlin `Overload resolution ambiguity` against `Exception.message`. Renamed error fields to `detail`.
2. **UniFFI `0.32.2` Flat `--config` Deprecation**: Passing `--config bookgen-core/uniffi.toml` to `uniffi-bindgen generate` triggered deprecation warnings in Cargo mode. Removed `--config` so `uniffi-bindgen` reads crate metadata directly, and passed `--no-format` since `ktlint` is absent on Windows.
3. **Windows Default JRE 1.8 vs AGP 9.0.1**: Default PATH resolved to Java 1.8. Builds require `JAVA_HOME` set to Adoptium JDK 17 (`jdk-17.0.8.7-hotspot`).

## Verification
- `cargo test` (`rust-core`): 2 passed, 0 failed (`health_check_returns_expected_status`, `health_check_rejects_blank_input`).
- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`: `BUILD SUCCESSFUL in 49s` (47 tasks).
- `python scripts\verify_native_libs.py`: `[OK]` on `arm64-v8a` (`load_align=['0x4000']`), `x86_64` (`load_align=['0x4000']`), and `armeabi-v7a` (`load_align=['0x1000']`), all with `Build ID`.
- `zipalign -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk`: `Verification successful`.

## Next Step
- Close Issue #1 via PR #1 merge, then begin **Phase 3 / Issue #2**: Rust SQLite + FTS5 persistent store, schema migrations, and legacy `bookgen` importer.
