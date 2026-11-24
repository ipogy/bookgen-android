# PR #6: BYOK Keystore Vault, Gemini/OpenRouter SSE Clients, and Resumable WorkManager Pipeline (Issue #6)

- **Date**: 2026-11-25
- **Issue**: #6 (`Implement BYOK Gemini/OpenRouter SSE client and resumable WorkManager pipeline`)
- **Branch**: `feat/6-byok-llm-and-workmanager`
- **PR**: Pending creation (links `Closes #6`)

## What Was Built

1. **Android Keystore BYOK Encrypted Vault (`domain/model/AiSettings.kt`, `data/security/SecretCipher.kt`, `data/repository/SecureSettingsRepository.kt`)**
   - Uses hardware-backed `AndroidKeyStore` (`AES/GCM/NoPadding`, 256-bit) wrapping IV + ciphertext Base64 payloads into private `SharedPreferences`. Plaintext API keys are never persisted to Rust SQLite or exposed in `toString()` / HTTP query strings.
   - Pre-populates legacy default model IDs (`gemini-embedding-2`, `inclusionai/ling-3.0-flash`, OpenRouter `novita`, concurrency `2`), plus per-book `MarkupFormat` preference storage.

2. **Direct Gemini & OpenRouter HTTP/SSE Clients (`data/remote/`)**
   - `UnifiedLlmHttpClient`: implements Gemini `embedContent`, `generateContent`, `streamGenerateContent?alt=sse` (`x-goog-api-key` header) and OpenRouter `chat/completions` (non-streaming & SSE `stream: true`, `provider.only` routing without `response_format` per legacy Novita workaround) with cooperative coroutine cancellation.
   - `SseEventParser`: parses multi-line SSE frames for both Gemini and OpenRouter streams while ignoring keepalive comments and `[DONE]` markers.
   - `HttpRetryPolicy`: jittered exponential backoff (up to 3 retries), HTTP `Retry-After` header support, retry on HTTP `429`/`500`/`502`/`503`/`504` / `IOException` / empty outputs, fail-fast on auth `401`/`403`/`400` and `CancellationException`.
   - `LlmJsonRepair`: strips `<think>` blocks and fences, extracts balanced JSON objects, repairs trailing commas, and validates chapter/section trees before saving.

3. **Context-Aware Prompt Engineering & Resumable Pipeline (`domain/service/BookPromptBuilder.kt`, `data/pipeline/`)**
   - Injects full Rust `GenreInfo` (`editorGuide`, `elementLabel`, `authorStyle`) plus book overview, previous section tail excerpt, and next section teaser into both planner and writer prompts.
   - `GenerationPipeline`: resolves genres via Gemini Embedding + Rust cosine matching (falling back cleanly to keyword matching), executes sections under `Semaphore(maxConcurrency)`, enforces `MIN_SECTION_NON_WS_CHARS = 40` after Rust sanitization, persists each completed section immediately, and broadcasts throttled token snippets on `GenerationProgressBus`.

4. **Foreground WorkManager Worker & Studio UI Integration (`worker/BookGenerationWorker.kt`, `AndroidManifest.xml`, `ui/components/AiSettingsSheet.kt`)**
   - Configured `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, and `SystemForegroundService` (`dataSync`) in `AndroidManifest.xml`.
   - Added ongoing progress notification with Pause/Cancel action, completion/failure alert notification, and `MainActivity` deep-link intent targeting `WorkspaceRouteKey(bookId)`.
   - Wired shared `AiSettingsBottomSheet` into Library, Wizard, and Workspace; added AI outline toggle in `WizardScreen` and start/resume/pause + Android 13+ notification permission flow in `WorkspaceScreen`.

## Obstacles & Fixes

- **Prompt Next-Section Context Caught by Mock Test**: Our mock failure test matching `"第2節"` inadvertently matched Section 1's prompt because `BookPromptBuilder` genuinely injected Section 2's teaser (`次節予定: 第2節...`) into Section 1! Narrowed the failure predicate to `"担当節: 第2節"` so partial-save and resume-only-failed-section behavior is verified cleanly.
- **WorkManager Deprecation Warning**: Updated `inputData.getStringArray` -> `getNullableStringArray` in `BookGenerationWorker.kt` to maintain zero Kotlin compiler warnings.

## Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`: 15/15 Kotlin JVM unit tests passed (0 failures, 0 skipped), zero Kotlin compiler warnings.
- `zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk`: verified.

## Next Steps

- Issue #7 (`feat/7-regen-reader-and-export-sharing`): Partial chapter/section regeneration with tone presets & custom instructions, persistent `ReaderAppearance` prefs, `FileProvider` share sheet & SAF export save, and SAF picker for legacy DB/directory import.
