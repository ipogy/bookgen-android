# ADR-001: Rust (`rust-core`) + Mozilla UniFFI と Kotlin (Compose MVVM) の責務分割

Status: Accepted (Issue #1 verified with UniFFI 0.32.2 + cargo-ndk)

Release: Unreleased

Related: [PLAN-001](../plan/001-android-port-roadmap.md), [DIG-001](../dig/001-legacy-bookgen-forensic-audit.md)

## Problem Statement

既存 Web 版 `bookgen/` は PHP サーバー側の SQLite 操作とブラウザ側の CDN JavaScript（Asciidoctor.js / KaTeX）に処理を分散させており、オフライン閲覧・高速全文検索・多形式（EPUB/PDF/Markdown）ビルド・大規模書籍（100KB〜300KB超）の低遅延処理を実現できていない。Android 版ではこれらを高速かつ安全に統合する必要がある。

## Context

- ターゲットは Android ネイティブアプリ（Kotlin / Jetpack Compose / Material 3 Expressive / MVVM）。
- 長編書籍（1冊あたり 6〜8章・20〜32節・5万〜30万文字規模）のパース、検索、フォーマット変換、永続化をモバイル端末上で快適に処理したい。
- ネットワーク・バックグラウンド制御（WorkManager, Foreground Service, Keystore）および UI 描画は Android プラットフォーム API と密結合している。

## Options Considered

1. **オール Kotlin 実装（Room + Kotlin純粋パーサー + iText/Android PDF）**
   - 長所: FFI 境界が不要でビルドが単純。
   - 短所: 指定技術スタック（Rust + UniFFI）に反する。EPUB ZIP/XHTML 生成、高速全文検索、マークアップAST処理のテスタビリティと将来のマルチプラットフォーム展開性が劣る。
2. **Rust コアに HTTP 通信まで全面統合（reqwest + tokio in Rust）**
   - 長所: 通信ロジックも Rust に集約できる。
   - 短所: Android の `NetworkCallback`・証明書のシステムトラスト・WorkManager キャンセル連動・プロキシ設定・SSEの Compose `StateFlow` 接続が JNI/UniFFI 越しに複雑化する。
3. **ハイブリッド分割（選定案）: データ・検索・解析・変換・Embedding分類を Rust (`rust-core`)、UI・Android OS連携・HTTP/SSEクライアントを Kotlin**
   - 長所: Rust は純粋で決定論的なローカル計算・DB・I/O（SQLite+FTS5, ASTパース, EPUB/PDF/MDビルド, コサイン類似度, `cargo test` による高速網羅テスト）に集中でき、Kotlin は Compose 状態管理・Ktor SSE・WorkManager・Keystore に専念できる。

## Decision

オプション3を採用する：
- **`rust-core` (Rust + Mozilla UniFFI)**:
  - `rusqlite` (bundled SQLite + FTS5) によるローカル永続化・全文検索・リビジョン管理・Web版 `app_data.db`/`.adoc`/`.md` インポート
  - AsciiDoc / Markdown AST パーサー（Compose ネイティブブロックレンダリング用の中間構造出力）、LLM出力正規化（コードフェンス誤破壊防止、節番号正規化）
  - バンドル済み 3072次元ジャンルベクトルを用いたコサイン類似度分類
  - EPUB 3 / PDF / 統合 Markdown / AsciiDoc エクスポーター（`ExportProgressListener` + `CancellationToken` + `catch_unwind` によるパニック安全保証）
- **`bookgen-android` (Kotlin + Jetpack Compose + MVVM)**:
  - Material 3 Expressive UI 4画面と状態管理（`StateFlow`）
  - Ktor Client による Gemini / OpenRouter API（SSE ストリーミング + ジッター付きリトライ）
  - `WorkManager` + Foreground Notification によるバックグラウンド生成・途中再開
  - Android Keystore による API キー暗号化保管と SAF/ShareSheet ファイル共有

## Consequences

- `cargo-ndk` と UniFFI バインディング生成タスクを Gradle ビルドへ統合する初期コストが発生する（Phase 2 で解決）。
- Rust 側ロジックの大半を JVM/エミュレータ無しで `cargo test` により高速検証できる。
- UniFFI 境界を越えるデータ量（章単位・検索スニペット単位の取得）を小さく保つことで JNI コピーコストを最小化できる。
