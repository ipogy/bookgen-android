# ADR-002: BYOK 直接LLMクライアント化と WorkManager による再開可能生成パイプライン

Status: Proposed

Release: Unreleased

Related: [PLAN-001](../plan/001-android-port-roadmap.md), [DIG-001](../dig/001-legacy-bookgen-forensic-audit.md), [ADR-001](../adr/001-rust-uniffi-core-architecture.md)

## Problem Statement

Web版 `bookgen/` は PHP サーバー（`api.php` + `config.php`）が API キーを保持し、ブラウザが全節の生成ループを制御していたため、モバイル端末でタブを閉じると執筆が中断し、1節の失敗で未保存欠損（実測で7冊中4冊に欠損・3件の空節）が発生していた。

## Context

- Android アプリ単体としてオフラインでもライブラリが機能し、ネットワーク復帰後に続きを執筆できる必要がある。
- 既存の `api.php` は Gemini (`generativelanguage.googleapis.com`) と OpenRouter (`openrouter.ai`) の 2 系統に対応しているが、サーバーサイドプロキシ前提の実装になっている。

## Options Considered

1. **既存 PHP バックエンド (`api.php`) のラッパーとして Android を作る**
   - 長所: サーバー側の招待コード・共有DBを流用できる。
   - 短所: オフライン不可、PHP `max_execution_time=180` 制限や `update_chapter` の節破壊バグ・非ストリーミングの制約を引きずる。サーバーデプロイなしに利用できない。
2. **BYOK（ユーザー自身の Gemini / OpenRouter API キーを Android Keystore に保存）＋ アプリ内直接通信 ＋ WorkManager 再開可能パイプライン（選定案）**
   - 長所: サーバー不要で完結。Ktor SSE ストリーミング、節単位の状態管理（`pending`/`generating`/`completed`/`failed`/`empty`）、指数バックオフリトライ、Android OS バックグラウンド継続・完了通知を実現できる。既存 Web 版データのインポートも Rust 側で対応可能。
   - 短所: ユーザー自身が初回設定画面で API キーを入力する必要がある（ただしデモ閲覧・ローカル編集・既存DBインポートはキー不要）。

## Decision

オプション2を採用する：
- Gemini API および OpenRouter API への直接クライアントを Kotlin (Ktor) で実装し、SSE ストリーミング・429/5xx 指数バックオフリトライ・空応答拒否バリデーションを組み込む。
- API キーは `EncryptedSharedPreferences` (Android Keystore) にのみ保存し、ログや Rust DB には保存しない。
- 生成パイプラインは `WorkManager` (`CoroutineWorker` + `ForegroundInfo`) で実行し、各節完了ごとに即座に Rust SQLite へ永続化する。失敗・中断時は欠損/空の節だけを抽出してレジューム可能とする。
- 将来的に自作 PHP サーバーとの同期が必要になった場合に備え、データインポート機能（`app_data.db` / `.adoc` / `.md` 読み込み）を標準提供する。

## Consequences

- `config.php` の `INVITE_CODE` / PHP セッション / IP レート制限テーブルは Android ローカル版では不要となり、代わりにクライアント側の並列数制限（セマフォ）とリトライ制御で API レート制限に対応する。
- デバイス再起動やアプリ強制終了後も、Rust DB 上の `section.status` を参照して安全に執筆を再開できる。
