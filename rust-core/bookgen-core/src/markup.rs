use crate::models::MarkupFormat;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AdmonitionKind {
    Note,
    Tip,
    Important,
    Warning,
    Caution,
}

impl AdmonitionKind {
    pub fn label(self) -> &'static str {
        match self {
            Self::Note => "NOTE",
            Self::Tip => "TIP",
            Self::Important => "IMPORTANT",
            Self::Warning => "WARNING",
            Self::Caution => "CAUTION",
        }
    }
}

/// Native structured AST block for Compose rendering and offline format export.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum DocumentBlock {
    Heading {
        level: u8,
        text: String,
    },
    Paragraph {
        text: String,
    },
    CodeBlock {
        language: Option<String>,
        code: String,
    },
    Table {
        headers: Vec<String>,
        rows: Vec<Vec<String>>,
    },
    Admonition {
        kind: AdmonitionKind,
        title: Option<String>,
        body: String,
    },
    MathBlock {
        latex: String,
    },
    BulletList {
        items: Vec<String>,
    },
    NumberedList {
        items: Vec<String>,
    },
    Quote {
        text: String,
        attribution: Option<String>,
    },
    Divider,
}

/// Sanitizes LLM section output while preserving legitimate inner fenced code blocks.
/// Fixes the legacy PHP `api.php` bug where greedy regexes corrupted inner code fences.
#[uniffi::export]
pub fn sanitize_section_output(raw: String, _format: MarkupFormat) -> String {
    let cleaned = raw
        .trim_start_matches('\u{feff}')
        .replace('\0', "")
        .replace("\r\n", "\n")
        .replace('\r', "\n");

    let mut lines: Vec<&str> = cleaned.lines().collect();
    while lines.first().is_some_and(|l| l.trim().is_empty()) {
        lines.remove(0);
    }
    while lines.last().is_some_and(|l| l.trim().is_empty()) {
        lines.pop();
    }

    if let Some(stripped) = try_unwrap_outer_wrapper_fence(&lines) {
        lines = stripped;
    }

    while lines.first().is_some_and(|l| l.trim().is_empty()) {
        lines.remove(0);
    }
    while lines.last().is_some_and(|l| l.trim().is_empty()) {
        lines.pop();
    }

    let mut collapsed = Vec::with_capacity(lines.len());
    let mut consecutive_empty = 0u32;
    for line in lines {
        if line.trim().is_empty() {
            consecutive_empty = consecutive_empty.saturating_add(1);
            if consecutive_empty <= 1 {
                collapsed.push("");
            }
        } else {
            consecutive_empty = 0;
            collapsed.push(line.trim_end());
        }
    }

    collapsed.join("\n")
}

/// Heuristically detects whether text uses AsciiDoc or Markdown conventions.
#[uniffi::export]
pub fn detect_markup_format_for_text(content: String) -> MarkupFormat {
    for line in content.lines() {
        let t = line.trim_start();
        if t.starts_with("== ")
            || t.starts_with("=== ")
            || t.starts_with("==== ")
            || t == "----"
            || t.starts_with("[source,")
            || t.starts_with("[NOTE]")
            || t.starts_with("[TIP]")
            || t.starts_with("[WARNING]")
            || t.starts_with("|===")
        {
            return MarkupFormat::AsciiDoc;
        }
        if t.starts_with("## ") || t.starts_with("### ") || t.starts_with("```") {
            return MarkupFormat::Markdown;
        }
    }
    MarkupFormat::Markdown
}

/// Parses AsciiDoc or Markdown text into structured AST blocks cleanly without panics.
#[uniffi::export]
pub fn parse_markup_blocks(content: String, format: MarkupFormat) -> Vec<DocumentBlock> {
    let normalized = sanitize_section_output(content, format);
    if normalized.is_empty() {
        return Vec::new();
    }
    let lines: Vec<&str> = normalized.lines().collect();
    match format {
        MarkupFormat::Markdown => parse_markdown(&lines),
        MarkupFormat::AsciiDoc => parse_asciidoc(&lines),
    }
}

/// Renders structured AST blocks into normalized Markdown.
pub(crate) fn blocks_to_markdown(blocks: &[DocumentBlock]) -> String {
    let mut out = Vec::new();
    for block in blocks {
        match block {
            DocumentBlock::Heading { level, text } => {
                let hashes = "#".repeat(usize::from((*level).clamp(1, 6)));
                out.push(format!("{hashes} {text}"));
            }
            DocumentBlock::Paragraph { text } => out.push(text.clone()),
            DocumentBlock::CodeBlock { language, code } => {
                let lang = language.as_deref().unwrap_or("");
                out.push(format!("```{lang}\n{code}\n```"));
            }
            DocumentBlock::Table { headers, rows } => {
                out.push(render_markdown_table(headers, rows));
            }
            DocumentBlock::Admonition { kind, title, body } => {
                let mut lines = vec![format!("> [!{}]", kind.label())];
                if let Some(t) = title.as_deref().filter(|s| !s.trim().is_empty()) {
                    lines.push(format!("> **{t}**"));
                }
                for l in body.lines() {
                    lines.push(format!("> {l}"));
                }
                out.push(lines.join("\n"));
            }
            DocumentBlock::MathBlock { latex } => {
                out.push(format!("$$\n{latex}\n$$"));
            }
            DocumentBlock::BulletList { items } => {
                out.push(
                    items
                        .iter()
                        .map(|i| format!("- {i}"))
                        .collect::<Vec<_>>()
                        .join("\n"),
                );
            }
            DocumentBlock::NumberedList { items } => {
                out.push(
                    items
                        .iter()
                        .enumerate()
                        .map(|(idx, i)| format!("{}. {i}", idx.saturating_add(1)))
                        .collect::<Vec<_>>()
                        .join("\n"),
                );
            }
            DocumentBlock::Quote { text, attribution } => {
                let mut lines: Vec<String> = text.lines().map(|l| format!("> {l}")).collect();
                if let Some(attr) = attribution.as_deref().filter(|s| !s.trim().is_empty()) {
                    lines.push(format!("> — {attr}"));
                }
                out.push(lines.join("\n"));
            }
            DocumentBlock::Divider => out.push("---".to_string()),
        }
    }
    out.join("\n\n")
}

/// Renders structured AST blocks into normalized AsciiDoc.
pub(crate) fn blocks_to_asciidoc(blocks: &[DocumentBlock]) -> String {
    let mut out = Vec::new();
    for block in blocks {
        match block {
            DocumentBlock::Heading { level, text } => {
                let eqs = "=".repeat(usize::from((*level).clamp(1, 6)));
                out.push(format!("{eqs} {text}"));
            }
            DocumentBlock::Paragraph { text } => out.push(text.clone()),
            DocumentBlock::CodeBlock { language, code } => {
                let header = match language.as_deref().filter(|s| !s.trim().is_empty()) {
                    Some(lang) => format!("[source,{lang}]\n"),
                    None => String::new(),
                };
                out.push(format!("{header}----\n{code}\n----"));
            }
            DocumentBlock::Table { headers, rows } => {
                out.push(render_asciidoc_table(headers, rows));
            }
            DocumentBlock::Admonition { kind, title, body } => {
                let title_line = match title.as_deref().filter(|s| !s.trim().is_empty()) {
                    Some(t) => format!(".{t}\n"),
                    None => String::new(),
                };
                out.push(format!(
                    "[{}]\n{title_line}====\n{body}\n====",
                    kind.label()
                ));
            }
            DocumentBlock::MathBlock { latex } => {
                out.push(format!("[stem]\n++++\n{latex}\n++++"));
            }
            DocumentBlock::BulletList { items } => {
                out.push(
                    items
                        .iter()
                        .map(|i| format!("* {i}"))
                        .collect::<Vec<_>>()
                        .join("\n"),
                );
            }
            DocumentBlock::NumberedList { items } => {
                out.push(
                    items
                        .iter()
                        .map(|i| format!(". {i}"))
                        .collect::<Vec<_>>()
                        .join("\n"),
                );
            }
            DocumentBlock::Quote { text, attribution } => {
                let header = match attribution.as_deref().filter(|s| !s.trim().is_empty()) {
                    Some(attr) => format!("[quote, {attr}]\n"),
                    None => "[quote]\n".to_string(),
                };
                out.push(format!("{header}____\n{text}\n____"));
            }
            DocumentBlock::Divider => out.push("'''".to_string()),
        }
    }
    out.join("\n\n")
}

fn try_unwrap_outer_wrapper_fence<'a>(lines: &[&'a str]) -> Option<Vec<&'a str>> {
    if lines.len() < 2 {
        return None;
    }
    let first = lines.first()?.trim();
    let last = lines.last()?.trim();
    let opener_prefix = if first.starts_with("```") {
        "```"
    } else {
        return None;
    };

    let tag = first
        .strip_prefix(opener_prefix)?
        .trim()
        .to_ascii_lowercase();
    let is_doc_wrapper = matches!(
        tag.as_str(),
        "" | "markdown" | "md" | "asciidoc" | "adoc" | "txt" | "text"
    );
    if !is_doc_wrapper || last != opener_prefix {
        return None;
    }

    // Only unwrap if inner content never closes and reopens at outer fence level.
    let inner = lines.get(1..lines.len().saturating_sub(1))?;
    let mut in_nested_fence = false;
    let mut nested_fence_char: Option<&str> = None;
    for line in inner {
        let trimmed = line.trim();
        if let Some(active) = nested_fence_char {
            if trimmed == active
                || trimmed.starts_with(active)
                    && trimmed
                        .chars()
                        .all(|c| c == active.chars().next().unwrap_or('`'))
            {
                in_nested_fence = false;
                nested_fence_char = None;
            }
        } else if trimmed.starts_with("```") {
            let lang = trimmed.trim_start_matches('`').trim();
            if lang.is_empty() {
                // Unpaired bare closing ``` inside body means outer fence already closed early!
                return None;
            }
            in_nested_fence = true;
            nested_fence_char = Some("```");
        } else if trimmed.starts_with("~~~") {
            in_nested_fence = true;
            nested_fence_char = Some("~~~");
        }
    }

    if in_nested_fence {
        None
    } else {
        Some(inner.to_vec())
    }
}

fn parse_markdown(lines: &[&str]) -> Vec<DocumentBlock> {
    let mut blocks = Vec::new();
    let mut i = 0;

    while i < lines.len() {
        let Some(&line) = lines.get(i) else {
            break;
        };
        let trimmed = line.trim();
        if trimmed.is_empty() {
            i += 1;
            continue;
        }

        // Fenced code blocks (``` or ~~~)
        if let Some((fence_char, fence_len, lang)) = parse_md_fence_open(trimmed) {
            let mut code_lines = Vec::new();
            i += 1;
            while i < lines.len() {
                let Some(&inner) = lines.get(i) else {
                    break;
                };
                if is_md_fence_close(inner.trim(), fence_char, fence_len) {
                    i += 1;
                    break;
                }
                code_lines.push(inner);
                i += 1;
            }
            blocks.push(DocumentBlock::CodeBlock {
                language: (!lang.is_empty()).then_some(lang),
                code: code_lines.join("\n"),
            });
            continue;
        }

        // Display math block ($$)
        if let Some(rest) = trimmed.strip_prefix("$$") {
            if let Some(inline_end) = rest.strip_suffix("$$").filter(|_| !rest.is_empty()) {
                blocks.push(DocumentBlock::MathBlock {
                    latex: inline_end.trim().to_string(),
                });
                i += 1;
                continue;
            }
            let mut math_lines = Vec::new();
            if !rest.trim().is_empty() {
                math_lines.push(rest.trim());
            }
            i += 1;
            while i < lines.len() {
                let Some(&inner) = lines.get(i) else {
                    break;
                };
                if inner.trim() == "$$" {
                    i += 1;
                    break;
                }
                math_lines.push(inner.trim_end());
                i += 1;
            }
            blocks.push(DocumentBlock::MathBlock {
                latex: math_lines.join("\n"),
            });
            continue;
        }

        // Divider
        if is_horizontal_rule(trimmed, &['-', '*', '_']) {
            blocks.push(DocumentBlock::Divider);
            i += 1;
            continue;
        }

        // Headings (# .. ######)
        if let Some((level, heading_text)) = parse_atx_heading(trimmed) {
            blocks.push(DocumentBlock::Heading {
                level,
                text: heading_text,
            });
            i += 1;
            continue;
        }

        // GFM Table
        if trimmed.contains('|')
            && lines
                .get(i.saturating_add(1))
                .is_some_and(|next| is_md_table_sep(next.trim()))
        {
            let headers = split_pipe_cells(trimmed);
            i += 2;
            let mut rows = Vec::new();
            while i < lines.len() {
                let Some(&row_line) = lines.get(i) else {
                    break;
                };
                let rt = row_line.trim();
                if rt.is_empty() || !rt.contains('|') {
                    break;
                }
                rows.push(split_pipe_cells(rt));
                i += 1;
            }
            blocks.push(DocumentBlock::Table { headers, rows });
            continue;
        }

        // Blockquotes / GitHub Admonitions (> [!NOTE])
        if trimmed.starts_with('>') {
            let mut quote_lines = Vec::new();
            while i < lines.len() {
                let Some(&qline) = lines.get(i) else {
                    break;
                };
                let qt = qline.trim_start();
                if !qt.starts_with('>') {
                    break;
                }
                let stripped = qt.strip_prefix('>').unwrap_or("").trim_start();
                quote_lines.push(stripped);
                i += 1;
            }
            if let Some(first) = quote_lines.first() {
                if let Some(kind) = parse_md_admonition_tag(first.trim()) {
                    let body = quote_lines
                        .iter()
                        .skip(1)
                        .copied()
                        .collect::<Vec<_>>()
                        .join("\n");
                    blocks.push(DocumentBlock::Admonition {
                        kind,
                        title: None,
                        body,
                    });
                    continue;
                }
            }
            blocks.push(DocumentBlock::Quote {
                text: quote_lines.join("\n"),
                attribution: None,
            });
            continue;
        }

        // Unordered list
        if parse_md_bullet_line(line.trim_start()).is_some() {
            let mut items = Vec::new();
            while i < lines.len() {
                let Some(&uline) = lines.get(i) else {
                    break;
                };
                let Some(item) = parse_md_bullet_line(uline.trim_start()) else {
                    break;
                };
                items.push(item.to_string());
                i += 1;
            }
            blocks.push(DocumentBlock::BulletList { items });
            continue;
        }

        // Ordered list
        if parse_numbered_line(line.trim_start()).is_some() {
            let mut items = Vec::new();
            while i < lines.len() {
                let Some(&oline) = lines.get(i) else {
                    break;
                };
                let Some(item) = parse_numbered_line(oline.trim_start()) else {
                    break;
                };
                items.push(item.to_string());
                i += 1;
            }
            blocks.push(DocumentBlock::NumberedList { items });
            continue;
        }

        // Paragraph
        let mut para = Vec::new();
        while i < lines.len() {
            let Some(&pline) = lines.get(i) else {
                break;
            };
            let pt = pline.trim();
            if pt.is_empty()
                || parse_atx_heading(pt).is_some()
                || parse_md_fence_open(pt).is_some()
                || pt.starts_with("$$")
                || pt.starts_with('>')
                || is_horizontal_rule(pt, &['-', '*', '_'])
                || parse_md_bullet_line(pt).is_some()
                || parse_numbered_line(pt).is_some()
            {
                break;
            }
            para.push(pt);
            i += 1;
        }
        if !para.is_empty() {
            blocks.push(DocumentBlock::Paragraph {
                text: para.join(" "),
            });
        }
    }

    blocks
}

fn parse_asciidoc(lines: &[&str]) -> Vec<DocumentBlock> {
    let mut blocks = Vec::new();
    let mut i = 0;

    while i < lines.len() {
        let Some(&line) = lines.get(i) else {
            break;
        };
        let trimmed = line.trim();
        if trimmed.is_empty() {
            i += 1;
            continue;
        }

        // Attribute line e.g. :toc:
        if trimmed.starts_with(':') && trimmed.ends_with(':') && trimmed.len() >= 3 {
            i += 1;
            continue;
        }

        // [source,lang] + ---- code block
        if let Some(lang_opt) = parse_asciidoc_source_attr(trimmed) {
            i += 1;
            while i < lines.len() && lines.get(i).is_some_and(|l| l.trim().is_empty()) {
                i += 1;
            }
            if lines.get(i).is_some_and(|l| l.trim() == "----") {
                i += 1;
                let mut code_lines = Vec::new();
                while i < lines.len() {
                    let Some(&inner) = lines.get(i) else {
                        break;
                    };
                    if inner.trim() == "----" {
                        i += 1;
                        break;
                    }
                    code_lines.push(inner);
                    i += 1;
                }
                blocks.push(DocumentBlock::CodeBlock {
                    language: lang_opt,
                    code: code_lines.join("\n"),
                });
                continue;
            }
        }

        // Bare ---- listing block
        if trimmed == "----" {
            i += 1;
            let mut code_lines = Vec::new();
            while i < lines.len() {
                let Some(&inner) = lines.get(i) else {
                    break;
                };
                if inner.trim() == "----" {
                    i += 1;
                    break;
                }
                code_lines.push(inner);
                i += 1;
            }
            blocks.push(DocumentBlock::CodeBlock {
                language: None,
                code: code_lines.join("\n"),
            });
            continue;
        }

        // Math block: [stem] + ++++ ... ++++
        if trimmed == "[stem]" || trimmed == "[latexmath]" || trimmed == "++++" {
            if trimmed != "++++" {
                i += 1;
            }
            if lines.get(i).is_some_and(|l| l.trim() == "++++") {
                i += 1;
                let mut math_lines = Vec::new();
                while i < lines.len() {
                    let Some(&inner) = lines.get(i) else {
                        break;
                    };
                    if inner.trim() == "++++" {
                        i += 1;
                        break;
                    }
                    math_lines.push(inner);
                    i += 1;
                }
                blocks.push(DocumentBlock::MathBlock {
                    latex: math_lines.join("\n"),
                });
                continue;
            }
        }

        // Admonition block: [NOTE] + optional .Title + ==== ... ==== (or inline NOTE: text)
        if let Some(kind) = parse_asciidoc_admonition_attr(trimmed) {
            i += 1;
            let mut title = None;
            if let Some(&maybe_title) = lines.get(i) {
                let mt = maybe_title.trim();
                if let Some(stripped) = mt
                    .strip_prefix('.')
                    .filter(|s| !s.is_empty() && !s.starts_with(' '))
                {
                    title = Some(stripped.to_string());
                    i += 1;
                }
            }
            if lines.get(i).is_some_and(|l| l.trim() == "====") {
                i += 1;
                let mut body_lines = Vec::new();
                while i < lines.len() {
                    let Some(&inner) = lines.get(i) else {
                        break;
                    };
                    if inner.trim() == "====" {
                        i += 1;
                        break;
                    }
                    body_lines.push(inner.trim_end());
                    i += 1;
                }
                blocks.push(DocumentBlock::Admonition {
                    kind,
                    title,
                    body: body_lines.join("\n"),
                });
                continue;
            } else if let Some(&next_line) = lines.get(i) {
                blocks.push(DocumentBlock::Admonition {
                    kind,
                    title,
                    body: next_line.trim().to_string(),
                });
                i += 1;
                continue;
            }
        }
        if let Some((kind, inline_body)) = parse_asciidoc_inline_admonition(trimmed) {
            blocks.push(DocumentBlock::Admonition {
                kind,
                title: None,
                body: inline_body,
            });
            i += 1;
            continue;
        }

        // Quote block: [quote, Author] + ____ ... ____
        if trimmed.starts_with("[quote") && trimmed.ends_with(']') {
            let attr = trimmed
                .strip_prefix("[quote")
                .and_then(|s| s.strip_suffix(']'))
                .and_then(|s| s.split(',').nth(1))
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty());
            i += 1;
            if lines.get(i).is_some_and(|l| l.trim() == "____") {
                i += 1;
                let mut q_lines = Vec::new();
                while i < lines.len() {
                    let Some(&inner) = lines.get(i) else {
                        break;
                    };
                    if inner.trim() == "____" {
                        i += 1;
                        break;
                    }
                    q_lines.push(inner.trim_end());
                    i += 1;
                }
                blocks.push(DocumentBlock::Quote {
                    text: q_lines.join("\n"),
                    attribution: attr,
                });
                continue;
            }
        }

        // AsciiDoc table |=== ... |===
        if trimmed.starts_with("[cols=") || trimmed.starts_with("[%header") {
            i += 1;
        }
        if lines.get(i).is_some_and(|l| l.trim() == "|===") {
            i += 1;
            let mut raw_rows: Vec<Vec<String>> = Vec::new();
            while i < lines.len() {
                let Some(&inner) = lines.get(i) else {
                    break;
                };
                let it = inner.trim();
                if it == "|===" {
                    i += 1;
                    break;
                }
                if !it.is_empty() {
                    let cells: Vec<String> = it
                        .split('|')
                        .skip(if it.starts_with('|') { 1 } else { 0 })
                        .map(|c| c.trim().to_string())
                        .filter(|c| !c.is_empty())
                        .collect();
                    if !cells.is_empty() {
                        raw_rows.push(cells);
                    }
                }
                i += 1;
            }
            if !raw_rows.is_empty() {
                let headers = raw_rows.remove(0);
                blocks.push(DocumentBlock::Table {
                    headers,
                    rows: raw_rows,
                });
            }
            continue;
        }

        // Divider '''
        if trimmed == "'''" {
            blocks.push(DocumentBlock::Divider);
            i += 1;
            continue;
        }

        // Headings (= .. ======)
        if let Some((level, heading_text)) = parse_asciidoc_heading(trimmed) {
            blocks.push(DocumentBlock::Heading {
                level,
                text: heading_text,
            });
            i += 1;
            continue;
        }

        // Bullet list (* item or ** item or - item)
        if parse_asciidoc_bullet(trimmed).is_some() {
            let mut items = Vec::new();
            while i < lines.len() {
                let Some(&uline) = lines.get(i) else {
                    break;
                };
                let Some(item) = parse_asciidoc_bullet(uline.trim()) else {
                    break;
                };
                items.push(item.to_string());
                i += 1;
            }
            blocks.push(DocumentBlock::BulletList { items });
            continue;
        }

        // Ordered list (. item)
        if parse_asciidoc_numbered(trimmed).is_some() {
            let mut items = Vec::new();
            while i < lines.len() {
                let Some(&oline) = lines.get(i) else {
                    break;
                };
                let Some(item) = parse_asciidoc_numbered(oline.trim()) else {
                    break;
                };
                items.push(item.to_string());
                i += 1;
            }
            blocks.push(DocumentBlock::NumberedList { items });
            continue;
        }

        // Paragraph
        let mut para = Vec::new();
        while i < lines.len() {
            let Some(&pline) = lines.get(i) else {
                break;
            };
            let pt = pline.trim();
            if pt.is_empty()
                || parse_asciidoc_heading(pt).is_some()
                || pt == "----"
                || pt == "|==="
                || pt == "'''"
                || parse_asciidoc_source_attr(pt).is_some()
                || parse_asciidoc_admonition_attr(pt).is_some()
                || parse_asciidoc_inline_admonition(pt).is_some()
                || parse_asciidoc_bullet(pt).is_some()
                || parse_asciidoc_numbered(pt).is_some()
            {
                break;
            }
            para.push(pt);
            i += 1;
        }
        if !para.is_empty() {
            blocks.push(DocumentBlock::Paragraph {
                text: para.join(" "),
            });
        }
    }

    blocks
}

fn parse_md_fence_open(trimmed: &str) -> Option<(char, usize, String)> {
    let first = trimmed.chars().next()?;
    if first != '`' && first != '~' {
        return None;
    }
    let count = trimmed.chars().take_while(|&c| c == first).count();
    if count < 3 {
        return None;
    }
    let lang = trimmed
        .chars()
        .skip(count)
        .collect::<String>()
        .trim()
        .to_string();
    Some((first, count, lang))
}

fn is_md_fence_close(trimmed: &str, ch: char, min_len: usize) -> bool {
    let count = trimmed.chars().take_while(|&c| c == ch).count();
    count >= min_len && trimmed.chars().all(|c| c == ch)
}

fn parse_atx_heading(trimmed: &str) -> Option<(u8, String)> {
    let count = trimmed.chars().take_while(|&c| c == '#').count();
    if !(1..=6).contains(&count) {
        return None;
    }
    let rest = trimmed.get(count..)?;
    let stripped = rest.strip_prefix(' ')?;
    let text = stripped.trim().trim_end_matches('#').trim();
    if text.is_empty() {
        None
    } else {
        Some((u8::try_from(count).unwrap_or(1), text.to_string()))
    }
}

fn parse_asciidoc_heading(trimmed: &str) -> Option<(u8, String)> {
    let count = trimmed.chars().take_while(|&c| c == '=').count();
    if !(1..=6).contains(&count) {
        return None;
    }
    let rest = trimmed.get(count..)?;
    let stripped = rest.strip_prefix(' ')?;
    let text = stripped.trim();
    if text.is_empty() {
        None
    } else {
        Some((u8::try_from(count).unwrap_or(1), text.to_string()))
    }
}

fn is_horizontal_rule(trimmed: &str, allowed: &[char]) -> bool {
    if trimmed.len() < 3 {
        return false;
    }
    let mut marker = None;
    let mut count = 0usize;
    for ch in trimmed.chars() {
        if ch == ' ' {
            continue;
        }
        if !allowed.contains(&ch) {
            return false;
        }
        match marker {
            None => marker = Some(ch),
            Some(m) if m != ch => return false,
            _ => {}
        }
        count = count.saturating_add(1);
    }
    count >= 3
}

fn is_md_table_sep(trimmed: &str) -> bool {
    trimmed.contains('-')
        && trimmed.contains('|')
        && trimmed
            .chars()
            .all(|c| c == '|' || c == '-' || c == ':' || c == ' ')
}

fn split_pipe_cells(trimmed: &str) -> Vec<String> {
    let s = trimmed.strip_prefix('|').unwrap_or(trimmed);
    let s = s.strip_suffix('|').unwrap_or(s);
    s.split('|').map(|c| c.trim().to_string()).collect()
}

fn parse_md_admonition_tag(first_line: &str) -> Option<AdmonitionKind> {
    match first_line.to_ascii_uppercase().as_str() {
        "[!NOTE]" => Some(AdmonitionKind::Note),
        "[!TIP]" => Some(AdmonitionKind::Tip),
        "[!IMPORTANT]" => Some(AdmonitionKind::Important),
        "[!WARNING]" => Some(AdmonitionKind::Warning),
        "[!CAUTION]" => Some(AdmonitionKind::Caution),
        _ => None,
    }
}

fn parse_md_bullet_line(trimmed: &str) -> Option<&str> {
    trimmed
        .strip_prefix("- ")
        .or_else(|| trimmed.strip_prefix("* "))
        .map(str::trim)
}

fn parse_numbered_line(trimmed: &str) -> Option<&str> {
    let dot_pos = trimmed.find(". ")?;
    let digits = trimmed.get(..dot_pos)?;
    if !digits.is_empty() && digits.chars().all(|c| c.is_ascii_digit()) {
        trimmed.get(dot_pos.saturating_add(2)..).map(str::trim)
    } else {
        None
    }
}

fn parse_asciidoc_source_attr(trimmed: &str) -> Option<Option<String>> {
    if !trimmed.starts_with("[source") || !trimmed.ends_with(']') {
        return None;
    }
    let inner = trimmed.strip_prefix("[source")?.strip_suffix(']')?;
    let lang = inner
        .strip_prefix(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty());
    Some(lang)
}

fn parse_asciidoc_admonition_attr(trimmed: &str) -> Option<AdmonitionKind> {
    match trimmed {
        "[NOTE]" => Some(AdmonitionKind::Note),
        "[TIP]" => Some(AdmonitionKind::Tip),
        "[IMPORTANT]" => Some(AdmonitionKind::Important),
        "[WARNING]" => Some(AdmonitionKind::Warning),
        "[CAUTION]" => Some(AdmonitionKind::Caution),
        _ => None,
    }
}

fn parse_asciidoc_inline_admonition(trimmed: &str) -> Option<(AdmonitionKind, String)> {
    let pairs = [
        ("NOTE:", AdmonitionKind::Note),
        ("TIP:", AdmonitionKind::Tip),
        ("IMPORTANT:", AdmonitionKind::Important),
        ("WARNING:", AdmonitionKind::Warning),
        ("CAUTION:", AdmonitionKind::Caution),
    ];
    for (prefix, kind) in pairs {
        if let Some(rest) = trimmed.strip_prefix(prefix) {
            return Some((kind, rest.trim().to_string()));
        }
    }
    None
}

fn parse_asciidoc_bullet(trimmed: &str) -> Option<&str> {
    let stars = trimmed.chars().take_while(|&c| c == '*').count();
    if stars >= 1 {
        if let Some(rest) = trimmed.get(stars..).and_then(|r| r.strip_prefix(' ')) {
            return Some(rest.trim());
        }
    }
    trimmed.strip_prefix("- ").map(str::trim)
}

fn parse_asciidoc_numbered(trimmed: &str) -> Option<&str> {
    let dots = trimmed.chars().take_while(|&c| c == '.').count();
    if dots >= 1 {
        trimmed
            .get(dots..)
            .and_then(|r| r.strip_prefix(' '))
            .map(str::trim)
    } else {
        None
    }
}

fn render_markdown_table(headers: &[String], rows: &[Vec<String>]) -> String {
    let mut lines = Vec::new();
    lines.push(format!("| {} |", headers.join(" | ")));
    let sep = headers
        .iter()
        .map(|_| "---")
        .collect::<Vec<_>>()
        .join(" | ");
    lines.push(format!("| {sep} |"));
    for row in rows {
        lines.push(format!("| {} |", row.join(" | ")));
    }
    lines.join("\n")
}

fn render_asciidoc_table(headers: &[String], rows: &[Vec<String>]) -> String {
    let mut lines = vec![
        format!("[cols=\"{}\",options=\"header\"]", headers.len().max(1)),
        "|===".to_string(),
    ];
    lines.push(format!("| {}", headers.join(" | ")));
    for row in rows {
        lines.push(format!("| {}", row.join(" | ")));
    }
    lines.push("|===".to_string());
    lines.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sanitizer_strips_outer_wrapper_without_breaking_inner_code_blocks() {
        let wrapped = "```markdown\n## Intro\n\n```rust\nfn main() {}\n```\n\nDone.\n```";
        let sanitized = sanitize_section_output(wrapped.to_string(), MarkupFormat::Markdown);
        assert_eq!(sanitized, "## Intro\n\n```rust\nfn main() {}\n```\n\nDone.");

        // Document that intentionally begins and ends with separate code fences must remain intact
        let valid_pair = "```rust\nlet a = 1;\n```\n\nBetween\n\n```kotlin\nval b = 2\n```";
        assert_eq!(
            sanitize_section_output(valid_pair.to_string(), MarkupFormat::Markdown),
            valid_pair
        );
    }

    #[test]
    fn parses_markdown_and_asciidoc_ast_blocks() {
        let md = "## Architecture\n\nText body.\n\n```rust\nlet x = 1;\n```\n\n> [!NOTE]\n> Important note.\n\n| Key | Value |\n| --- | --- |\n| A | 1 |\n\n$$\nE = mc^2\n$$";
        let md_blocks = parse_markup_blocks(md.to_string(), MarkupFormat::Markdown);
        assert!(md_blocks.iter().any(
            |b| matches!(b, DocumentBlock::CodeBlock { language: Some(l), .. } if l == "rust")
        ));
        assert!(md_blocks.iter().any(|b| matches!(
            b,
            DocumentBlock::Admonition {
                kind: AdmonitionKind::Note,
                ..
            }
        )));
        assert!(md_blocks
            .iter()
            .any(|b| matches!(b, DocumentBlock::Table { headers, .. } if headers.len() == 2)));
        assert!(md_blocks
            .iter()
            .any(|b| matches!(b, DocumentBlock::MathBlock { .. })));

        let adoc = "=== Sec\n\n[source,kotlin]\n----\nfun hi() {}\n----\n\n[WARNING]\n====\nWatch out.\n====\n\n. First step\n. Second step";
        let adoc_blocks = parse_markup_blocks(adoc.to_string(), MarkupFormat::AsciiDoc);
        assert!(adoc_blocks
            .iter()
            .any(|b| matches!(b, DocumentBlock::Heading { level: 3, .. })));
        assert!(adoc_blocks.iter().any(
            |b| matches!(b, DocumentBlock::CodeBlock { language: Some(l), .. } if l == "kotlin")
        ));
        assert!(adoc_blocks.iter().any(|b| matches!(
            b,
            DocumentBlock::Admonition {
                kind: AdmonitionKind::Warning,
                ..
            }
        )));
        assert!(adoc_blocks
            .iter()
            .any(|b| matches!(b, DocumentBlock::NumberedList { items } if items.len() == 2)));
    }
}
