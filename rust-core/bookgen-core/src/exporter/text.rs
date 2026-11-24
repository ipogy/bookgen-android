use std::io::Write;
use std::sync::atomic::AtomicBool;

use crate::error::BookGenError;
use crate::exporter::check_cancelled;
use crate::markup::{blocks_to_asciidoc, blocks_to_markdown, parse_markup_blocks, DocumentBlock};
use crate::models::{BookDetail, SectionStatus};

pub(crate) fn write_markdown_book<W: Write>(
    detail: &BookDetail,
    include_toc: bool,
    include_incomplete_notice: bool,
    cancel_flag: &AtomicBool,
    writer: &mut W,
    mut on_fraction: impl FnMut(f64) -> Result<(), BookGenError>,
) -> Result<(), BookGenError> {
    writeln!(writer, "# {}\n", detail.summary.title)?;
    if !detail.summary.genre_name.is_empty() {
        writeln!(writer, "- Genre: {}", detail.summary.genre_name)?;
    }
    if !detail.summary.target_audience.is_empty() {
        writeln!(
            writer,
            "- Target Audience: {}",
            detail.summary.target_audience
        )?;
    }
    if !detail.summary.tone.is_empty() {
        writeln!(writer, "- Tone: {}", detail.summary.tone)?;
    }
    writeln!(writer)?;

    if !detail.summary.overview.trim().is_empty() {
        writeln!(writer, "{}\n", detail.summary.overview.trim())?;
    }

    if include_toc && !detail.chapters.is_empty() {
        writeln!(writer, "## Table of Contents\n")?;
        for ch in &detail.chapters {
            writeln!(
                writer,
                "- Chapter {}: {}",
                ch.chapter.chapter_number, ch.chapter.title
            )?;
            for sec in &ch.sections {
                writeln!(
                    writer,
                    "  - {}.{} {}",
                    ch.chapter.chapter_number, sec.section_number, sec.title
                )?;
            }
        }
        writeln!(writer, "\n---\n")?;
    }

    let total_chapters = detail.chapters.len().max(1);
    for (idx, ch) in detail.chapters.iter().enumerate() {
        check_cancelled(cancel_flag)?;
        writeln!(
            writer,
            "## Chapter {}: {}\n",
            ch.chapter.chapter_number, ch.chapter.title
        )?;
        if !ch.chapter.focus_area.trim().is_empty() {
            writeln!(writer, "*{}*\n", ch.chapter.focus_area.trim())?;
        }

        for sec in &ch.sections {
            check_cancelled(cancel_flag)?;
            let blocks = parse_markup_blocks(sec.content.clone(), sec.format);
            let has_leading_heading = matches!(blocks.first(), Some(DocumentBlock::Heading { .. }));
            if !has_leading_heading {
                writeln!(
                    writer,
                    "### {}.{} {}\n",
                    ch.chapter.chapter_number, sec.section_number, sec.title
                )?;
            }

            if sec.status == SectionStatus::Completed && !blocks.is_empty() {
                writeln!(writer, "{}\n", blocks_to_markdown(&blocks))?;
            } else if include_incomplete_notice {
                writeln!(
                    writer,
                    "> [!NOTE]\n> This section is currently incomplete ({:?}).\n",
                    sec.status
                )?;
            }
        }

        on_fraction((idx.saturating_add(1) as f64) / (total_chapters as f64))?;
    }

    writer.flush()?;
    Ok(())
}

pub(crate) fn write_asciidoc_book<W: Write>(
    detail: &BookDetail,
    include_toc: bool,
    include_incomplete_notice: bool,
    cancel_flag: &AtomicBool,
    writer: &mut W,
    mut on_fraction: impl FnMut(f64) -> Result<(), BookGenError>,
) -> Result<(), BookGenError> {
    writeln!(writer, "= {}", detail.summary.title)?;
    writeln!(writer, ":doctype: book")?;
    if include_toc {
        writeln!(writer, ":toc:")?;
    }
    writeln!(writer, ":sectnums:")?;
    writeln!(writer)?;

    if !detail.summary.overview.trim().is_empty() {
        writeln!(writer, "[abstract]\n{}\n", detail.summary.overview.trim())?;
    }

    let total_chapters = detail.chapters.len().max(1);
    for (idx, ch) in detail.chapters.iter().enumerate() {
        check_cancelled(cancel_flag)?;
        writeln!(
            writer,
            "== Chapter {}: {}\n",
            ch.chapter.chapter_number, ch.chapter.title
        )?;
        if !ch.chapter.focus_area.trim().is_empty() {
            writeln!(writer, "_{}_\n", ch.chapter.focus_area.trim())?;
        }

        for sec in &ch.sections {
            check_cancelled(cancel_flag)?;
            let blocks = parse_markup_blocks(sec.content.clone(), sec.format);
            let has_leading_heading = matches!(blocks.first(), Some(DocumentBlock::Heading { .. }));
            if !has_leading_heading {
                writeln!(
                    writer,
                    "=== {}.{} {}\n",
                    ch.chapter.chapter_number, sec.section_number, sec.title
                )?;
            }

            if sec.status == SectionStatus::Completed && !blocks.is_empty() {
                writeln!(writer, "{}\n", blocks_to_asciidoc(&blocks))?;
            } else if include_incomplete_notice {
                writeln!(
                    writer,
                    "[NOTE]\n====\nThis section is currently incomplete ({:?}).\n====\n",
                    sec.status
                )?;
            }
        }

        on_fraction((idx.saturating_add(1) as f64) / (total_chapters as f64))?;
    }

    writer.flush()?;
    Ok(())
}
