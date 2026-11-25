use std::fmt::Write as FmtWrite;
use std::io::Write;
use std::sync::atomic::AtomicBool;

use crate::error::BookGenError;
use crate::exporter::check_cancelled;
use crate::markup::{parse_markup_blocks, resolve_section_format, DocumentBlock};
use crate::models::{BookDetail, SectionStatus};

const PAGE_WIDTH: f64 = 595.28; // A4 width pt
const PAGE_HEIGHT: f64 = 841.89; // A4 height pt
const MARGIN_X: f64 = 54.0;
const MARGIN_TOP: f64 = 60.0;
const MARGIN_BOTTOM: f64 = 56.0;
const CONTENT_WIDTH: f64 = PAGE_WIDTH - (MARGIN_X * 2.0);

#[derive(Clone, Copy)]
enum FontRole {
    Body,
    Bold,
    Mono,
}

struct PageBuilder {
    pages: Vec<String>,
    current: String,
    y: f64,
}

impl PageBuilder {
    fn new() -> Self {
        Self {
            pages: Vec::new(),
            current: String::new(),
            y: PAGE_HEIGHT - MARGIN_TOP,
        }
    }

    fn ensure_space(&mut self, needed_height: f64) {
        if self.y - needed_height < MARGIN_BOTTOM {
            self.finish_page();
        }
    }

    fn finish_page(&mut self) {
        if !self.current.is_empty() {
            self.pages.push(std::mem::take(&mut self.current));
        }
        self.y = PAGE_HEIGHT - MARGIN_TOP;
    }

    fn add_gap(&mut self, gap: f64) {
        self.y -= gap;
        if self.y < MARGIN_BOTTOM {
            self.finish_page();
        }
    }

    fn draw_rule(&mut self) {
        self.ensure_space(16.0);
        let y = self.y;
        let x2 = PAGE_WIDTH - MARGIN_X;
        let _ = writeln!(
            self.current,
            "q 0.78 0.75 0.70 RG 0.6 w {MARGIN_X:.2} {y:.2} m {x2:.2} {y:.2} l S Q"
        );
        self.y -= 14.0;
    }

    fn write_wrapped(
        &mut self,
        text: &str,
        font: FontRole,
        size: f64,
        leading: f64,
        indent: f64,
        shaded: bool,
    ) {
        let max_w = (CONTENT_WIDTH - indent - if shaded { 14.0 } else { 0.0 }).max(80.0);
        let lines = wrap_text_to_width(text, max_w, size, matches!(font, FontRole::Mono));
        if lines.is_empty() {
            return;
        }

        for line in lines {
            self.ensure_space(leading);
            let x = MARGIN_X + indent + if shaded { 7.0 } else { 0.0 };
            let y = self.y;
            if shaded {
                let rect_x = MARGIN_X + indent;
                let rect_y = y - (leading * 0.25);
                let rect_w = CONTENT_WIDTH - indent;
                let _ = writeln!(
                    self.current,
                    "q 0.95 0.94 0.91 rg {rect_x:.2} {rect_y:.2} {rect_w:.2} {leading:.2} re f Q"
                );
            }
            emit_mixed_line(&mut self.current, x, y, &line, font, size);
            self.y -= leading;
        }
    }

    fn finalize(mut self, book_title: &str) -> Vec<String> {
        if !self.current.is_empty() || self.pages.is_empty() {
            self.pages.push(self.current);
        }
        let total = self.pages.len();
        for (idx, page) in self.pages.iter_mut().enumerate() {
            let page_num = idx.saturating_add(1);
            let footer_y = 28.0;
            let _ = writeln!(page, "q 0.45 0.43 0.40 rg");
            emit_mixed_line(page, MARGIN_X, footer_y, book_title, FontRole::Body, 8.5);
            let pager = format!("{page_num} / {total}");
            let pager_w = estimate_text_width(&pager, 8.5, false);
            emit_mixed_line(
                page,
                PAGE_WIDTH - MARGIN_X - pager_w,
                footer_y,
                &pager,
                FontRole::Body,
                8.5,
            );
            let _ = writeln!(page, "Q");
        }
        self.pages
    }
}

/// Renders a complete multi-page PDF 1.4 document with Latin WinAnsi + Japan1 UTF-16BE fonts.
pub(crate) fn write_pdf_book<W: Write>(
    detail: &BookDetail,
    include_toc: bool,
    include_incomplete_notice: bool,
    cancel_flag: &AtomicBool,
    mut writer: W,
    mut on_fraction: impl FnMut(f64) -> Result<(), BookGenError>,
) -> Result<(), BookGenError> {
    let mut pb = PageBuilder::new();

    // Cover / Title section
    pb.write_wrapped(
        &detail.summary.title,
        FontRole::Bold,
        20.0,
        28.0,
        0.0,
        false,
    );
    pb.add_gap(8.0);
    if !detail.summary.genre_name.is_empty() {
        pb.write_wrapped(
            &format!("Genre: {}", detail.summary.genre_name),
            FontRole::Body,
            10.5,
            15.0,
            0.0,
            false,
        );
    }
    if !detail.summary.target_audience.is_empty() {
        pb.write_wrapped(
            &format!("Audience: {}", detail.summary.target_audience),
            FontRole::Body,
            10.5,
            15.0,
            0.0,
            false,
        );
    }
    if !detail.summary.overview.trim().is_empty() {
        pb.add_gap(8.0);
        pb.write_wrapped(
            detail.summary.overview.trim(),
            FontRole::Body,
            10.5,
            16.0,
            0.0,
            false,
        );
    }

    if include_toc && !detail.chapters.is_empty() {
        pb.add_gap(12.0);
        pb.write_wrapped("Table of Contents", FontRole::Bold, 13.0, 19.0, 0.0, false);
        for ch in &detail.chapters {
            pb.write_wrapped(
                &format!(
                    "Chapter {}: {}",
                    ch.chapter.chapter_number, ch.chapter.title
                ),
                FontRole::Body,
                10.0,
                14.5,
                10.0,
                false,
            );
        }
    }
    pb.finish_page();

    let total_chapters = detail.chapters.len().max(1);
    for (idx, ch) in detail.chapters.iter().enumerate() {
        check_cancelled(cancel_flag)?;
        if idx > 0 {
            pb.finish_page();
        }
        pb.write_wrapped(
            &format!(
                "Chapter {}: {}",
                ch.chapter.chapter_number, ch.chapter.title
            ),
            FontRole::Bold,
            16.0,
            23.0,
            0.0,
            false,
        );
        if !ch.chapter.focus_area.trim().is_empty() {
            pb.write_wrapped(
                ch.chapter.focus_area.trim(),
                FontRole::Body,
                10.0,
                15.0,
                0.0,
                false,
            );
        }
        pb.draw_rule();

        for sec in &ch.sections {
            check_cancelled(cancel_flag)?;
            let format = resolve_section_format(sec.format, &sec.content);
            let blocks = parse_markup_blocks(sec.content.clone(), format);
            let has_leading_heading = matches!(blocks.first(), Some(DocumentBlock::Heading { .. }));
            if !has_leading_heading {
                pb.add_gap(6.0);
                pb.write_wrapped(
                    &format!(
                        "{}.{} {}",
                        ch.chapter.chapter_number, sec.section_number, sec.title
                    ),
                    FontRole::Bold,
                    12.5,
                    18.5,
                    0.0,
                    false,
                );
            }

            if sec.status == SectionStatus::Completed && !blocks.is_empty() {
                for block in &blocks {
                    check_cancelled(cancel_flag)?;
                    render_block_to_pdf(&mut pb, block);
                }
            } else if include_incomplete_notice {
                pb.write_wrapped(
                    &format!("[NOTE] Section incomplete ({:?}).", sec.status),
                    FontRole::Body,
                    10.0,
                    15.0,
                    8.0,
                    true,
                );
            }
            pb.add_gap(8.0);
        }

        on_fraction((idx.saturating_add(1) as f64) / (total_chapters as f64))?;
    }

    let pages = pb.finalize(&detail.summary.title);
    emit_pdf_container(&pages, &mut writer)?;
    writer.flush()?;
    Ok(())
}

fn render_block_to_pdf(pb: &mut PageBuilder, block: &DocumentBlock) {
    match block {
        DocumentBlock::Heading { level, text } => {
            pb.add_gap(4.0);
            let size = match level {
                1 | 2 => 13.5,
                3 => 12.0,
                _ => 11.0,
            };
            pb.write_wrapped(text, FontRole::Bold, size, size + 5.5, 0.0, false);
            pb.add_gap(2.0);
        }
        DocumentBlock::Paragraph { text } => {
            pb.write_wrapped(text, FontRole::Body, 10.5, 16.0, 0.0, false);
            pb.add_gap(5.0);
        }
        DocumentBlock::CodeBlock { language, code } => {
            if let Some(lang) = language.as_deref().filter(|s| !s.trim().is_empty()) {
                pb.write_wrapped(&format!("[{lang}]"), FontRole::Mono, 8.5, 12.5, 6.0, true);
            }
            for code_line in code.lines() {
                if code_line.trim().is_empty() {
                    pb.add_gap(6.0);
                } else {
                    pb.write_wrapped(code_line, FontRole::Mono, 9.0, 13.0, 6.0, true);
                }
            }
            pb.add_gap(6.0);
        }
        DocumentBlock::Table { headers, rows } => {
            if !headers.is_empty() {
                pb.write_wrapped(&headers.join(" | "), FontRole::Bold, 9.5, 14.0, 4.0, true);
            }
            for row in rows {
                pb.write_wrapped(&row.join(" | "), FontRole::Body, 9.5, 14.0, 4.0, false);
            }
            pb.add_gap(6.0);
        }
        DocumentBlock::Admonition { kind, title, body } => {
            let label = title.as_deref().unwrap_or(kind.label());
            pb.write_wrapped(
                &format!("[{label}] {body}"),
                FontRole::Body,
                10.0,
                15.0,
                8.0,
                true,
            );
            pb.add_gap(6.0);
        }
        DocumentBlock::MathBlock { latex } => {
            pb.write_wrapped(latex, FontRole::Mono, 9.5, 14.0, 10.0, true);
            pb.add_gap(6.0);
        }
        DocumentBlock::BulletList { items } => {
            for item in items {
                pb.write_wrapped(
                    &format!("• {item}"),
                    FontRole::Body,
                    10.5,
                    15.5,
                    12.0,
                    false,
                );
            }
            pb.add_gap(4.0);
        }
        DocumentBlock::NumberedList { items } => {
            for (i, item) in items.iter().enumerate() {
                pb.write_wrapped(
                    &format!("{}. {item}", i.saturating_add(1)),
                    FontRole::Body,
                    10.5,
                    15.5,
                    12.0,
                    false,
                );
            }
            pb.add_gap(4.0);
        }
        DocumentBlock::Quote { text, attribution } => {
            pb.write_wrapped(text, FontRole::Body, 10.0, 15.0, 14.0, true);
            if let Some(attr) = attribution.as_deref().filter(|s| !s.trim().is_empty()) {
                pb.write_wrapped(&format!("— {attr}"), FontRole::Body, 9.5, 14.0, 14.0, false);
            }
            pb.add_gap(5.0);
        }
        DocumentBlock::Divider => {
            pb.draw_rule();
        }
    }
}

fn emit_pdf_container<W: Write>(pages: &[String], writer: &mut W) -> Result<(), BookGenError> {
    let mut offsets: Vec<usize> = Vec::new();
    let mut bytes_written: usize = 0;

    let header = b"%PDF-1.4\n%\xE2\xE3\xCF\xD3\n";
    writer.write_all(header)?;
    bytes_written = bytes_written.saturating_add(header.len());

    let page_count = pages.len();
    let first_page_obj = 9usize;
    let mut page_refs = String::new();
    for idx in 0..page_count {
        let page_obj = first_page_obj.saturating_add(idx.saturating_mul(2));
        let _ = write!(page_refs, "{page_obj} 0 R ");
    }

    // Object 1: Catalog
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        1,
        "<< /Type /Catalog /Pages 2 0 R >>",
    )?;
    // Object 2: Pages
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        2,
        &format!("<< /Type /Pages /Kids [ {page_refs}] /Count {page_count} >>"),
    )?;
    // Objects 3..=5: Standard Type1 Fonts
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        3,
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    )?;
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        4,
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
    )?;
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        5,
        "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>",
    )?;
    // Objects 6..=8: Type0 CJK Font (Japan1 UniJIS-UTF16-H)
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        6,
        "<< /Type /Font /Subtype /Type0 /BaseFont /HeiseiKakuGo-W5 /Encoding /UniJIS-UTF16-H /DescendantFonts [ 7 0 R ] >>",
    )?;
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        7,
        "<< /Type /Font /Subtype /CIDFontType0 /BaseFont /HeiseiKakuGo-W5 /CIDSystemInfo << /Registry (Adobe) /Ordering (Japan1) /Supplement 4 >> /FontDescriptor 8 0 R /DW 1000 >>",
    )?;
    write_obj(
        writer,
        &mut offsets,
        &mut bytes_written,
        8,
        "<< /Type /FontDescriptor /FontName /HeiseiKakuGo-W5 /Flags 4 /FontBBox [-100 -250 1000 900] /ItalicAngle 0 /Ascent 880 /Descent -120 /CapHeight 750 /StemV 80 >>",
    )?;

    // Objects 9+: alternating Page object and Content stream object
    for (idx, stream) in pages.iter().enumerate() {
        let page_obj = first_page_obj.saturating_add(idx.saturating_mul(2));
        let content_obj = page_obj.saturating_add(1);
        write_obj(
            writer,
            &mut offsets,
            &mut bytes_written,
            page_obj,
            &format!(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {PAGE_WIDTH:.2} {PAGE_HEIGHT:.2}] \
                 /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R >> >> \
                 /Contents {content_obj} 0 R >>"
            ),
        )?;
        let stream_bytes = stream.as_bytes();
        write_obj(
            writer,
            &mut offsets,
            &mut bytes_written,
            content_obj,
            &format!(
                "<< /Length {} >>\nstream\n{}\nendstream",
                stream_bytes.len(),
                stream
            ),
        )?;
    }

    let xref_start = bytes_written;
    let total_objs = offsets.len().saturating_add(1);
    writeln!(writer, "xref\n0 {total_objs}")?;
    writeln!(writer, "0000000000 65535 f ")?;
    for offset in &offsets {
        writeln!(writer, "{offset:010} 00000 n ")?;
    }
    writeln!(
        writer,
        "trailer\n<< /Size {total_objs} /Root 1 0 R >>\nstartxref\n{xref_start}\n%%EOF"
    )?;
    Ok(())
}

fn write_obj<W: Write>(
    writer: &mut W,
    offsets: &mut Vec<usize>,
    bytes_written: &mut usize,
    obj_id: usize,
    body: &str,
) -> Result<(), BookGenError> {
    offsets.push(*bytes_written);
    let rendered = format!("{obj_id} 0 obj\n{body}\nendobj\n");
    writer.write_all(rendered.as_bytes())?;
    *bytes_written = bytes_written.saturating_add(rendered.len());
    Ok(())
}

fn emit_mixed_line(
    out: &mut String,
    start_x: f64,
    y: f64,
    line: &str,
    latin_font: FontRole,
    size: f64,
) {
    let mut cursor_x = start_x;
    let mut chunk = String::new();
    let mut chunk_is_ascii = None;

    for ch in line.chars() {
        let is_ascii = ch.is_ascii() && !ch.is_control();
        match chunk_is_ascii {
            None => {
                chunk_is_ascii = Some(is_ascii);
                chunk.push(ch);
            }
            Some(prev) if prev == is_ascii => {
                chunk.push(ch);
            }
            Some(prev) => {
                flush_pdf_run(out, &mut cursor_x, y, &chunk, prev, latin_font, size);
                chunk.clear();
                chunk.push(ch);
                chunk_is_ascii = Some(is_ascii);
            }
        }
    }
    if let Some(is_ascii) = chunk_is_ascii {
        flush_pdf_run(out, &mut cursor_x, y, &chunk, is_ascii, latin_font, size);
    }
}

fn flush_pdf_run(
    out: &mut String,
    cursor_x: &mut f64,
    y: f64,
    text: &str,
    is_ascii: bool,
    latin_font: FontRole,
    size: f64,
) {
    if text.is_empty() {
        return;
    }
    if is_ascii {
        let fname = match latin_font {
            FontRole::Body => "F1",
            FontRole::Bold => "F2",
            FontRole::Mono => "F3",
        };
        let escaped = escape_pdf_literal(text);
        let _ = writeln!(
            out,
            "BT /{fname} {size:.1} Tf 1 0 0 1 {cursor_x:.2} {y:.2} Tm ({escaped}) Tj ET",
            cursor_x = *cursor_x
        );
        *cursor_x += estimate_text_width(text, size, matches!(latin_font, FontRole::Mono));
    } else {
        let hex = encode_utf16be_hex(text);
        let _ = writeln!(
            out,
            "BT /F4 {size:.1} Tf 1 0 0 1 {cursor_x:.2} {y:.2} Tm <{hex}> Tj ET",
            cursor_x = *cursor_x
        );
        *cursor_x += (text.chars().count() as f64) * size;
    }
}

fn escape_pdf_literal(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for ch in s.chars() {
        match ch {
            '\\' => out.push_str("\\\\"),
            '(' => out.push_str("\\("),
            ')' => out.push_str("\\)"),
            c if c.is_ascii() && !c.is_control() => out.push(c),
            _ => out.push(' '),
        }
    }
    out
}

fn encode_utf16be_hex(s: &str) -> String {
    let mut out = String::with_capacity(s.len().saturating_mul(4));
    for unit in s.encode_utf16() {
        let _ = write!(out, "{unit:04X}");
    }
    out
}

fn estimate_char_width(ch: char, size: f64, mono: bool) -> f64 {
    if ch.is_ascii() {
        if mono {
            size * 0.60
        } else {
            match ch {
                'i' | 'l' | '.' | ',' | ':' | ';' | '\'' | '!' | '|' => size * 0.28,
                'm' | 'w' | 'M' | 'W' => size * 0.78,
                ' ' => size * 0.28,
                _ => size * 0.52,
            }
        }
    } else {
        size
    }
}

fn estimate_text_width(s: &str, size: f64, mono: bool) -> f64 {
    s.chars().map(|c| estimate_char_width(c, size, mono)).sum()
}

fn wrap_text_to_width(text: &str, max_width: f64, size: f64, mono: bool) -> Vec<String> {
    let mut out = Vec::new();
    for raw_line in text.lines() {
        let mut current = String::new();
        let mut current_w = 0.0_f64;

        for ch in raw_line.chars() {
            let cw = estimate_char_width(ch, size, mono);
            if current_w + cw > max_width && !current.is_empty() {
                if !mono && ch.is_ascii() && !ch.is_whitespace() {
                    if let Some(space_idx) = current.rfind(' ') {
                        let tail = current
                            .get(space_idx.saturating_add(1)..)
                            .unwrap_or("")
                            .to_string();
                        current.truncate(space_idx);
                        out.push(std::mem::take(&mut current));
                        current = tail;
                        current.push(ch);
                        current_w = estimate_text_width(&current, size, mono);
                        continue;
                    }
                }
                out.push(std::mem::take(&mut current));
                if !ch.is_whitespace() {
                    current.push(ch);
                    current_w = cw;
                } else {
                    current_w = 0.0;
                }
            } else {
                current.push(ch);
                current_w += cw;
            }
        }
        out.push(current);
    }
    out
}
