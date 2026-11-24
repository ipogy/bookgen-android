---
version: alpha
name: Co-Author-Studio-Mobile
description: "Editorial warm paper surfaces paired with fountain-pen indigo ink chrome and terracotta/teal workflow accents. Built on #FAF8F5 Alabaster and #F3F0EA Stone canvases in Light mode, #131417 Slate in Dark mode, and a dedicated #F4ECD8 Sepia paper palette for long-form reading. Uses IBM Plex Sans + Noto Sans JP with tabular numerals for studio UI, Shippori Mincho + Noto Serif JP for book typography, and JetBrains Mono for code blocks, keeping a strict 4dp spatial grid, 12dp cards, 16dp rounded-square FAB, hairline borders without blurry drop shadows, and zero purple gradients."

colors:
  canvas: "#FAF8F5"
  surface-low: "#F3F0EA"
  surface-1: "#F3F0EA"
  surface-2: "#EAE5DC"
  ink: "#1A1917"
  ink-muted: "#5E5A54"
  hairline: "#DED8CE"
  primary: "#1E3A5F"
  on-primary: "#FAF8F5"
  primary-container: "#D6E3F4"
  on-primary-container: "#0E2340"
  secondary: "#9A4A2E"
  secondary-container: "#F6DDD4"
  on-secondary-container: "#3D180B"
  tertiary: "#2C6B6E"
  tertiary-container: "#D0ECEC"
  on-tertiary-container: "#0D2D2F"
  semantic-danger: "#B3261E"
  semantic-warning: "#9C5B00"
  semantic-success: "#1E6E3E"
  sepia-canvas: "#F4ECD8"
  sepia-surface-low: "#EFE5CE"
  sepia-surface-high: "#E4D7BA"
  sepia-ink: "#3B2F23"
  sepia-ink-muted: "#6E604F"
  sepia-primary: "#6F4423"

typography:
  headline-large:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 32px
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: -0.2px
  headline-medium:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 28px
    fontWeight: 600
    lineHeight: 1.28
    letterSpacing: 0
  title-large:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 23px
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: 0
  title-medium:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 19px
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: 0.1px
  body-large:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.65
    letterSpacing: 0.15px
  body-medium:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: 0.15px
  label-medium:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 1.35
    letterSpacing: 0.3px
  label-small:
    fontFamily: "IBM Plex Sans, Noto Sans JP"
    fontSize: 11px
    fontWeight: 500
    lineHeight: 1.3
    letterSpacing: 0.4px
  reader-serif:
    fontFamily: "Shippori Mincho, Noto Serif JP"
    fontSize: 17px
    fontWeight: 400
    lineHeight: 1.75
    letterSpacing: 0.1px
  code-mono:
    fontFamily: "JetBrains Mono"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0

rounded:
  none: 0px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  pill: 9999px

spacing:
  none: 0px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  xxl: 24px
  section: 32px

components:
  hero-resume-card:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    borderColor: "{colors.hairline}"
    rounded: "{rounded.md}"
    padding: 16px
  extended-fab-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.lg}"
    padding: 16px 20px
  filter-chip:
    backgroundColor: "{colors.surface-low}"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.pill}"
  genre-badge:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label-small}"
    rounded: "{rounded.xs}"
    padding: 4px 8px
  live-stream-drawer:
    backgroundColor: "#1B1D22"
    textColor: "#ECE9E2"
    typography: "{typography.code-mono}"
    rounded: "{rounded.md}"
    padding: 12px 16px
---

## Overview

Co-Author Studio Mobile rejects generic neon/violet AI dashboards in favor of editorial craft: typography, paper warmth, and crisp chapter hierarchy take center stage. Warm `#FAF8F5` alabaster canvases paired with subtle `#F3F0EA` stone cards and `1px` `#DED8CE` hairlines create an unhurried reading and writing workspace that remains legible during multi-hour mobile sessions.

The single chromatic primary accent `#1E3A5F` (fountain-pen indigo ink) is reserved strictly for high-intent calls to action such as resuming a manuscript, triggering outline generation, and primary reading progress bars. Secondary terracotta `#9A4A2E` highlights resumable warnings or empty-section attention states, while calm teal `#2C6B6E` drives live AI streaming progress indicators.

**Key Characteristics:**
- Warm editorial paper surfaces (`{colors.canvas}`, `{colors.surface-1}`) with zero pure `#FFFFFF` or `#000000` backgrounds.
- Fountain-pen indigo (`{colors.primary}`) used selectively on primary CTAs and progress chrome.
- Three complete palette modes (`light`, `dark`, `sepia`) backed by semantic token aliases.
- Strict 4dp spacing grid (`{spacing.xs}` through `{spacing.section}`) and `640dp` max measure in the Reader canvas.
- Hairline borders (`{colors.hairline}`) and tonal surface steps replace heavy blurred shadows.
- Dedicated distinct skeletons across the 4 screens (hero+list Library, single-column stepper Wizard, two-zone Workspace tree, full-bleed Reader).

## Colors

> Source: `design/tokens.json` (`primitives`, `semantic`, `modes/light`, `modes/dark`, `modes/sepia`) & Penpot file `29883df9-fd36-8190-8008-feabca1cbdab`.

### Brand & Accent
| Penpot Token | Semantic Name | Light Hex | Dark Hex | Sepia Hex |
|---|---|---|---|---|
| `color.action.primary.bg` | `{colors.primary}` | `#1E3A5F` | `#9EC2F0` | `#6F4423` |
| `color.action.primary.text` | `{colors.on-primary}` | `#FAF8F5` | `#0E2340` | `#F4ECD8` |
| `color.action.primaryContainer.bg` | `{colors.primary-container}` | `#D6E3F4` | `#1E3A5F` | `#E4D7BA` |
| `color.accent.secondary.bg` | `{colors.secondary}` | `#9A4A2E` | `#E8A389` | `#9A4A2E` |
| `color.accent.tertiary.bg` | `{colors.tertiary}` | `#2C6B6E` | `#7FC8CB` | `#2C6B6E` |

### Surface
- Canvas `{colors.canvas}` (`#FAF8F5` Light / `#131417` Dark / `{colors.sepia-canvas}` `#F4ECD8` Sepia)
- Low/Card `{colors.surface-1}` (`#F3F0EA` Light / `#22252B` Dark / `{colors.sepia-surface-low}` `#EFE5CE` Sepia)
- High/Search Strip `{colors.surface-2}` (`#EAE5DC` Light / `#2B2E36` Dark / `{colors.sepia-surface-high}` `#E4D7BA` Sepia)

### Text
- Primary body text `{colors.ink}` (`#1A1917` Light / `#ECE9E2` Dark / `{colors.sepia-ink}` `#3B2F23` Sepia)
- Muted metadata `{colors.ink-muted}` (`#5E5A54` Light / `#B4AFA6` Dark / `{colors.sepia-ink-muted}` `#6E604F` Sepia)

### Semantic
- Danger `#B3261E` (Dark `#F2B8B5`), Warning `#9C5B00` (Dark `#F3C884`), Success `#1E6E3E` (Dark `#8CD6A8`).

## Typography

### Font Family
- Studio UI & Lists: `{typography.body-large.fontFamily}` (`IBM Plex Sans, Noto Sans JP`), falling back cleanly to platform SansSerif on Android builds without custom font TTFs.
- Reader Serif Option: `{typography.reader-serif.fontFamily}` (`Shippori Mincho, Noto Serif JP`) via `FontFamily.Serif`.
- Technical Code Blocks: `{typography.code-mono.fontFamily}` (`JetBrains Mono`) via `FontFamily.Monospace`.

### Hierarchy
- `headline-large` (`32px`, 700) -> Book cover titles in Reader
- `title-large` (`23px`, 700) -> TopAppBar brand & workspace headings
- `title-medium` (`19px`, 600) -> Chapter titles & Hero card headline
- `body-large` (`16px`, 400, 1.65-1.75 LH) -> Long-form reading prose
- `body-medium` (`14px`, 400) -> Cards, wizard inputs, section outlines
- `label-medium` (`12px`, 500) -> Filter chips, section counters
- `label-small` (`11px`, 500) -> `4px`-rounded Genre tags and status badges

### Principles
- Pair tabular numerals (`tnum`) with chapter counts, character targets, and reading estimates so progress rows never jitter vertically/horizontally while streaming.

## Layout

### Spacing System
- Base unit: `4dp`. Common tokens: inset `{spacing.lg}` (`16px`), card stack `{spacing.md}` (`12px`), section gap `{spacing.xxl}` (`24px`).

### Grid & Container
- Compact phones (`412 × 915 dp` canonical Penpot board): full-width column with `16dp` outer horizontal inset.
- Tablet / Reader: prose canvas constrained to `640dp` max width and horizontally centered.

### Whitespace Philosophy
- Lists use open hairline dividers rather than thick boxed cards everywhere; only the primary Hero card, Wizard configuration groups, Pipeline status header, and Reader overlays sit in filled containers.

## Elevation & Depth
- Flat tonal surface ladder separated by `1dp` `{colors.hairline}` borders (`#DED8CE` Light / `#343842` Dark / `#D7C8AA` Sepia). Drop shadows are omitted on cards; Material 3 ModalBottomSheets and ExtendedFAB rely on tonal contrast.

## Shapes

### Border Radius Scale
- `{rounded.xs}` (`4dp`): Genre badges and status pills.
- `{rounded.sm}` (`8dp`): Buttons, form inputs, segmented controls.
- `{rounded.md}` (`12dp`): Hero cards, chapter cards, live stream console.
- `{rounded.lg}` (`16dp`): Primary `ExtendedFloatingActionButton`.
- `{rounded.xl}` (`24dp`): BottomSheets (`AppearanceSheet`, `ExportSheet`, `RegenerateSheet`).
- `{rounded.pill}` (`9999dp`): Search bar and FilterChips.

## Components

### Cards & Containers
- **`hero-resume-card`** — Highlighted active/incomplete book card in Library with status warning chip and single `{colors.primary}` action button.
- **`live-stream-drawer`** — Collapsible `#1B1D22` monospace drawer showing real-time AI token streaming in Workspace.

### Buttons & Actions
- **`extended-fab-primary`** — `16dp`-radius rounded square FAB anchored above navigation insets with `{colors.primary}` fill and `{colors.on-primary}` label.

### Metadata & Filters
- **`genre-badge`** — `4dp`-radius badge inside `{colors.primary-container}`.
- **`filter-chip`** — Pill-shaped toggle chips (`すべて`, `執筆中`, `要再開`, `完了`).

## Do's and Don'ts

### Do
- Keep one primary indigo CTA button per visible viewport zone.
- Highlight empty/missing legacy or AI sections clearly using terracotta `{colors.secondary}` warning chips so users can resume reliably.
- Constrain Reader measure to `640dp` max width on wide displays.

### Don't
- Never use neon/violet AI gradients or sparkle emoji icons.
- Never place pure `#FFFFFF` cards on pure `#000000` backgrounds.
- Never collapse section granularity during editing (chapter trees keep per-section controls accessible).

## Responsive Behavior
- Single-column scrollable stacks on Compact width (`< 600dp`); centered `640dp` reading column and wider form margins on Medium/Expanded tablets.

## Iteration Guide
1. Always map Compose colors through `MaterialTheme.colorScheme` plus `BookGenTheme.extendedColors` (`borderSubtle`, `warning`, `success`, `codeContainer`).
2. Keep custom M3 radii aligned with `BookGenShapes` (`4dp`, `8dp`, `12dp`, `16dp`, `24dp`).
3. Reserve `{colors.primary}` for primary navigation/generation CTAs and `{colors.tertiary}` for wavy generation progress.

## Known Gaps
- At extraction time during Phase 4, the browser suspended the live Penpot tab plugin (`no heartbeat for 40s`), so token hexes and screen structures were cross-verified directly against `design/tokens.json` and `design/penpot-mcp-blueprint.md` (which match the 4 Penpot boards generated and visually verified in Phase 1).
- Bundled custom `.ttf` font files (`IBM Plex Sans`, `Shippori Mincho`, `JetBrains Mono`) are not yet committed as raw APK assets to keep APK size compact; Android platform `SansSerif` / `Serif` / `Monospace` system families act as documented runtime substitutes.
