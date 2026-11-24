# Penpot MCP 再現用 4画面レイアウト・ブループリント

> 本ドキュメントは `penpot-foundations`, `penpot-build-screen`, `penpot-anti-slop`, `mobile-design-android` に基づき、Penpot MCP 接続時に `execute_code` でそのまま構築できる 4画面（`412 × 915 dp` Android Compact基準）の構造・トークンバインディング・Anti-Slop Direction を定義する。

## Direction（Anti-Slop Playbook）

- **回避するデフォルト**:
  - 紫/ネオン系の汎用AIグラデーション（`violet-gradient`）
  - 中央寄せヒーロー＋3枚均等アイコンカード（`centered-hero-stack`, `identical-triplet`）
  - AIを示すキラキラ絵文字（`sparkle-ai-marker`, `emoji-as-icon`）
  - デフォルト書体 `Inter` 単独使用（`default-typeface`）および純白/純黒背景
- **代替する設計判断**:
  - **素材起点**: 「書籍の章構成と組版された紙面」そのものを主役にする
  - **配色**: 温かみのある石材・紙面ニュートラル（`#FAF8F5` / `#F3F0EA`）＋ 万年筆インクの深い藍（`#1E3A5F`）を主CTAに限定。Reader用 `modes/sepia`（`#F4ECD8`）を独立モードとして定義
  - **タイポグラフィ**: `IBM Plex Sans` + `Noto Sans JP`（等幅数字）、Reader本文オプションに `Shippori Mincho` + `Noto Serif JP`、コードに `JetBrains Mono`
  - **画面骨格の多様性（Variety Rule）**:
    1. **Screen 1 (`Library`)**: Topbar + FTS5検索バー + 1件の「続きから」ヒーローカード + ヘアライン区切りリスト + `16dp`角丸FAB
    2. **Screen 2 (`Wizard`)**: 単一カラムのステッパーフォーム（ジャンル自動/手動切替、構成テンプレート、分量スライダー）＋ 下部固定CTAバー
    3. **Screen 3 (`OutlineWorkspace`)**: 2ゾーン構造（波形進捗ヘッダー + 編集/並び替え可能な章・節ツリー＋展開式ストリーミングコンソール）
    4. **Screen 4 (`ReaderAndExport`)**: フルブリード読書キャンバス＋タップ表示の上下バー＋表示設定(`Aa`)シート / 書き出し(`EPUB/PDF/MD/ADOC`)シート

---

## Screen 1: `Library` (412 × 915)
- **Root Board**: `Library`, flex `column`, fill=`color.bg.default`, padding=`spacing.inset.md` (`16`)
- **Sections**:
  1. `header` (`TopAppBar`, 56px): Title `Co-Author Studio` (`titleLarge`, `IBM Plex Sans` 700), trailing `IconButton`s (`Import`, `Settings`, `48x48` hit target)
  2. `search-strip` (structural board, `fills=[]`): FTS5検索 `SearchField` (`color.bg.surfaceHigh`, `radius.chip`) + 横スクロール `FilterChip` 列 (`すべて`, `執筆中`, `要再開`, `完了`)
  3. `hero-resume-card` (surface board, `color.bg.surface`, `radius.card`, `1dp` border `color.border.subtle`): 直近の執筆途中/読書中1冊（例: `実務で使うLLM Embedding...`, `29/32 節` — 未完警告バッジ `3節欠損・再開可` + Primaryボタン `執筆を再開`）
  4. `section-library-list` (structural board, `fills=[]`): セクション見出し `ライブラリ (7冊)` + ヘアライン区切りの書籍行リスト（`ListItem`、ジャンルタグ `radius.badge` `4dp`、文字数・章数・進捗バー、状態アイコン）
  5. `fab` (absolute bottom-end `16dp` margin above nav): `ExtendedFloatingActionButton` (`radius.fab` `16dp`, fill=`color.action.primary.bg`, label=`新しい本を書く`)

## Screen 2: `Wizard` (412 × 915)
- **Root Board**: `Wizard`, flex `column`, fill=`color.bg.default`
- **Sections**:
  1. `header` (`TopAppBar`): Back icon (`48x48`), Title `新しい本を企画`, trailing step pill `1 / 2`
  2. `form-theme` (surface `color.bg.surfaceLow`, `radius.card`): ラベル `書籍のテーマ・企画メモ` + 複数行入力欄 + サンプルチップ（`技術実務書`, `資格対策`, `小説プロット`）
  3. `form-genre` (structural): `ジャンル判定` 切替（`AI自動判定 (Embedding)` / `手動選択 (12種)`）＋ ジャンル選択チップ群
  4. `form-parameters` (surface `color.bg.surfaceLow`, `radius.card`):
     - 構成テンプレート選択（`標準専門書 6-8章` / `短期集中 4-5章` / `試験対策`）
     - スライダー: `章数 (6)` / `1章の節数 (4)` / `目標分量 (標準 3,000字/節)`
     - `ButtonGroup`: 出力記法 (`Markdown` / `AsciiDoc`)、文体トーン (`厳密・論理` / `平易・解説` / `実践ハンズオン`)
     - モデル選択行 (`Planner` / `Writer` モデル表示＋変更)
  5. `bottom-cta-bar` (surface `color.bg.surface`, hairline top border): `SplitButton` (`構成案を生成する` / テンプレート保存)

## Screen 3: `OutlineWorkspace` (412 × 915)
- **Root Board**: `OutlineWorkspace`, flex `column`, fill=`color.bg.default`
- **Sections**:
  1. `header` (`TopAppBar`): Back, 書籍タイトル（省略付）, actions: `リーダーで読む`, `More`
  2. `pipeline-status-card` (surface `color.bg.surface`, `radius.card`):
     - 全体進捗: `18 / 28 節 完了 (64%)` + M3 Expressive `WavyLinearProgressIndicator` (`color.accent.tertiary.bg`)
     - バックグラウンド状態注記: `バックグラウンド実行有効・画面を閉じても継続`
     - アクション行: Primary `残りの10節を執筆` / Secondary `一時停止`
  3. `live-stream-drawer` (surface `color.slate.900` console strip, collapsible): 現在執筆中 `第5章 第2節` のリアルタイムトークン表示
  4. `outline-tree` (structural scrollable):
     - 章カード（折りたたみ可能、上下並べ替えハンドル、章タイトル編集、**「章を再生成 / トーン調整」**ボタン）
     - 子要素 `section-row` × N: 番号・節タイトル・状態バッジ（`完了 4,210字` / `執筆中` / `要再試行 (空応答)`）、タップで単一節ソース編集または個別再生成BottomSheetを開く

## Screen 4: `ReaderAndExport` (412 × 915)
- **Root Board**: `ReaderAndExport`, flex `column`, fill=`color.bg.default` (Light / Dark / **Sepia `#F4ECD8`** で切替確認)
- **Sections**:
  1. `reading-progress-bar`: 上端 `3dp` 進捗ライン (`color.action.primary.bg`)
  2. `header` (`TopAppBar`, タップで自動非表示): Back, `第1章: データ指向設計の基礎`, actions: `目次(TOC)`, `表示設定(Aa)`, `書き出し`
  3. `reader-canvas` (`maxWidth: 640dp`, padding `20dp`):
     - 章バッジ + 文字数 + 読了目安 (`18分`)
     - 本文タイポグラフィ（`bodyLarge` 16–18sp, line-height 1.75）、見出しH2/H3、シンタックスハイライト風コードブロック（コピーボタン付）、Admonition枠 (`NOTE: ...`)、数式ブロック
  4. `reader-bottom-nav` (surface `color.bg.surfaceLow`): `← 概要` / `第2章 →`
  5. **Overlay BottomSheets（別Boardとして設計）**:
     - `AppearanceSheet`: テーマ選択カード (`Light` / `Dark` / `Sepia`)、書体切替 (`Sans` / `Serif`)、文字サイズ・行間・余白スライダー
     - `ExportSheet`: フォーマットカード 4種（**EPUB 3** / **PDF** / **Markdown** / **AsciiDoc**）、範囲選択、生成プログレス＆Android共有ボタン
