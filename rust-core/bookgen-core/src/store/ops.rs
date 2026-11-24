use std::collections::HashSet;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

use chrono::{SecondsFormat, Utc};
use rusqlite::{params, OptionalExtension, Transaction};

use crate::error::BookGenError;
use crate::models::{
    count_words_and_chars, derive_book_status, BookDetail, BookStatus, BookSummary, ChapterRecord,
    ChapterWithSections, IncompleteSection, MarkupFormat, NewBookDraft, NewChapterPlan,
    ReadingProgress, SearchHit, SectionRecord, SectionRevision, SectionStatus,
};

static ID_SEQ: AtomicU64 = AtomicU64::new(1);

pub(crate) fn now_iso_utc() -> String {
    Utc::now().to_rfc3339_opts(SecondsFormat::Secs, true)
}

pub(crate) fn generate_id(prefix: &str) -> String {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_micros() as u64)
        .unwrap_or(0);
    let seq = ID_SEQ.fetch_add(1, Ordering::Relaxed);
    if prefix.is_empty() {
        format!("{:012x}{:04x}", nanos & 0xFFFF_FFFF_FFFF, seq & 0xFFFF)
    } else {
        format!(
            "{prefix}_{:012x}_{:04x}",
            nanos & 0xFFFF_FFFF_FFFF,
            seq & 0xFFFF
        )
    }
}

pub(crate) fn insert_book_tx(
    tx: &Transaction<'_>,
    draft: &NewBookDraft,
    forced_id: Option<&str>,
    status: BookStatus,
    created_at: &str,
    updated_at: &str,
) -> Result<String, BookGenError> {
    let title = draft.title.trim();
    if title.is_empty() {
        return Err(BookGenError::invalid_input("book title must not be blank"));
    }
    if draft.target_char_count < 0 {
        return Err(BookGenError::invalid_input(
            "target_char_count must be non-negative",
        ));
    }

    let id = match forced_id {
        Some(raw) if !raw.trim().is_empty() => raw.trim().to_string(),
        _ => match draft.id.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
            Some(explicit) => explicit.to_string(),
            None => generate_id(""),
        },
    };

    let existing: Option<String> = tx
        .query_row("SELECT id FROM books WHERE id = ?1", params![id], |r| {
            r.get(0)
        })
        .optional()?;
    if existing.is_some() {
        return Err(BookGenError::conflict("book id already exists"));
    }

    let genre_key = if draft.genre_key.trim().is_empty() {
        "tech"
    } else {
        draft.genre_key.trim()
    };

    tx.execute(
        "INSERT INTO books (
            id, title, theme_prompt, genre_key, genre_name,
            target_audience, tone, target_char_count, overview,
            status, created_at, updated_at
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)",
        params![
            id,
            title,
            draft.theme_prompt.trim(),
            genre_key,
            draft.genre_name.trim(),
            draft.target_audience.trim(),
            draft.tone.trim(),
            draft.target_char_count,
            draft.overview.trim(),
            status.as_str(),
            created_at,
            updated_at,
        ],
    )?;

    Ok(id)
}

pub(crate) struct ChapterInsert<'a> {
    pub book_id: &'a str,
    pub chapter_number: u32,
    pub position: u32,
    pub title: &'a str,
    pub focus_area: &'a str,
    pub created_at: &'a str,
    pub updated_at: &'a str,
}

pub(crate) struct SectionInsert<'a> {
    pub chapter_id: &'a str,
    pub book_id: &'a str,
    pub chapter_number: u32,
    pub section_number: u32,
    pub position: u32,
    pub title: &'a str,
    pub key_focus: &'a str,
    pub format: MarkupFormat,
    pub content: &'a str,
    pub status: SectionStatus,
    pub error_message: Option<&'a str>,
    pub created_at: &'a str,
    pub updated_at: &'a str,
    pub revision_source: Option<&'a str>,
    pub revision_note: &'a str,
}

pub(crate) fn insert_chapter_tx(
    tx: &Transaction<'_>,
    row: ChapterInsert<'_>,
) -> Result<String, BookGenError> {
    let chapter_id = generate_id("ch");
    tx.execute(
        "INSERT INTO chapters (
            id, book_id, chapter_number, position, title, focus_area, created_at, updated_at
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
        params![
            chapter_id,
            row.book_id,
            i64::from(row.chapter_number),
            i64::from(row.position),
            row.title.trim(),
            row.focus_area.trim(),
            row.created_at,
            row.updated_at,
        ],
    )?;
    Ok(chapter_id)
}

pub(crate) fn insert_section_tx(
    tx: &Transaction<'_>,
    row: SectionInsert<'_>,
) -> Result<String, BookGenError> {
    let section_id = generate_id("sec");
    let (word_count, char_count) = count_words_and_chars(row.content);

    tx.execute(
        "INSERT INTO sections (
            id, chapter_id, book_id, chapter_number, section_number, position,
            title, key_focus, format, content, status, error_message,
            word_count, char_count, created_at, updated_at
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
        params![
            section_id,
            row.chapter_id,
            row.book_id,
            i64::from(row.chapter_number),
            i64::from(row.section_number),
            i64::from(row.position),
            row.title.trim(),
            row.key_focus.trim(),
            row.format.as_str(),
            row.content,
            row.status.as_str(),
            row.error_message,
            word_count as i64,
            char_count as i64,
            row.created_at,
            row.updated_at,
        ],
    )?;

    if let Some(source) = row.revision_source {
        insert_revision_tx(
            tx,
            &section_id,
            row.content,
            source,
            row.revision_note,
            row.updated_at,
        )?;
    }
    sync_section_fts_tx(tx, &section_id)?;

    Ok(section_id)
}

pub(crate) fn insert_revision_tx(
    tx: &Transaction<'_>,
    section_id: &str,
    content: &str,
    source: &str,
    note: &str,
    created_at: &str,
) -> Result<(), BookGenError> {
    let max_rev: i64 = tx.query_row(
        "SELECT COALESCE(MAX(revision_number), 0) FROM section_revisions WHERE section_id = ?1",
        params![section_id],
        |r| r.get(0),
    )?;
    let next_rev = max_rev.saturating_add(1);
    tx.execute(
        "INSERT INTO section_revisions (section_id, revision_number, content, source, note, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![section_id, next_rev, content, source, note, created_at],
    )?;
    Ok(())
}

pub(crate) fn sync_section_fts_tx(
    tx: &Transaction<'_>,
    section_id: &str,
) -> Result<(), BookGenError> {
    tx.execute(
        "DELETE FROM sections_fts WHERE section_id = ?1",
        params![section_id],
    )?;

    let row: Option<(String, String, String, String, String, String)> = tx
        .query_row(
            "SELECT s.book_id, s.chapter_id, b.title, c.title, s.title, s.content
             FROM sections s
             JOIN books b ON b.id = s.book_id
             JOIN chapters c ON c.id = s.chapter_id
             WHERE s.id = ?1",
            params![section_id],
            |r| {
                Ok((
                    r.get(0)?,
                    r.get(1)?,
                    r.get(2)?,
                    r.get(3)?,
                    r.get(4)?,
                    r.get(5)?,
                ))
            },
        )
        .optional()?;

    if let Some((book_id, chapter_id, book_title, chapter_title, section_title, content)) = row {
        tx.execute(
            "INSERT INTO sections_fts (
                section_id, book_id, chapter_id, book_title, chapter_title, section_title, content
             ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                section_id,
                book_id,
                chapter_id,
                book_title,
                chapter_title,
                section_title,
                content
            ],
        )?;
    }

    Ok(())
}

pub(crate) fn sync_book_fts_tx(tx: &Transaction<'_>, book_id: &str) -> Result<(), BookGenError> {
    tx.execute(
        "DELETE FROM sections_fts WHERE book_id = ?1",
        params![book_id],
    )?;
    tx.execute(
        "INSERT INTO sections_fts (
            section_id, book_id, chapter_id, book_title, chapter_title, section_title, content
         )
         SELECT s.id, s.book_id, s.chapter_id, b.title, c.title, s.title, s.content
         FROM sections s
         JOIN books b ON b.id = s.book_id
         JOIN chapters c ON c.id = s.chapter_id
         WHERE s.book_id = ?1",
        params![book_id],
    )?;
    Ok(())
}

pub(crate) fn refresh_book_status_tx(
    tx: &Transaction<'_>,
    book_id: &str,
    now: &str,
) -> Result<BookStatus, BookGenError> {
    let chapter_count: i64 = tx.query_row(
        "SELECT COUNT(*) FROM chapters WHERE book_id = ?1",
        params![book_id],
        |r| r.get(0),
    )?;

    let mut total_sections: u32 = 0;
    let mut completed: u32 = 0;
    let mut generating: u32 = 0;
    let mut failed_or_empty: u32 = 0;

    let mut stmt =
        tx.prepare("SELECT status, COUNT(*) FROM sections WHERE book_id = ?1 GROUP BY status")?;
    let rows = stmt.query_map(params![book_id], |r| {
        Ok((r.get::<_, String>(0)?, r.get::<_, i64>(1)?))
    })?;

    for item in rows {
        let (status_str, cnt_raw) = item?;
        let cnt = u32::try_from(cnt_raw.max(0)).unwrap_or(u32::MAX);
        total_sections = total_sections.saturating_add(cnt);
        match SectionStatus::from_db(&status_str)? {
            SectionStatus::Completed => completed = completed.saturating_add(cnt),
            SectionStatus::Generating => generating = generating.saturating_add(cnt),
            SectionStatus::Failed | SectionStatus::Empty => {
                failed_or_empty = failed_or_empty.saturating_add(cnt);
            }
            SectionStatus::Pending => {}
        }
    }

    let status = derive_book_status(
        chapter_count > 0,
        total_sections,
        completed,
        generating,
        failed_or_empty,
    );

    tx.execute(
        "UPDATE books SET status = ?1, updated_at = ?2 WHERE id = ?3",
        params![status.as_str(), now, book_id],
    )?;

    Ok(status)
}

pub(crate) fn save_outline_tx(
    tx: &Transaction<'_>,
    book_id: &str,
    chapters: &[NewChapterPlan],
    now: &str,
) -> Result<(), BookGenError> {
    let exists: Option<String> = tx
        .query_row(
            "SELECT id FROM books WHERE id = ?1",
            params![book_id],
            |r| r.get(0),
        )
        .optional()?;
    if exists.is_none() {
        return Err(BookGenError::not_found("book not found"));
    }
    if chapters.is_empty() {
        return Err(BookGenError::invalid_input(
            "outline must include at least one chapter",
        ));
    }

    tx.execute(
        "DELETE FROM reading_progress WHERE book_id = ?1",
        params![book_id],
    )?;
    tx.execute(
        "DELETE FROM sections_fts WHERE book_id = ?1",
        params![book_id],
    )?;
    tx.execute("DELETE FROM chapters WHERE book_id = ?1", params![book_id])?;

    for (ch_idx, chapter) in chapters.iter().enumerate() {
        if chapter.title.trim().is_empty() {
            return Err(BookGenError::invalid_input(
                "chapter title must not be blank",
            ));
        }
        let pos = u32::try_from(ch_idx.saturating_add(1)).unwrap_or(u32::MAX);
        let ch_num = if chapter.chapter_number == 0 {
            pos
        } else {
            chapter.chapter_number
        };
        let chapter_id = insert_chapter_tx(
            tx,
            ChapterInsert {
                book_id,
                chapter_number: ch_num,
                position: pos,
                title: &chapter.title,
                focus_area: &chapter.focus_area,
                created_at: now,
                updated_at: now,
            },
        )?;

        for (sec_idx, section) in chapter.sections.iter().enumerate() {
            if section.title.trim().is_empty() {
                return Err(BookGenError::invalid_input(
                    "section title must not be blank",
                ));
            }
            let sec_pos = u32::try_from(sec_idx.saturating_add(1)).unwrap_or(u32::MAX);
            let sec_num = if section.section_number == 0 {
                sec_pos
            } else {
                section.section_number
            };
            insert_section_tx(
                tx,
                SectionInsert {
                    chapter_id: &chapter_id,
                    book_id,
                    chapter_number: ch_num,
                    section_number: sec_num,
                    position: sec_pos,
                    title: &section.title,
                    key_focus: &section.key_focus,
                    format: MarkupFormat::Markdown,
                    content: "",
                    status: SectionStatus::Pending,
                    error_message: None,
                    created_at: now,
                    updated_at: now,
                    revision_source: None,
                    revision_note: "",
                },
            )?;
        }
    }

    refresh_book_status_tx(tx, book_id, now)?;
    Ok(())
}

pub(crate) fn query_book_summary(
    conn: &rusqlite::Connection,
    book_id: &str,
) -> Result<BookSummary, BookGenError> {
    let sql = "
        SELECT
            b.id,
            b.title,
            b.theme_prompt,
            b.genre_key,
            b.genre_name,
            b.target_audience,
            b.tone,
            b.target_char_count,
            b.overview,
            b.status,
            (SELECT COUNT(*) FROM chapters c WHERE c.book_id = b.id) AS total_chapters,
            COALESCE(COUNT(s.id), 0) AS total_sections,
            COALESCE(SUM(CASE WHEN s.status = 'completed' THEN 1 ELSE 0 END), 0) AS completed_sections,
            COALESCE(SUM(CASE WHEN s.status IN ('failed', 'empty') THEN 1 ELSE 0 END), 0) AS empty_or_failed_sections,
            COALESCE(SUM(s.word_count), 0) AS word_count,
            COALESCE(SUM(s.char_count), 0) AS char_count,
            rp.chapter_id,
            rp.section_id,
            COALESCE(rp.scroll_offset, 0.0),
            b.created_at,
            b.updated_at
        FROM books b
        LEFT JOIN sections s ON s.book_id = b.id
        LEFT JOIN reading_progress rp ON rp.book_id = b.id
        WHERE b.id = ?1
        GROUP BY b.id
    ";

    conn.query_row(sql, params![book_id], map_book_summary_row)
        .optional()?
        .ok_or_else(|| BookGenError::not_found("book not found"))
}

pub(crate) fn query_list_books(
    conn: &rusqlite::Connection,
    genre_filter: Option<&str>,
    status_filter: Option<BookStatus>,
) -> Result<Vec<BookSummary>, BookGenError> {
    let sql = "
        SELECT
            b.id,
            b.title,
            b.theme_prompt,
            b.genre_key,
            b.genre_name,
            b.target_audience,
            b.tone,
            b.target_char_count,
            b.overview,
            b.status,
            (SELECT COUNT(*) FROM chapters c WHERE c.book_id = b.id) AS total_chapters,
            COALESCE(COUNT(s.id), 0) AS total_sections,
            COALESCE(SUM(CASE WHEN s.status = 'completed' THEN 1 ELSE 0 END), 0) AS completed_sections,
            COALESCE(SUM(CASE WHEN s.status IN ('failed', 'empty') THEN 1 ELSE 0 END), 0) AS empty_or_failed_sections,
            COALESCE(SUM(s.word_count), 0) AS word_count,
            COALESCE(SUM(s.char_count), 0) AS char_count,
            rp.chapter_id,
            rp.section_id,
            COALESCE(rp.scroll_offset, 0.0),
            b.created_at,
            b.updated_at
        FROM books b
        LEFT JOIN sections s ON s.book_id = b.id
        LEFT JOIN reading_progress rp ON rp.book_id = b.id
        WHERE (?1 IS NULL OR b.genre_key = ?1)
          AND (?2 IS NULL OR b.status = ?2)
        GROUP BY b.id
        ORDER BY b.updated_at DESC, b.created_at DESC
    ";

    let genre_param = genre_filter.map(str::trim).filter(|s| !s.is_empty());
    let status_param = status_filter.map(BookStatus::as_str);

    let mut stmt = conn.prepare(sql)?;
    let rows = stmt.query_map(params![genre_param, status_param], map_book_summary_row)?;
    let mut results = Vec::new();
    for item in rows {
        results.push(item?);
    }
    Ok(results)
}

fn map_book_summary_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<BookSummary> {
    let status_raw: String = row.get(9)?;
    Ok(BookSummary {
        id: row.get(0)?,
        title: row.get(1)?,
        theme_prompt: row.get(2)?,
        genre_key: row.get(3)?,
        genre_name: row.get(4)?,
        target_audience: row.get(5)?,
        tone: row.get(6)?,
        target_char_count: row.get(7)?,
        overview: row.get(8)?,
        status: BookStatus::from_db(&status_raw),
        total_chapters: to_u32(row.get::<_, i64>(10)?),
        total_sections: to_u32(row.get::<_, i64>(11)?),
        completed_sections: to_u32(row.get::<_, i64>(12)?),
        empty_or_failed_sections: to_u32(row.get::<_, i64>(13)?),
        word_count: row.get::<_, i64>(14)?.max(0) as u64,
        char_count: row.get::<_, i64>(15)?.max(0) as u64,
        last_read_chapter_id: row.get(16)?,
        last_read_section_id: row.get(17)?,
        last_read_scroll_offset: row.get(18)?,
        created_at: row.get(19)?,
        updated_at: row.get(20)?,
    })
}

pub(crate) fn query_book_detail(
    conn: &rusqlite::Connection,
    book_id: &str,
) -> Result<BookDetail, BookGenError> {
    let summary = query_book_summary(conn, book_id)?;
    let chapters = query_chapters_with_sections(conn, book_id, None)?;
    Ok(BookDetail { summary, chapters })
}

pub(crate) fn query_chapters_with_sections(
    conn: &rusqlite::Connection,
    book_id: &str,
    only_chapter_id: Option<&str>,
) -> Result<Vec<ChapterWithSections>, BookGenError> {
    let mut ch_stmt = conn.prepare(
        "SELECT id, book_id, chapter_number, position, title, focus_area, created_at, updated_at
         FROM chapters
         WHERE book_id = ?1 AND (?2 IS NULL OR id = ?2)
         ORDER BY position ASC, chapter_number ASC",
    )?;
    let ch_rows = ch_stmt.query_map(params![book_id, only_chapter_id], |row| {
        Ok(ChapterRecord {
            id: row.get(0)?,
            book_id: row.get(1)?,
            chapter_number: to_u32(row.get::<_, i64>(2)?),
            position: to_u32(row.get::<_, i64>(3)?),
            title: row.get(4)?,
            focus_area: row.get(5)?,
            created_at: row.get(6)?,
            updated_at: row.get(7)?,
        })
    })?;

    let mut chapters_out = Vec::new();
    for ch_res in ch_rows {
        let chapter = ch_res?;
        let sections = query_sections_for_chapter(conn, &chapter.id)?;
        chapters_out.push(ChapterWithSections { chapter, sections });
    }
    Ok(chapters_out)
}

pub(crate) fn query_sections_for_chapter(
    conn: &rusqlite::Connection,
    chapter_id: &str,
) -> Result<Vec<SectionRecord>, BookGenError> {
    let mut stmt = conn.prepare(
        "SELECT
            s.id, s.chapter_id, s.book_id, s.chapter_number, s.section_number, s.position,
            s.title, s.key_focus, s.format, s.content, s.status, s.error_message,
            s.word_count, s.char_count,
            (SELECT COUNT(*) FROM section_revisions sr WHERE sr.section_id = s.id) AS revision_count,
            s.created_at, s.updated_at
         FROM sections s
         WHERE s.chapter_id = ?1
         ORDER BY s.position ASC, s.section_number ASC",
    )?;
    let rows = stmt.query_map(params![chapter_id], map_section_row)?;
    let mut sections = Vec::new();
    for item in rows {
        sections.push(item?);
    }
    Ok(sections)
}

pub(crate) fn query_section_by_id(
    conn: &rusqlite::Connection,
    section_id: &str,
) -> Result<SectionRecord, BookGenError> {
    conn.query_row(
        "SELECT
            s.id, s.chapter_id, s.book_id, s.chapter_number, s.section_number, s.position,
            s.title, s.key_focus, s.format, s.content, s.status, s.error_message,
            s.word_count, s.char_count,
            (SELECT COUNT(*) FROM section_revisions sr WHERE sr.section_id = s.id) AS revision_count,
            s.created_at, s.updated_at
         FROM sections s
         WHERE s.id = ?1",
        params![section_id],
        map_section_row,
    )
    .optional()?
    .ok_or_else(|| BookGenError::not_found("section not found"))
}

fn map_section_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<SectionRecord> {
    let fmt_raw: String = row.get(8)?;
    let status_raw: String = row.get(10)?;
    let status = SectionStatus::from_db(&status_raw).map_err(|_| {
        rusqlite::Error::InvalidColumnType(10, "status".to_string(), rusqlite::types::Type::Text)
    })?;

    Ok(SectionRecord {
        id: row.get(0)?,
        chapter_id: row.get(1)?,
        book_id: row.get(2)?,
        chapter_number: to_u32(row.get::<_, i64>(3)?),
        section_number: to_u32(row.get::<_, i64>(4)?),
        position: to_u32(row.get::<_, i64>(5)?),
        title: row.get(6)?,
        key_focus: row.get(7)?,
        format: MarkupFormat::from_db(&fmt_raw),
        content: row.get(9)?,
        status,
        error_message: row.get(11)?,
        word_count: row.get::<_, i64>(12)?.max(0) as u64,
        char_count: row.get::<_, i64>(13)?.max(0) as u64,
        revision_count: to_u32(row.get::<_, i64>(14)?),
        created_at: row.get(15)?,
        updated_at: row.get(16)?,
    })
}

pub(crate) fn query_incomplete_sections(
    conn: &rusqlite::Connection,
    book_id: &str,
) -> Result<Vec<IncompleteSection>, BookGenError> {
    let exists: Option<String> = conn
        .query_row(
            "SELECT id FROM books WHERE id = ?1",
            params![book_id],
            |r| r.get(0),
        )
        .optional()?;
    if exists.is_none() {
        return Err(BookGenError::not_found("book not found"));
    }

    let mut stmt = conn.prepare(
        "SELECT
            s.book_id, s.chapter_id, c.chapter_number, c.title,
            s.id, s.section_number, s.title, s.key_focus, s.status, s.error_message
         FROM sections s
         JOIN chapters c ON c.id = s.chapter_id
         WHERE s.book_id = ?1
           AND s.status IN ('pending', 'generating', 'failed', 'empty')
         ORDER BY c.position ASC, s.position ASC",
    )?;
    let rows = stmt.query_map(params![book_id], |r| {
        let status_raw: String = r.get(8)?;
        let status = SectionStatus::from_db(&status_raw).map_err(|_| {
            rusqlite::Error::InvalidColumnType(8, "status".to_string(), rusqlite::types::Type::Text)
        })?;
        Ok(IncompleteSection {
            book_id: r.get(0)?,
            chapter_id: r.get(1)?,
            chapter_number: to_u32(r.get::<_, i64>(2)?),
            chapter_title: r.get(3)?,
            section_id: r.get(4)?,
            section_number: to_u32(r.get::<_, i64>(5)?),
            section_title: r.get(6)?,
            key_focus: r.get(7)?,
            status,
            error_message: r.get(9)?,
        })
    })?;

    let mut out = Vec::new();
    for item in rows {
        out.push(item?);
    }
    Ok(out)
}

pub(crate) fn query_section_revisions(
    conn: &rusqlite::Connection,
    section_id: &str,
) -> Result<Vec<SectionRevision>, BookGenError> {
    let exists: Option<String> = conn
        .query_row(
            "SELECT id FROM sections WHERE id = ?1",
            params![section_id],
            |r| r.get(0),
        )
        .optional()?;
    if exists.is_none() {
        return Err(BookGenError::not_found("section not found"));
    }

    let mut stmt = conn.prepare(
        "SELECT id, section_id, revision_number, content, source, note, created_at
         FROM section_revisions
         WHERE section_id = ?1
         ORDER BY revision_number DESC, id DESC",
    )?;
    let rows = stmt.query_map(params![section_id], |r| {
        Ok(SectionRevision {
            id: r.get(0)?,
            section_id: r.get(1)?,
            revision_number: to_u32(r.get::<_, i64>(2)?),
            content: r.get(3)?,
            source: r.get(4)?,
            note: r.get(5)?,
            created_at: r.get(6)?,
        })
    })?;

    let mut revisions = Vec::new();
    for item in rows {
        revisions.push(item?);
    }
    Ok(revisions)
}

pub(crate) fn save_reading_progress_tx(
    tx: &Transaction<'_>,
    book_id: &str,
    chapter_id: &str,
    section_id: Option<&str>,
    scroll_offset: f64,
    now: &str,
) -> Result<ReadingProgress, BookGenError> {
    if !scroll_offset.is_finite() {
        return Err(BookGenError::invalid_input(
            "scroll_offset must be a finite number",
        ));
    }
    let clamped = scroll_offset.clamp(0.0, 1.0);

    let ch_valid: Option<String> = tx
        .query_row(
            "SELECT id FROM chapters WHERE id = ?1 AND book_id = ?2",
            params![chapter_id, book_id],
            |r| r.get(0),
        )
        .optional()?;
    if ch_valid.is_none() {
        return Err(BookGenError::not_found(
            "chapter not found for given book_id",
        ));
    }

    let normalized_section = match section_id.map(str::trim).filter(|s| !s.is_empty()) {
        Some(sec_id) => {
            let sec_valid: Option<String> = tx
                .query_row(
                    "SELECT id FROM sections WHERE id = ?1 AND chapter_id = ?2 AND book_id = ?3",
                    params![sec_id, chapter_id, book_id],
                    |r| r.get(0),
                )
                .optional()?;
            if sec_valid.is_none() {
                return Err(BookGenError::not_found(
                    "section not found for given chapter_id",
                ));
            }
            Some(sec_id.to_string())
        }
        None => None,
    };

    tx.execute(
        "INSERT INTO reading_progress (book_id, chapter_id, section_id, scroll_offset, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5)
         ON CONFLICT(book_id) DO UPDATE SET
            chapter_id = excluded.chapter_id,
            section_id = excluded.section_id,
            scroll_offset = excluded.scroll_offset,
            updated_at = excluded.updated_at",
        params![book_id, chapter_id, normalized_section, clamped, now],
    )?;

    Ok(ReadingProgress {
        book_id: book_id.to_string(),
        chapter_id: chapter_id.to_string(),
        section_id: normalized_section,
        scroll_offset: clamped,
        updated_at: now.to_string(),
    })
}

pub(crate) fn validate_exact_permutation(
    expected_ids: &[String],
    candidate_ids: &[String],
    label: &str,
) -> Result<(), BookGenError> {
    if expected_ids.len() != candidate_ids.len() {
        return Err(BookGenError::invalid_input(format!(
            "{label} reorder list length mismatch"
        )));
    }
    let expected: HashSet<&str> = expected_ids.iter().map(String::as_str).collect();
    let mut seen = HashSet::with_capacity(candidate_ids.len());
    for id in candidate_ids {
        if !expected.contains(id.as_str()) {
            return Err(BookGenError::invalid_input(format!(
                "{label} reorder list contains unknown id"
            )));
        }
        if !seen.insert(id.as_str()) {
            return Err(BookGenError::invalid_input(format!(
                "{label} reorder list contains duplicate id"
            )));
        }
    }
    Ok(())
}

pub(crate) fn query_search(
    conn: &rusqlite::Connection,
    raw_query: &str,
    book_id_filter: Option<&str>,
    limit: u32,
) -> Result<Vec<SearchHit>, BookGenError> {
    let trimmed = raw_query.trim();
    if trimmed.is_empty() {
        return Err(BookGenError::invalid_input(
            "search query must not be blank",
        ));
    }

    let cap = i64::from(limit.clamp(1, 200));
    let book_param = book_id_filter.map(str::trim).filter(|s| !s.is_empty());

    // SQLite trigram FTS5 requires >= 3 characters per indexed token.
    // Split tokens: if all non-empty tokens have >= 3 chars, use FTS5 trigram + bm25 + snippet;
    // otherwise fall back to safe escaped LIKE query with Rust snippet extraction.
    let tokens: Vec<&str> = trimmed.split_whitespace().collect();
    let can_use_fts = !tokens.is_empty() && tokens.iter().all(|t| t.chars().count() >= 3);

    if can_use_fts {
        let fts_expr = tokens
            .iter()
            .map(|tok| format!("\"{}\"", tok.replace('"', "\"\"")))
            .collect::<Vec<_>>()
            .join(" AND ");

        let mut stmt = conn.prepare(
            "SELECT
                s.book_id,
                b.title,
                s.chapter_id,
                s.chapter_number,
                c.title,
                s.id,
                s.section_number,
                s.title,
                snippet(sections_fts, -1, '«', '»', '…', 20) AS hit_snippet,
                bm25(sections_fts) AS rank_score
             FROM sections_fts
             JOIN sections s ON s.id = sections_fts.section_id
             JOIN chapters c ON c.id = s.chapter_id
             JOIN books b ON b.id = s.book_id
             WHERE sections_fts MATCH ?1
               AND (?2 IS NULL OR s.book_id = ?2)
             ORDER BY rank_score ASC, s.chapter_number ASC, s.position ASC
             LIMIT ?3",
        )?;

        let rows = stmt.query_map(params![fts_expr, book_param, cap], |r| {
            Ok(SearchHit {
                book_id: r.get(0)?,
                book_title: r.get(1)?,
                chapter_id: r.get(2)?,
                chapter_number: to_u32(r.get::<_, i64>(3)?),
                chapter_title: r.get(4)?,
                section_id: r.get(5)?,
                section_number: to_u32(r.get::<_, i64>(6)?),
                section_title: r.get(7)?,
                snippet: r.get(8)?,
                rank: r.get(9)?,
            })
        })?;

        let mut hits = Vec::new();
        for item in rows {
            hits.push(item?);
        }
        return Ok(hits);
    }

    let escaped = escape_like_pattern(trimmed);
    let like_pattern = format!("%{escaped}%");
    let mut stmt = conn.prepare(
        "SELECT
            s.book_id, b.title, s.chapter_id, s.chapter_number, c.title,
            s.id, s.section_number, s.title, s.content
         FROM sections s
         JOIN chapters c ON c.id = s.chapter_id
         JOIN books b ON b.id = s.book_id
         WHERE (?1 IS NULL OR s.book_id = ?1)
           AND (
               s.content LIKE ?2 ESCAPE '\\'
               OR s.title LIKE ?2 ESCAPE '\\'
               OR c.title LIKE ?2 ESCAPE '\\'
               OR b.title LIKE ?2 ESCAPE '\\'
           )
         ORDER BY s.updated_at DESC, s.chapter_number ASC, s.position ASC
         LIMIT ?3",
    )?;

    let rows = stmt.query_map(params![book_param, like_pattern, cap], |r| {
        let book_title: String = r.get(1)?;
        let chapter_title: String = r.get(4)?;
        let section_title: String = r.get(7)?;
        let content: String = r.get(8)?;
        let snippet = extract_fallback_snippet(
            trimmed,
            &content,
            &section_title,
            &chapter_title,
            &book_title,
        );
        Ok(SearchHit {
            book_id: r.get(0)?,
            book_title,
            chapter_id: r.get(2)?,
            chapter_number: to_u32(r.get::<_, i64>(3)?),
            chapter_title,
            section_id: r.get(5)?,
            section_number: to_u32(r.get::<_, i64>(6)?),
            section_title,
            snippet,
            rank: 0.0,
        })
    })?;

    let mut hits = Vec::new();
    for item in rows {
        hits.push(item?);
    }
    Ok(hits)
}

fn escape_like_pattern(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for ch in input.chars() {
        match ch {
            '\\' | '%' | '_' => {
                out.push('\\');
                out.push(ch);
            }
            _ => out.push(ch),
        }
    }
    out
}

fn extract_fallback_snippet(
    query: &str,
    content: &str,
    section_title: &str,
    chapter_title: &str,
    book_title: &str,
) -> String {
    let haystacks = [content, section_title, chapter_title, book_title];
    for text in haystacks {
        if let Some((start_byte, matched_len)) = find_case_insensitive(text, query) {
            let prefix = text.get(..start_byte).unwrap_or("");
            let matched_slice = text
                .get(start_byte..start_byte.saturating_add(matched_len))
                .unwrap_or(query);
            let prefix_chars = prefix.chars().count();
            let total_chars = text.chars().count();
            let window_start = prefix_chars.saturating_sub(20);
            let match_char_len = matched_slice.chars().count();
            let window_end = prefix_chars
                .saturating_add(match_char_len)
                .saturating_add(40)
                .min(total_chars);

            let before: String = text
                .chars()
                .skip(window_start)
                .take(prefix_chars.saturating_sub(window_start))
                .collect();
            let matched: String = text
                .chars()
                .skip(prefix_chars)
                .take(match_char_len)
                .collect();
            let after: String = text
                .chars()
                .skip(prefix_chars.saturating_add(match_char_len))
                .take(window_end.saturating_sub(prefix_chars.saturating_add(match_char_len)))
                .collect();

            let mut snippet = String::new();
            if window_start > 0 {
                snippet.push('…');
            }
            snippet.push_str(before.trim_start());
            snippet.push('«');
            snippet.push_str(&matched);
            snippet.push('»');
            snippet.push_str(after.trim_end());
            if window_end < total_chars {
                snippet.push('…');
            }
            return snippet.replace(['\r', '\n'], " ");
        }
    }
    section_title.to_string()
}

fn find_case_insensitive(haystack: &str, needle: &str) -> Option<(usize, usize)> {
    let needle_lower = needle.to_lowercase();
    if needle_lower.is_empty() {
        return None;
    }
    // Walk char boundaries safely to avoid UTF-8 slicing panics on case folding.
    for (idx, _) in haystack.char_indices() {
        let Some(slice) = haystack.get(idx..) else {
            continue;
        };
        if slice.to_lowercase().starts_with(&needle_lower) {
            for (end_off, ch) in slice.char_indices() {
                let matched_bytes = end_off.saturating_add(ch.len_utf8());
                if slice
                    .get(..matched_bytes)
                    .is_some_and(|s| s.to_lowercase() == needle_lower)
                {
                    return Some((idx, matched_bytes));
                }
            }
            return Some((idx, needle_lower.len().min(slice.len())));
        }
    }
    None
}

fn to_u32(val: i64) -> u32 {
    u32::try_from(val.max(0)).unwrap_or(u32::MAX)
}
