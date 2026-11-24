# PR: Add Exhaustive Rust Unit & Integration Tests and Harden FFI Panic/FTS5 Edge Cases

Date: 2026-11-24
Issue: [#4](https://github.com/ipogy/bookgen-android/issues/4)
Branch: `test/4-rust-core-unit-tests`

## What Was Built & Hardened
- **White-box Panic & Cancellation Tests**:
  - [error.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/error.rs): Added `guard_ffi_catches_panic_without_leaking_payload_and_survives_drop_panic` proving panics (even with a panicking `Drop` payload destructor) never unwind across FFI or leak secret panic text into `BookGenError::InternalPanic`.
  - [exporter/mod.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/exporter/mod.rs): Added `mid_export_cancel_and_listener_failure_leave_no_temp_files`, `golden_determinism_and_multipage_pdf_rendering`, and `job_registry_bounds_capacity_rejects_invalid_ids_and_recovers_poison` verifying `MAX_ACTIVE_JOBS`, `MAX_JOB_ID_BYTES`, zero `.part` leftovers, deterministic output parity, monotonic `ProgressEvent`s, and `PoisonError::into_inner` recovery in `cancel_job`.
- **Integration Tests**:
  - [store_and_importer.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/tests/store_and_importer.rs): Covers input/conflict validation, `BookMetadataUpdate`, `add_chapter`/`update_chapter_meta`/`add_section`/`delete_section` reindexing, reorder permutation rejections, reading progress validation/clamping, `delete_book` FTS cleanup, and malformed legacy `plan_json` / legacy `chapters` table / orphan row recovery.
  - [parser_and_genres.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/tests/parser_and_genres.rs): Covers BOM/NUL/CRLF sanitization, unclosed code blocks/tildes/tables/math/quotes, CJK lists, all 12 genre metadata integrity checks, and invalid/non-finite/zero-norm embedding classification.
- **FTS5 Quoted Token Fix Found by Testing**:
  - In [ops.rs](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/rust-core/bookgen-core/src/store/ops.rs), `query_search` previously preserved literal surrounding `"` characters inside search tokens, causing quoted user queries like `"observability" "Renamed"` to search for literal quote characters in trigram FTS5. Normalized token quotes prior to constructing `AND` queries.

## Verification
- `cargo test --locked`: 22 passed, 0 failed (`16` crate unit tests + `3` `parser_and_genres` + `3` `store_and_importer`).
- `cargo clippy --locked --all-targets -- -D warnings`: 0 errors, 0 warnings.
- `cargo fmt --check`: clean.
- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`: passed (including `:app:testDebugUnitTest` tracking host `bookgen_core.dll` inputs cleanly in [app/build.gradle.kts](file:///c:/Users/sghar/Documents/android_apps/bookgen-android/app/build.gradle.kts)).
- `scripts/verify_native_libs.py` & `zipalign -c -P 16 -v 4`: passed cleanly.

## Next Step
- Merge PR and close Issue #4 (completing Phase 3), then start **Phase 4 / Issue #5**: Material 3 Expressive `Theme.kt` and all 4 Compose screens faithful to the approved Penpot design.
