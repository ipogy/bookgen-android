use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs;
use std::path::Path;

use rusqlite::{params, Connection, OpenFlags, OptionalExtension, Transaction};
use serde::Deserialize;

use crate::error::BookGenError;
use crate::models::{BookStatus, LegacyImportReport, MarkupFormat, NewBookDraft, SectionStatus};
use crate::store::ops::{
    insert_book_tx, insert_chapter_tx, insert_section_tx, now_iso_utc, refresh_book_status_tx,
    ChapterInsert, SectionInsert,
};

#[derive(Debug, Deserialize, Default)]
struct LegacyPlanJson {
    id: Option<String>,
    title: Option<String>,
    book_title: Option<String>,
    genre_key: Option<String>,
    genre_name: Option<String>,
    target_audience: Option<String>,
    overview: Option<String>,
    created_at: Option<String>,
    #[serde(default)]
    chapters: Vec<LegacyPlanChapter>,
}

#[derive(Debug, Deserialize, Default)]
struct LegacyPlanChapter {
    chapter_number: Option<u32>,
    title: Option<String>,
    focus_area: Option<String>,
    #[serde(default)]
    sections: Vec<LegacyPlanSection>,
}

#[derive(Debug, Deserialize, Default)]
struct LegacyPlanSection {
    section_number: Option<u32>,
    title: Option<String>,
    key_focus: Option<String>,
}

struct LegacyDbSectionRow {
    section_number: u32,
    content: String,
    updated_at: String,
}

pub(crate) fn run_legacy_import(
    target: &mut Connection,
    legacy_books_dir: &str,
) -> Result<LegacyImportReport, BookGenError> {
    let dir_trimmed = legacy_books_dir.trim();
    if dir_trimmed.is_empty() {
        return Err(BookGenError::invalid_input(
            "legacy_books_dir must not be blank",
        ));
    }
    let root = Path::new(dir_trimmed);
    if !root.exists() || !root.is_dir() {
        return Err(BookGenError::not_found("legacy books directory not found"));
    }

    let mut report = LegacyImportReport {
        db_books_imported: 0,
        fs_books_imported: 0,
        books_skipped_existing: 0,
        chapters_imported: 0,
        sections_imported: 0,
        empty_sections_flagged: 0,
        normalized_section_numbers: 0,
        warnings: Vec::new(),
    };

    let mut seen_book_ids: HashSet<String> = HashSet::new();
    {
        let mut stmt = target.prepare("SELECT id FROM books")?;
        let rows = stmt.query_map([], |r| r.get::<_, String>(0))?;
        for row in rows {
            seen_book_ids.insert(row?);
        }
    }

    let db_path = root.join("app_data.db");
    if db_path.exists() && db_path.is_file() {
        import_from_sqlite(target, &db_path, &mut seen_book_ids, &mut report)?;
    }

    import_from_directories(target, root, &mut seen_book_ids, &mut report)?;

    Ok(report)
}

fn import_from_sqlite(
    target: &mut Connection,
    db_path: &Path,
    seen_book_ids: &mut HashSet<String>,
    report: &mut LegacyImportReport,
) -> Result<(), BookGenError> {
    let legacy = Connection::open_with_flags(db_path, OpenFlags::SQLITE_OPEN_READ_ONLY)?;

    let has_books_table: i64 = legacy.query_row(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'books'",
        [],
        |r| r.get(0),
    )?;
    if has_books_table == 0 {
        return Ok(());
    }

    let has_sections_table: bool = legacy.query_row(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'chapter_sections'",
        [],
        |r| r.get::<_, i64>(0).map(|n| n > 0),
    )?;
    let has_chapters_table: bool = legacy.query_row(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'chapters'",
        [],
        |r| r.get::<_, i64>(0).map(|n| n > 0),
    )?;

    let mut book_stmt = legacy.prepare(
        "SELECT id, title, genre_key, genre_name, target_audience, overview, plan_json, created_at
         FROM books ORDER BY created_at ASC",
    )?;
    let book_rows = book_stmt.query_map([], |r| {
        Ok((
            r.get::<_, String>(0)?,
            r.get::<_, String>(1)?,
            r.get::<_, Option<String>>(2)?,
            r.get::<_, Option<String>>(3)?,
            r.get::<_, Option<String>>(4)?,
            r.get::<_, Option<String>>(5)?,
            r.get::<_, String>(6)?,
            r.get::<_, String>(7)?,
        ))
    })?;

    for row_res in book_rows {
        let (id, title, genre_key, genre_name, audience, overview, plan_json, created_at) =
            row_res?;
        if seen_book_ids.contains(&id) {
            report.books_skipped_existing = report.books_skipped_existing.saturating_add(1);
            continue;
        }

        let plan: LegacyPlanJson = match serde_json::from_str(&plan_json) {
            Ok(parsed) => parsed,
            Err(_) => {
                report.warnings.push(format!(
                    "Book '{id}' had malformed plan_json; skipped DB import."
                ));
                continue;
            }
        };

        let mut sections_by_ch: BTreeMap<u32, Vec<LegacyDbSectionRow>> = BTreeMap::new();
        if has_sections_table {
            let mut sec_stmt = legacy.prepare(
                "SELECT chapter_number, section_number, content, updated_at
                 FROM chapter_sections
                 WHERE book_id = ?1
                 ORDER BY chapter_number ASC, section_number ASC",
            )?;
            let sec_rows = sec_stmt.query_map(params![id], |r| {
                Ok((
                    r.get::<_, i64>(0)?,
                    r.get::<_, i64>(1)?,
                    r.get::<_, String>(2)?,
                    r.get::<_, String>(3)?,
                ))
            })?;
            for s in sec_rows {
                let (ch_raw, sec_raw, content, updated_at) = s?;
                let ch_num = u32::try_from(ch_raw.max(1)).unwrap_or(1);
                let sec_num = u32::try_from(sec_raw.max(1)).unwrap_or(1);
                sections_by_ch
                    .entry(ch_num)
                    .or_default()
                    .push(LegacyDbSectionRow {
                        section_number: sec_num,
                        content,
                        updated_at,
                    });
            }
        }

        let mut legacy_chapters_by_ch: BTreeMap<u32, (String, String)> = BTreeMap::new();
        if sections_by_ch.is_empty() && has_chapters_table {
            let mut ch_stmt = legacy.prepare(
                "SELECT chapter_number, content, updated_at
                 FROM chapters WHERE book_id = ?1 ORDER BY chapter_number ASC",
            )?;
            let ch_rows = ch_stmt.query_map(params![id], |r| {
                Ok((
                    r.get::<_, i64>(0)?,
                    r.get::<_, String>(1)?,
                    r.get::<_, String>(2)?,
                ))
            })?;
            for c in ch_rows {
                let (ch_raw, content, updated_at) = c?;
                let ch_num = u32::try_from(ch_raw.max(1)).unwrap_or(1);
                legacy_chapters_by_ch.insert(ch_num, (content, updated_at));
            }
        }
        if sections_by_ch.is_empty() && legacy_chapters_by_ch.is_empty() {
            if let Some(root_dir) = db_path.parent() {
                let fallback_dir = root_dir.join(&id);
                if fallback_dir.is_dir() {
                    legacy_chapters_by_ch =
                        load_chapter_blobs_from_disk(&fallback_dir, &plan.chapters, &created_at);
                }
            }
        }

        let now = now_iso_utc();
        let draft = NewBookDraft {
            id: Some(id.clone()),
            title: non_empty_or(
                &title,
                plan.book_title
                    .as_deref()
                    .or(plan.title.as_deref())
                    .unwrap_or("Untitled Legacy Book"),
            ),
            theme_prompt: non_empty_or(&title, "Imported legacy book"),
            genre_key: genre_key
                .or(plan.genre_key.clone())
                .unwrap_or_else(|| "tech".to_string()),
            genre_name: genre_name.or(plan.genre_name.clone()).unwrap_or_default(),
            target_audience: audience
                .or(plan.target_audience.clone())
                .unwrap_or_default(),
            tone: String::new(),
            target_char_count: 15000,
            overview: overview.or(plan.overview.clone()).unwrap_or_default(),
        };

        let tx = target.transaction()?;
        let imported_book_id =
            insert_book_tx(&tx, &draft, Some(&id), BookStatus::Draft, &created_at, &now)?;

        if !sections_by_ch.is_empty() || !plan.chapters.is_empty() {
            if sections_by_ch.is_empty() && !legacy_chapters_by_ch.is_empty() {
                import_chapter_blobs_into_tx(
                    &tx,
                    &imported_book_id,
                    &plan.chapters,
                    &legacy_chapters_by_ch,
                    &created_at,
                    report,
                )?;
            } else {
                import_planned_sections_with_rows_tx(
                    &tx,
                    &imported_book_id,
                    &plan.chapters,
                    &mut sections_by_ch,
                    &created_at,
                    report,
                )?;
            }
        }

        refresh_book_status_tx(&tx, &imported_book_id, &now)?;
        tx.commit()?;

        seen_book_ids.insert(imported_book_id);
        report.db_books_imported = report.db_books_imported.saturating_add(1);
    }

    Ok(())
}

fn import_from_directories(
    target: &mut Connection,
    root: &Path,
    seen_book_ids: &mut HashSet<String>,
    report: &mut LegacyImportReport,
) -> Result<(), BookGenError> {
    let mut dirs = Vec::new();
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }
        let dir_name = entry.file_name().to_string_lossy().to_string();
        if dir_name.starts_with('.') {
            continue;
        }
        dirs.push((dir_name, path));
    }
    dirs.sort_by(|a, b| a.0.cmp(&b.0));

    for (dir_name, book_dir) in dirs {
        let meta_path = book_dir.join("book.json");
        if !meta_path.exists() || !meta_path.is_file() {
            continue;
        }

        let raw_json = match fs::read_to_string(&meta_path) {
            Ok(s) => s,
            Err(_) => {
                report
                    .warnings
                    .push(format!("Could not read book.json in '{dir_name}'."));
                continue;
            }
        };
        let plan: LegacyPlanJson = match serde_json::from_str(&raw_json) {
            Ok(p) => p,
            Err(_) => {
                report
                    .warnings
                    .push(format!("Malformed book.json in '{dir_name}'."));
                continue;
            }
        };

        let book_id = plan
            .id
            .as_deref()
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .unwrap_or(dir_name.as_str())
            .to_string();

        if seen_book_ids.contains(&book_id) {
            report.books_skipped_existing = report.books_skipped_existing.saturating_add(1);
            continue;
        }

        let exists_in_db: Option<String> = target
            .query_row(
                "SELECT id FROM books WHERE id = ?1",
                params![book_id],
                |r| r.get(0),
            )
            .optional()?;
        if exists_in_db.is_some() {
            seen_book_ids.insert(book_id);
            report.books_skipped_existing = report.books_skipped_existing.saturating_add(1);
            continue;
        }

        let now = now_iso_utc();
        let created_at = plan
            .created_at
            .clone()
            .filter(|s| !s.trim().is_empty())
            .unwrap_or_else(|| now.clone());

        let title = plan
            .book_title
            .as_deref()
            .or(plan.title.as_deref())
            .filter(|s| !s.trim().is_empty())
            .unwrap_or(dir_name.as_str())
            .to_string();

        let draft = NewBookDraft {
            id: Some(book_id.clone()),
            title: title.clone(),
            theme_prompt: title,
            genre_key: plan.genre_key.unwrap_or_else(|| "tech".to_string()),
            genre_name: plan.genre_name.unwrap_or_default(),
            target_audience: plan.target_audience.unwrap_or_default(),
            tone: String::new(),
            target_char_count: 15000,
            overview: plan.overview.unwrap_or_default(),
        };

        let chapter_blobs = load_chapter_blobs_from_disk(&book_dir, &plan.chapters, &created_at);

        let tx = target.transaction()?;
        let imported_id = insert_book_tx(
            &tx,
            &draft,
            Some(&book_id),
            BookStatus::Draft,
            &created_at,
            &now,
        )?;
        import_chapter_blobs_into_tx(
            &tx,
            &imported_id,
            &plan.chapters,
            &chapter_blobs,
            &created_at,
            report,
        )?;
        refresh_book_status_tx(&tx, &imported_id, &now)?;
        tx.commit()?;

        seen_book_ids.insert(imported_id);
        report.fs_books_imported = report.fs_books_imported.saturating_add(1);
    }

    Ok(())
}

fn import_planned_sections_with_rows_tx(
    tx: &Transaction<'_>,
    book_id: &str,
    plan_chapters: &[LegacyPlanChapter],
    rows_by_ch: &mut BTreeMap<u32, Vec<LegacyDbSectionRow>>,
    created_at: &str,
    report: &mut LegacyImportReport,
) -> Result<(), BookGenError> {
    for (ch_idx, ch) in plan_chapters.iter().enumerate() {
        let ch_pos = u32::try_from(ch_idx.saturating_add(1)).unwrap_or(u32::MAX);
        let ch_num = ch.chapter_number.filter(|n| *n > 0).unwrap_or(ch_pos);
        let ch_title = ch
            .title
            .as_deref()
            .filter(|s| !s.trim().is_empty())
            .unwrap_or("Chapter");
        let focus = ch.focus_area.as_deref().unwrap_or("");

        let chapter_id = insert_chapter_tx(
            tx,
            ChapterInsert {
                book_id,
                chapter_number: ch_num,
                position: ch_pos,
                title: ch_title,
                focus_area: focus,
                created_at,
                updated_at: created_at,
            },
        )?;
        report.chapters_imported = report.chapters_imported.saturating_add(1);

        let mut ch_rows = rows_by_ch.remove(&ch_num).unwrap_or_default();
        let mut used_indices = HashSet::new();

        for (sec_idx, sec) in ch.sections.iter().enumerate() {
            let local_num = u32::try_from(sec_idx.saturating_add(1)).unwrap_or(u32::MAX);
            let raw_sec_num = sec.section_number.filter(|n| *n > 0).unwrap_or(local_num);
            if raw_sec_num != local_num {
                report.normalized_section_numbers =
                    report.normalized_section_numbers.saturating_add(1);
            }

            let prefixed_num = ch_num.saturating_mul(10).saturating_add(local_num);
            let matched_pos = ch_rows
                .iter()
                .enumerate()
                .find(|(idx, r)| {
                    !used_indices.contains(idx)
                        && (r.section_number == raw_sec_num
                            || r.section_number == local_num
                            || r.section_number == prefixed_num)
                })
                .map(|(idx, _)| idx);

            let sec_title = sec
                .title
                .as_deref()
                .filter(|s| !s.trim().is_empty())
                .unwrap_or("Section");
            let key_focus = sec.key_focus.as_deref().unwrap_or("");

            if let Some(pos_in_rows) = matched_pos {
                used_indices.insert(pos_in_rows);
                if let Some(row) = ch_rows.get(pos_in_rows) {
                    if row.section_number != local_num {
                        report.normalized_section_numbers =
                            report.normalized_section_numbers.saturating_add(1);
                    }
                    let (status, format, revision_source) = classify_imported_content(&row.content);
                    if status == SectionStatus::Empty {
                        report.empty_sections_flagged =
                            report.empty_sections_flagged.saturating_add(1);
                    }
                    insert_section_tx(
                        tx,
                        SectionInsert {
                            chapter_id: &chapter_id,
                            book_id,
                            chapter_number: ch_num,
                            section_number: local_num,
                            position: local_num,
                            title: sec_title,
                            key_focus,
                            format,
                            content: &row.content,
                            status,
                            error_message: None,
                            created_at,
                            updated_at: &row.updated_at,
                            revision_source,
                            revision_note: "Imported from legacy app_data.db",
                        },
                    )?;
                    report.sections_imported = report.sections_imported.saturating_add(1);
                    continue;
                }
            }

            insert_section_tx(
                tx,
                SectionInsert {
                    chapter_id: &chapter_id,
                    book_id,
                    chapter_number: ch_num,
                    section_number: local_num,
                    position: local_num,
                    title: sec_title,
                    key_focus,
                    format: MarkupFormat::AsciiDoc,
                    content: "",
                    status: SectionStatus::Pending,
                    error_message: None,
                    created_at,
                    updated_at: created_at,
                    revision_source: None,
                    revision_note: "",
                },
            )?;
            report.sections_imported = report.sections_imported.saturating_add(1);
        }

        // Preserve any orphan generated rows inside this chapter rather than dropping content.
        let mut next_pos = u32::try_from(ch.sections.len().saturating_add(1)).unwrap_or(1);
        for (idx, row) in ch_rows.drain(..).enumerate() {
            if used_indices.contains(&idx) {
                continue;
            }
            if row.section_number != next_pos {
                report.normalized_section_numbers =
                    report.normalized_section_numbers.saturating_add(1);
            }
            let (status, format, revision_source) = classify_imported_content(&row.content);
            if status == SectionStatus::Empty {
                report.empty_sections_flagged = report.empty_sections_flagged.saturating_add(1);
            }
            let orphan_title = format!("Section {next_pos}");
            insert_section_tx(
                tx,
                SectionInsert {
                    chapter_id: &chapter_id,
                    book_id,
                    chapter_number: ch_num,
                    section_number: next_pos,
                    position: next_pos,
                    title: &orphan_title,
                    key_focus: "",
                    format,
                    content: &row.content,
                    status,
                    error_message: None,
                    created_at,
                    updated_at: &row.updated_at,
                    revision_source,
                    revision_note: "Recovered orphan legacy section",
                },
            )?;
            report.sections_imported = report.sections_imported.saturating_add(1);
            next_pos = next_pos.saturating_add(1);
        }
    }

    Ok(())
}

fn import_chapter_blobs_into_tx(
    tx: &Transaction<'_>,
    book_id: &str,
    plan_chapters: &[LegacyPlanChapter],
    blobs: &BTreeMap<u32, (String, String)>,
    created_at: &str,
    report: &mut LegacyImportReport,
) -> Result<(), BookGenError> {
    for (ch_idx, ch) in plan_chapters.iter().enumerate() {
        let ch_pos = u32::try_from(ch_idx.saturating_add(1)).unwrap_or(u32::MAX);
        let ch_num = ch.chapter_number.filter(|n| *n > 0).unwrap_or(ch_pos);
        let ch_title = ch
            .title
            .as_deref()
            .filter(|s| !s.trim().is_empty())
            .unwrap_or("Chapter");
        let focus = ch.focus_area.as_deref().unwrap_or("");

        let chapter_id = insert_chapter_tx(
            tx,
            ChapterInsert {
                book_id,
                chapter_number: ch_num,
                position: ch_pos,
                title: ch_title,
                focus_area: focus,
                created_at,
                updated_at: created_at,
            },
        )?;
        report.chapters_imported = report.chapters_imported.saturating_add(1);

        let blob_opt = blobs.get(&ch_num);
        let split_chunks: Vec<(String, String)> = match blob_opt {
            Some((raw_text, _)) => split_chapter_blob_into_sections(raw_text),
            None => Vec::new(),
        };
        let blob_updated = blob_opt.map(|(_, ts)| ts.as_str()).unwrap_or(created_at);

        let mut used_chunks = HashSet::new();
        let mut norm_by_title: HashMap<String, usize> = HashMap::new();
        for (i, (heading, _)) in split_chunks.iter().enumerate() {
            norm_by_title.insert(heading.trim().to_lowercase(), i);
        }

        for (sec_idx, sec) in ch.sections.iter().enumerate() {
            let local_num = u32::try_from(sec_idx.saturating_add(1)).unwrap_or(u32::MAX);
            let sec_title = sec
                .title
                .as_deref()
                .filter(|s| !s.trim().is_empty())
                .unwrap_or("Section");
            let key_focus = sec.key_focus.as_deref().unwrap_or("");

            let chosen = norm_by_title
                .get(&sec_title.trim().to_lowercase())
                .copied()
                .filter(|idx| !used_chunks.contains(idx))
                .or_else(|| {
                    (!used_chunks.contains(&sec_idx) && sec_idx < split_chunks.len())
                        .then_some(sec_idx)
                });

            if let Some(chunk_idx) = chosen {
                if let Some((_, body)) = split_chunks.get(chunk_idx) {
                    used_chunks.insert(chunk_idx);
                    let (status, format, rev_src) = classify_imported_content(body);
                    if status == SectionStatus::Empty {
                        report.empty_sections_flagged =
                            report.empty_sections_flagged.saturating_add(1);
                    }
                    insert_section_tx(
                        tx,
                        SectionInsert {
                            chapter_id: &chapter_id,
                            book_id,
                            chapter_number: ch_num,
                            section_number: local_num,
                            position: local_num,
                            title: sec_title,
                            key_focus,
                            format,
                            content: body,
                            status,
                            error_message: None,
                            created_at,
                            updated_at: blob_updated,
                            revision_source: rev_src,
                            revision_note: "Imported from legacy chapter file",
                        },
                    )?;
                    report.sections_imported = report.sections_imported.saturating_add(1);
                    continue;
                }
            }

            insert_section_tx(
                tx,
                SectionInsert {
                    chapter_id: &chapter_id,
                    book_id,
                    chapter_number: ch_num,
                    section_number: local_num,
                    position: local_num,
                    title: sec_title,
                    key_focus,
                    format: MarkupFormat::Markdown,
                    content: "",
                    status: SectionStatus::Pending,
                    error_message: None,
                    created_at,
                    updated_at: created_at,
                    revision_source: None,
                    revision_note: "",
                },
            )?;
            report.sections_imported = report.sections_imported.saturating_add(1);
        }

        // Handle unmapped chunks if the legacy file has additional or unaligned headings.
        let mut extra_pos = u32::try_from(ch.sections.len().saturating_add(1)).unwrap_or(1);
        for (idx, (heading, body)) in split_chunks.iter().enumerate() {
            if used_chunks.contains(&idx) || body.trim().is_empty() {
                continue;
            }
            let (status, format, rev_src) = classify_imported_content(body);
            let title = if heading.trim().is_empty() {
                format!("Section {extra_pos}")
            } else {
                heading.trim().to_string()
            };
            insert_section_tx(
                tx,
                SectionInsert {
                    chapter_id: &chapter_id,
                    book_id,
                    chapter_number: ch_num,
                    section_number: extra_pos,
                    position: extra_pos,
                    title: &title,
                    key_focus: "",
                    format,
                    content: body,
                    status,
                    error_message: None,
                    created_at,
                    updated_at: blob_updated,
                    revision_source: rev_src,
                    revision_note: "Imported unaligned legacy section",
                },
            )?;
            report.sections_imported = report.sections_imported.saturating_add(1);
            extra_pos = extra_pos.saturating_add(1);
        }
    }

    Ok(())
}

fn split_chapter_blob_into_sections(raw: &str) -> Vec<(String, String)> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Vec::new();
    }

    let mut sections: Vec<(String, String)> = Vec::new();
    let mut current_title = String::new();
    let mut current_lines: Vec<&str> = Vec::new();
    let mut in_fence = false;

    for line in trimmed.lines() {
        let trim_line = line.trim_start();
        if trim_line.starts_with("```") || trim_line == "----" {
            in_fence = !in_fence;
            current_lines.push(line);
            continue;
        }
        if !in_fence {
            if let Some(heading) = trim_line
                .strip_prefix("=== ")
                .or_else(|| trim_line.strip_prefix("## "))
            {
                if !current_lines.is_empty() {
                    let body = current_lines.join("\n");
                    if !body.trim().is_empty() {
                        sections.push((current_title.clone(), body));
                    }
                    current_lines.clear();
                }
                current_title = heading.trim().to_string();
                current_lines.push(line);
                continue;
            }
            // Drop top-level chapter banner lines (# Chapter or == Chapter) only when preamble is empty.
            if sections.is_empty()
                && current_lines.is_empty()
                && (trim_line.starts_with("# ") || trim_line.starts_with("== "))
            {
                continue;
            }
        }
        current_lines.push(line);
    }

    if !current_lines.is_empty() {
        let body = current_lines.join("\n");
        if !body.trim().is_empty() {
            sections.push((current_title, body));
        }
    }

    if sections.is_empty() {
        vec![(String::new(), trimmed.to_string())]
    } else {
        sections
    }
}

fn classify_imported_content(content: &str) -> (SectionStatus, MarkupFormat, Option<&'static str>) {
    let format = detect_markup_format(content);
    if content.trim().is_empty() {
        (SectionStatus::Empty, format, None)
    } else {
        (SectionStatus::Completed, format, Some("legacy_import"))
    }
}

fn detect_markup_format(content: &str) -> MarkupFormat {
    for line in content.lines() {
        let t = line.trim_start();
        if t.starts_with("== ")
            || t.starts_with("=== ")
            || t == "----"
            || t.starts_with("[NOTE]")
            || t.starts_with("[TIP]")
            || t.starts_with("[WARNING]")
            || t.starts_with("[source,")
        {
            return MarkupFormat::AsciiDoc;
        }
        if t.starts_with("## ") || t.starts_with("### ") || t.starts_with("```") {
            return MarkupFormat::Markdown;
        }
    }
    MarkupFormat::Markdown
}

fn load_chapter_blobs_from_disk(
    book_dir: &Path,
    chapters: &[LegacyPlanChapter],
    created_at: &str,
) -> BTreeMap<u32, (String, String)> {
    let mut chapter_blobs = BTreeMap::new();
    for (idx, ch) in chapters.iter().enumerate() {
        let ch_num = ch
            .chapter_number
            .filter(|n| *n > 0)
            .unwrap_or_else(|| u32::try_from(idx.saturating_add(1)).unwrap_or(1));
        let adoc_path = book_dir.join(format!("chapter_{ch_num}.adoc"));
        let md_path = book_dir.join(format!("chapter_{ch_num}.md"));
        let selected = if adoc_path.exists() {
            Some(adoc_path)
        } else if md_path.exists() {
            Some(md_path)
        } else {
            None
        };
        if let Some(p) = selected {
            if let Ok(content) = fs::read_to_string(&p) {
                chapter_blobs.insert(ch_num, (content, created_at.to_string()));
            }
        }
    }
    chapter_blobs
}

fn non_empty_or(primary: &str, fallback: &str) -> String {
    if primary.trim().is_empty() {
        fallback.trim().to_string()
    } else {
        primary.trim().to_string()
    }
}
