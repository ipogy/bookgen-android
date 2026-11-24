pub mod importer;
pub mod ops;
pub mod schema;

use std::fs;
use std::path::Path;
use std::sync::{Arc, Mutex, MutexGuard};

use rusqlite::{params, Connection, OptionalExtension};

use crate::error::{guard_ffi, BookGenError};
use crate::models::{
    count_words_and_chars, BookDetail, BookMetadataUpdate, BookStatus, BookSummary,
    ChapterWithSections, IncompleteSection, LegacyImportReport, MarkupFormat, NewBookDraft,
    NewChapterPlan, ReadingProgress, SearchHit, SectionRecord, SectionRevision, SectionStatus,
};
use crate::store::ops::{
    insert_book_tx, insert_chapter_tx, insert_revision_tx, insert_section_tx, now_iso_utc,
    query_book_detail, query_book_summary, query_chapters_with_sections, query_incomplete_sections,
    query_list_books, query_search, query_section_by_id, query_section_revisions,
    refresh_book_status_tx, save_outline_tx, save_reading_progress_tx, sync_book_fts_tx,
    sync_section_fts_tx, validate_exact_permutation, ChapterInsert, SectionInsert,
};

/// Persistent SQLite + FTS5 repository exposed to Kotlin over UniFFI.
#[derive(uniffi::Object)]
pub struct BookStore {
    conn: Mutex<Connection>,
}

#[uniffi::export]
impl BookStore {
    /// Opens (or creates) the SQLite WAL database at `db_path` and runs schema migrations.
    #[uniffi::constructor]
    pub fn open(db_path: String) -> Result<Arc<Self>, BookGenError> {
        guard_ffi("BookStore::open", || {
            let trimmed = db_path.trim();
            if trimmed.is_empty() {
                return Err(BookGenError::invalid_input("db_path must not be blank"));
            }

            if trimmed != ":memory:" {
                let path = Path::new(trimmed);
                if let Some(parent) = path.parent().filter(|p| !p.as_os_str().is_empty()) {
                    fs::create_dir_all(parent)?;
                }
            }

            let mut conn = if trimmed == ":memory:" {
                Connection::open_in_memory()?
            } else {
                Connection::open(trimmed)?
            };
            let now = now_iso_utc();
            schema::configure_and_migrate(&mut conn, &now)?;
            Ok(Arc::new(Self {
                conn: Mutex::new(conn),
            }))
        })
    }

    /// Creates a new book draft in the store.
    pub fn create_book(&self, draft: NewBookDraft) -> Result<BookSummary, BookGenError> {
        guard_ffi("BookStore::create_book", || {
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;
            let book_id = insert_book_tx(&tx, &draft, None, BookStatus::Draft, &now, &now)?;
            tx.commit()?;
            query_book_summary(&conn, &book_id)
        })
    }

    /// Saves or replaces the outline plan (chapters + pending sections) for a book.
    pub fn save_book_outline(
        &self,
        book_id: String,
        chapters: Vec<NewChapterPlan>,
    ) -> Result<BookDetail, BookGenError> {
        guard_ffi("BookStore::save_book_outline", || {
            let trimmed_id = book_id.trim();
            if trimmed_id.is_empty() {
                return Err(BookGenError::invalid_input("book_id must not be blank"));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;
            save_outline_tx(&tx, trimmed_id, &chapters, &now)?;
            tx.commit()?;
            query_book_detail(&conn, trimmed_id)
        })
    }

    /// Lists books ordered by most recently updated, with optional genre and status filters.
    pub fn list_books(
        &self,
        genre_filter: Option<String>,
        status_filter: Option<BookStatus>,
    ) -> Result<Vec<BookSummary>, BookGenError> {
        guard_ffi("BookStore::list_books", || {
            let conn = self.lock_conn()?;
            query_list_books(&conn, genre_filter.as_deref(), status_filter)
        })
    }

    /// Loads a full book detail including chapters, sections, and summary metrics.
    pub fn get_book(&self, book_id: String) -> Result<BookDetail, BookGenError> {
        guard_ffi("BookStore::get_book", || {
            let trimmed = book_id.trim();
            if trimmed.is_empty() {
                return Err(BookGenError::invalid_input("book_id must not be blank"));
            }
            let conn = self.lock_conn()?;
            query_book_detail(&conn, trimmed)
        })
    }

    /// Updates editable metadata of an existing book and refreshes its search index.
    pub fn update_book_metadata(
        &self,
        patch: BookMetadataUpdate,
    ) -> Result<BookSummary, BookGenError> {
        guard_ffi("BookStore::update_book_metadata", || {
            let trimmed_id = patch.book_id.trim();
            let trimmed_title = patch.title.trim();
            if trimmed_id.is_empty() || trimmed_title.is_empty() {
                return Err(BookGenError::invalid_input(
                    "book_id and title must not be blank",
                ));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;
            let changed = tx.execute(
                "UPDATE books
                 SET title = ?1, overview = ?2, genre_key = ?3, genre_name = ?4,
                     target_audience = ?5, tone = ?6, updated_at = ?7
                 WHERE id = ?8",
                params![
                    trimmed_title,
                    patch.overview.trim(),
                    if patch.genre_key.trim().is_empty() {
                        "tech"
                    } else {
                        patch.genre_key.trim()
                    },
                    patch.genre_name.trim(),
                    patch.target_audience.trim(),
                    patch.tone.trim(),
                    now,
                    trimmed_id
                ],
            )?;
            if changed == 0 {
                return Err(BookGenError::not_found("book not found"));
            }
            sync_book_fts_tx(&tx, trimmed_id)?;
            tx.commit()?;
            query_book_summary(&conn, trimmed_id)
        })
    }

    /// Deletes a book and all child chapters, sections, revisions, progress, and FTS entries.
    pub fn delete_book(&self, book_id: String) -> Result<(), BookGenError> {
        guard_ffi("BookStore::delete_book", || {
            let trimmed = book_id.trim();
            if trimmed.is_empty() {
                return Err(BookGenError::invalid_input("book_id must not be blank"));
            }
            let mut conn = self.lock_conn()?;
            let tx = conn.transaction()?;
            tx.execute(
                "DELETE FROM sections_fts WHERE book_id = ?1",
                params![trimmed],
            )?;
            let deleted = tx.execute("DELETE FROM books WHERE id = ?1", params![trimmed])?;
            if deleted == 0 {
                return Err(BookGenError::not_found("book not found"));
            }
            tx.commit()?;
            Ok(())
        })
    }

    /// Saves a section's body without disturbing sibling sections, recording a revision snapshot
    /// and marking blank content as `SectionStatus::Empty`.
    pub fn upsert_section_content(
        &self,
        section_id: String,
        content: String,
        format: MarkupFormat,
        source: String,
        note: String,
    ) -> Result<SectionRecord, BookGenError> {
        guard_ffi("BookStore::upsert_section_content", || {
            let trimmed_id = section_id.trim();
            if trimmed_id.is_empty() {
                return Err(BookGenError::invalid_input("section_id must not be blank"));
            }

            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let (book_id, prev_content): (String, String) = tx
                .query_row(
                    "SELECT book_id, content FROM sections WHERE id = ?1",
                    params![trimmed_id],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("section not found"))?;

            let status = if content.trim().is_empty() {
                SectionStatus::Empty
            } else {
                SectionStatus::Completed
            };
            let (word_count, char_count) = count_words_and_chars(&content);

            tx.execute(
                "UPDATE sections
                 SET content = ?1, format = ?2, status = ?3, error_message = NULL,
                     word_count = ?4, char_count = ?5, updated_at = ?6
                 WHERE id = ?7",
                params![
                    content,
                    format.as_str(),
                    status.as_str(),
                    word_count as i64,
                    char_count as i64,
                    now,
                    trimmed_id
                ],
            )?;

            if !content.trim().is_empty() || content != prev_content {
                let rev_source = if source.trim().is_empty() {
                    "manual_edit"
                } else {
                    source.trim()
                };
                insert_revision_tx(&tx, trimmed_id, &content, rev_source, note.trim(), &now)?;
            }

            sync_section_fts_tx(&tx, trimmed_id)?;
            refresh_book_status_tx(&tx, &book_id, &now)?;
            tx.commit()?;

            query_section_by_id(&conn, trimmed_id)
        })
    }

    /// Updates a section's title and key focus metadata without discarding its text.
    pub fn update_section_meta(
        &self,
        section_id: String,
        title: String,
        key_focus: String,
    ) -> Result<SectionRecord, BookGenError> {
        guard_ffi("BookStore::update_section_meta", || {
            let trimmed_id = section_id.trim();
            let trimmed_title = title.trim();
            if trimmed_id.is_empty() || trimmed_title.is_empty() {
                return Err(BookGenError::invalid_input(
                    "section_id and title must not be blank",
                ));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;
            let changed = tx.execute(
                "UPDATE sections SET title = ?1, key_focus = ?2, updated_at = ?3 WHERE id = ?4",
                params![trimmed_title, key_focus.trim(), now, trimmed_id],
            )?;
            if changed == 0 {
                return Err(BookGenError::not_found("section not found"));
            }
            sync_section_fts_tx(&tx, trimmed_id)?;
            tx.commit()?;
            query_section_by_id(&conn, trimmed_id)
        })
    }

    /// Updates a section's writing status (for example `Generating` or `Failed`) and recalculates
    /// the parent book status.
    pub fn set_section_status(
        &self,
        section_id: String,
        status: SectionStatus,
        error_message: Option<String>,
    ) -> Result<SectionRecord, BookGenError> {
        guard_ffi("BookStore::set_section_status", || {
            let trimmed_id = section_id.trim();
            if trimmed_id.is_empty() {
                return Err(BookGenError::invalid_input("section_id must not be blank"));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let book_id: String = tx
                .query_row(
                    "SELECT book_id FROM sections WHERE id = ?1",
                    params![trimmed_id],
                    |r| r.get(0),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("section not found"))?;

            tx.execute(
                "UPDATE sections SET status = ?1, error_message = ?2, updated_at = ?3 WHERE id = ?4",
                params![status.as_str(), error_message, now, trimmed_id],
            )?;
            refresh_book_status_tx(&tx, &book_id, &now)?;
            tx.commit()?;

            query_section_by_id(&conn, trimmed_id)
        })
    }

    /// Reorders chapters within a book cleanly and updates normalized `chapter_number` and `position`.
    pub fn reorder_chapters(
        &self,
        book_id: String,
        ordered_chapter_ids: Vec<String>,
    ) -> Result<BookDetail, BookGenError> {
        guard_ffi("BookStore::reorder_chapters", || {
            let trimmed_book = book_id.trim();
            if trimmed_book.is_empty() {
                return Err(BookGenError::invalid_input("book_id must not be blank"));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let mut existing_ids = Vec::new();
            {
                let mut stmt = tx.prepare(
                    "SELECT id FROM chapters WHERE book_id = ?1 ORDER BY position ASC, chapter_number ASC",
                )?;
                let rows = stmt.query_map(params![trimmed_book], |r| r.get::<_, String>(0))?;
                for row in rows {
                    existing_ids.push(row?);
                }
            }
            if existing_ids.is_empty() {
                return Err(BookGenError::not_found(
                    "book has no chapters or does not exist",
                ));
            }
            validate_exact_permutation(&existing_ids, &ordered_chapter_ids, "chapter")?;

            for (idx, ch_id) in ordered_chapter_ids.iter().enumerate() {
                let new_num = i64::try_from(idx.saturating_add(1)).unwrap_or(i64::MAX);
                tx.execute(
                    "UPDATE chapters SET chapter_number = ?1, position = ?1, updated_at = ?2 WHERE id = ?3",
                    params![new_num, now, ch_id],
                )?;
                tx.execute(
                    "UPDATE sections SET chapter_number = ?1, updated_at = ?2 WHERE chapter_id = ?3",
                    params![new_num, now, ch_id],
                )?;
            }

            tx.execute(
                "UPDATE books SET updated_at = ?1 WHERE id = ?2",
                params![now, trimmed_book],
            )?;
            tx.commit()?;

            query_book_detail(&conn, trimmed_book)
        })
    }

    /// Reorders sections inside a chapter and updates normalized `section_number` and `position`.
    pub fn reorder_sections(
        &self,
        chapter_id: String,
        ordered_section_ids: Vec<String>,
    ) -> Result<ChapterWithSections, BookGenError> {
        guard_ffi("BookStore::reorder_sections", || {
            let trimmed_ch = chapter_id.trim();
            if trimmed_ch.is_empty() {
                return Err(BookGenError::invalid_input("chapter_id must not be blank"));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let book_id: String = tx
                .query_row(
                    "SELECT book_id FROM chapters WHERE id = ?1",
                    params![trimmed_ch],
                    |r| r.get(0),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("chapter not found"))?;

            let mut existing_ids = Vec::new();
            {
                let mut stmt = tx.prepare(
                    "SELECT id FROM sections WHERE chapter_id = ?1 ORDER BY position ASC, section_number ASC",
                )?;
                let rows = stmt.query_map(params![trimmed_ch], |r| r.get::<_, String>(0))?;
                for row in rows {
                    existing_ids.push(row?);
                }
            }
            validate_exact_permutation(&existing_ids, &ordered_section_ids, "section")?;

            for (idx, sec_id) in ordered_section_ids.iter().enumerate() {
                let new_num = i64::try_from(idx.saturating_add(1)).unwrap_or(i64::MAX);
                tx.execute(
                    "UPDATE sections SET section_number = ?1, position = ?1, updated_at = ?2 WHERE id = ?3",
                    params![new_num, now, sec_id],
                )?;
            }

            tx.execute(
                "UPDATE books SET updated_at = ?1 WHERE id = ?2",
                params![now, book_id],
            )?;
            tx.commit()?;

            let mut chapters = query_chapters_with_sections(&conn, &book_id, Some(trimmed_ch))?;
            chapters
                .pop()
                .ok_or_else(|| BookGenError::not_found("chapter not found"))
        })
    }

    /// Lists saved revision history entries for a section (newest first).
    pub fn list_section_revisions(
        &self,
        section_id: String,
    ) -> Result<Vec<SectionRevision>, BookGenError> {
        guard_ffi("BookStore::list_section_revisions", || {
            let trimmed = section_id.trim();
            if trimmed.is_empty() {
                return Err(BookGenError::invalid_input("section_id must not be blank"));
            }
            let conn = self.lock_conn()?;
            query_section_revisions(&conn, trimmed)
        })
    }

    /// Rolls a section back to a historical revision snapshot and appends a `rollback` revision.
    pub fn restore_section_revision(
        &self,
        section_id: String,
        revision_id: i64,
    ) -> Result<SectionRecord, BookGenError> {
        guard_ffi("BookStore::restore_section_revision", || {
            let trimmed_id = section_id.trim();
            if trimmed_id.is_empty() {
                return Err(BookGenError::invalid_input("section_id must not be blank"));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let (book_id, current_format): (String, String) = tx
                .query_row(
                    "SELECT book_id, format FROM sections WHERE id = ?1",
                    params![trimmed_id],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("section not found"))?;

            let (rev_num, rev_content): (i64, String) = tx
                .query_row(
                    "SELECT revision_number, content FROM section_revisions WHERE id = ?1 AND section_id = ?2",
                    params![revision_id, trimmed_id],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("revision not found for section"))?;

            let status = if rev_content.trim().is_empty() {
                SectionStatus::Empty
            } else {
                SectionStatus::Completed
            };
            let (word_count, char_count) = count_words_and_chars(&rev_content);

            tx.execute(
                "UPDATE sections
                 SET content = ?1, format = ?2, status = ?3, error_message = NULL,
                     word_count = ?4, char_count = ?5, updated_at = ?6
                 WHERE id = ?7",
                params![
                    rev_content,
                    current_format,
                    status.as_str(),
                    word_count as i64,
                    char_count as i64,
                    now,
                    trimmed_id
                ],
            )?;

            insert_revision_tx(
                &tx,
                trimmed_id,
                &rev_content,
                "rollback",
                &format!("Restored revision #{rev_num}"),
                &now,
            )?;
            sync_section_fts_tx(&tx, trimmed_id)?;
            refresh_book_status_tx(&tx, &book_id, &now)?;
            tx.commit()?;

            query_section_by_id(&conn, trimmed_id)
        })
    }

    /// Returns missing, interrupted, failed, or empty sections for resuming generation.
    pub fn list_incomplete_sections(
        &self,
        book_id: String,
    ) -> Result<Vec<IncompleteSection>, BookGenError> {
        guard_ffi("BookStore::list_incomplete_sections", || {
            let trimmed = book_id.trim();
            if trimmed.is_empty() {
                return Err(BookGenError::invalid_input("book_id must not be blank"));
            }
            let conn = self.lock_conn()?;
            query_incomplete_sections(&conn, trimmed)
        })
    }

    /// Persists reading position so the user can resume seamlessly across app restarts.
    pub fn save_reading_progress(
        &self,
        book_id: String,
        chapter_id: String,
        section_id: Option<String>,
        scroll_offset: f64,
    ) -> Result<ReadingProgress, BookGenError> {
        guard_ffi("BookStore::save_reading_progress", || {
            let trimmed_book = book_id.trim();
            let trimmed_chapter = chapter_id.trim();
            if trimmed_book.is_empty() || trimmed_chapter.is_empty() {
                return Err(BookGenError::invalid_input(
                    "book_id and chapter_id must not be blank",
                ));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;
            let progress = save_reading_progress_tx(
                &tx,
                trimmed_book,
                trimmed_chapter,
                section_id.as_deref(),
                scroll_offset,
                &now,
            )?;
            tx.commit()?;
            Ok(progress)
        })
    }

    /// Loads saved reading position for a book, if any.
    pub fn get_reading_progress(
        &self,
        book_id: String,
    ) -> Result<Option<ReadingProgress>, BookGenError> {
        guard_ffi("BookStore::get_reading_progress", || {
            let trimmed = book_id.trim();
            if trimmed.is_empty() {
                return Err(BookGenError::invalid_input("book_id must not be blank"));
            }
            let conn = self.lock_conn()?;
            conn.query_row(
                "SELECT book_id, chapter_id, section_id, scroll_offset, updated_at
                 FROM reading_progress WHERE book_id = ?1",
                params![trimmed],
                |r| {
                    Ok(ReadingProgress {
                        book_id: r.get(0)?,
                        chapter_id: r.get(1)?,
                        section_id: r.get(2)?,
                        scroll_offset: r.get(3)?,
                        updated_at: r.get(4)?,
                    })
                },
            )
            .optional()
            .map_err(BookGenError::from)
        })
    }

    /// Full-text search across book titles, chapter titles, section titles, and bodies using
    /// FTS5 trigram matching with contextual snippet highlighting.
    pub fn search(
        &self,
        query: String,
        book_id_filter: Option<String>,
        limit: u32,
    ) -> Result<Vec<SearchHit>, BookGenError> {
        guard_ffi("BookStore::search", || {
            let conn = self.lock_conn()?;
            query_search(&conn, &query, book_id_filter.as_deref(), limit)
        })
    }

    /// Appends a new chapter at the end of a book's outline.
    pub fn add_chapter(
        &self,
        book_id: String,
        title: String,
        focus_area: String,
    ) -> Result<ChapterWithSections, BookGenError> {
        guard_ffi("BookStore::add_chapter", || {
            let trimmed_book = book_id.trim();
            let trimmed_title = title.trim();
            if trimmed_book.is_empty() || trimmed_title.is_empty() {
                return Err(BookGenError::invalid_input(
                    "book_id and title must not be blank",
                ));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let exists: Option<String> = tx
                .query_row(
                    "SELECT id FROM books WHERE id = ?1",
                    params![trimmed_book],
                    |r| r.get(0),
                )
                .optional()?;
            if exists.is_none() {
                return Err(BookGenError::not_found("book not found"));
            }

            let next_pos: i64 = tx.query_row(
                "SELECT COALESCE(MAX(position), 0) + 1 FROM chapters WHERE book_id = ?1",
                params![trimmed_book],
                |r| r.get(0),
            )?;
            let pos = u32::try_from(next_pos.max(1)).unwrap_or(1);
            let ch_id = insert_chapter_tx(
                &tx,
                ChapterInsert {
                    book_id: trimmed_book,
                    chapter_number: pos,
                    position: pos,
                    title: trimmed_title,
                    focus_area: focus_area.trim(),
                    created_at: &now,
                    updated_at: &now,
                },
            )?;
            refresh_book_status_tx(&tx, trimmed_book, &now)?;
            tx.commit()?;

            let mut list = query_chapters_with_sections(&conn, trimmed_book, Some(&ch_id))?;
            list.pop()
                .ok_or_else(|| BookGenError::not_found("chapter not found"))
        })
    }

    /// Updates a chapter's title and focus area and refreshes the search index.
    pub fn update_chapter_meta(
        &self,
        chapter_id: String,
        title: String,
        focus_area: String,
    ) -> Result<ChapterWithSections, BookGenError> {
        guard_ffi("BookStore::update_chapter_meta", || {
            let trimmed_id = chapter_id.trim();
            let trimmed_title = title.trim();
            if trimmed_id.is_empty() || trimmed_title.is_empty() {
                return Err(BookGenError::invalid_input(
                    "chapter_id and title must not be blank",
                ));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let book_id: String = tx
                .query_row(
                    "SELECT book_id FROM chapters WHERE id = ?1",
                    params![trimmed_id],
                    |r| r.get(0),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("chapter not found"))?;

            tx.execute(
                "UPDATE chapters SET title = ?1, focus_area = ?2, updated_at = ?3 WHERE id = ?4",
                params![trimmed_title, focus_area.trim(), now, trimmed_id],
            )?;
            sync_book_fts_tx(&tx, &book_id)?;
            tx.execute(
                "UPDATE books SET updated_at = ?1 WHERE id = ?2",
                params![now, book_id],
            )?;
            tx.commit()?;

            let mut list = query_chapters_with_sections(&conn, &book_id, Some(trimmed_id))?;
            list.pop()
                .ok_or_else(|| BookGenError::not_found("chapter not found"))
        })
    }

    /// Appends a pending section to the end of a chapter.
    pub fn add_section(
        &self,
        chapter_id: String,
        title: String,
        key_focus: String,
    ) -> Result<SectionRecord, BookGenError> {
        guard_ffi("BookStore::add_section", || {
            let trimmed_ch = chapter_id.trim();
            let trimmed_title = title.trim();
            if trimmed_ch.is_empty() || trimmed_title.is_empty() {
                return Err(BookGenError::invalid_input(
                    "chapter_id and title must not be blank",
                ));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let (book_id, ch_num_raw): (String, i64) = tx
                .query_row(
                    "SELECT book_id, chapter_number FROM chapters WHERE id = ?1",
                    params![trimmed_ch],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("chapter not found"))?;

            let next_pos_raw: i64 = tx.query_row(
                "SELECT COALESCE(MAX(position), 0) + 1 FROM sections WHERE chapter_id = ?1",
                params![trimmed_ch],
                |r| r.get(0),
            )?;
            let ch_num = u32::try_from(ch_num_raw.max(1)).unwrap_or(1);
            let next_pos = u32::try_from(next_pos_raw.max(1)).unwrap_or(1);

            let sec_id = insert_section_tx(
                &tx,
                SectionInsert {
                    chapter_id: trimmed_ch,
                    book_id: &book_id,
                    chapter_number: ch_num,
                    section_number: next_pos,
                    position: next_pos,
                    title: trimmed_title,
                    key_focus: key_focus.trim(),
                    format: MarkupFormat::Markdown,
                    content: "",
                    status: SectionStatus::Pending,
                    error_message: None,
                    created_at: &now,
                    updated_at: &now,
                    revision_source: None,
                    revision_note: "",
                },
            )?;
            refresh_book_status_tx(&tx, &book_id, &now)?;
            tx.commit()?;

            query_section_by_id(&conn, &sec_id)
        })
    }

    /// Deletes a single section, reindexes sibling section numbers, and updates book status.
    pub fn delete_section(&self, section_id: String) -> Result<BookDetail, BookGenError> {
        guard_ffi("BookStore::delete_section", || {
            let trimmed = section_id.trim();
            if trimmed.is_empty() {
                return Err(BookGenError::invalid_input("section_id must not be blank"));
            }
            let mut conn = self.lock_conn()?;
            let now = now_iso_utc();
            let tx = conn.transaction()?;

            let (book_id, chapter_id): (String, String) = tx
                .query_row(
                    "SELECT book_id, chapter_id FROM sections WHERE id = ?1",
                    params![trimmed],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )
                .optional()?
                .ok_or_else(|| BookGenError::not_found("section not found"))?;

            tx.execute(
                "DELETE FROM sections_fts WHERE section_id = ?1",
                params![trimmed],
            )?;
            tx.execute("DELETE FROM sections WHERE id = ?1", params![trimmed])?;

            let mut sibling_ids = Vec::new();
            {
                let mut stmt = tx.prepare(
                    "SELECT id FROM sections WHERE chapter_id = ?1 ORDER BY position ASC, section_number ASC",
                )?;
                let rows = stmt.query_map(params![chapter_id], |r| r.get::<_, String>(0))?;
                for row in rows {
                    sibling_ids.push(row?);
                }
            }
            for (idx, sib_id) in sibling_ids.iter().enumerate() {
                let new_num = i64::try_from(idx.saturating_add(1)).unwrap_or(i64::MAX);
                tx.execute(
                    "UPDATE sections SET section_number = ?1, position = ?1 WHERE id = ?2",
                    params![new_num, sib_id],
                )?;
            }

            refresh_book_status_tx(&tx, &book_id, &now)?;
            tx.commit()?;

            query_book_detail(&conn, &book_id)
        })
    }

    /// Imports legacy Web `bookgen` `app_data.db` and `books/<id>/book.json` + chapter files
    /// idempotently, detecting empty sections and normalizing section numbers.
    pub fn import_legacy_bookgen(
        &self,
        legacy_books_dir: String,
    ) -> Result<LegacyImportReport, BookGenError> {
        guard_ffi("BookStore::import_legacy_bookgen", || {
            let mut conn = self.lock_conn()?;
            importer::run_legacy_import(&mut conn, &legacy_books_dir)
        })
    }
}

impl BookStore {
    fn lock_conn(&self) -> Result<MutexGuard<'_, Connection>, BookGenError> {
        self.conn
            .lock()
            .map_err(|_| BookGenError::storage("database mutex poisoned"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::NewSectionPlan;
    use rusqlite::{params, Connection};
    use serde_json::json;
    use std::fs;
    use tempfile::TempDir;

    fn sample_draft(title: &str) -> NewBookDraft {
        NewBookDraft {
            id: None,
            title: title.to_string(),
            theme_prompt: "Distributed Rust systems on mobile".to_string(),
            genre_key: "tech".to_string(),
            genre_name: "Technical & Engineering".to_string(),
            target_audience: "Mobile developers".to_string(),
            tone: "Concise and practical".to_string(),
            target_char_count: 15000,
            overview: "A practical guide to Kotlin + Rust UniFFI.".to_string(),
        }
    }

    #[test]
    fn crud_outline_section_edit_preserves_siblings_and_revisions(
    ) -> Result<(), Box<dyn std::error::Error>> {
        let store = BookStore::open(":memory:".to_string())?;
        let book = store.create_book(sample_draft("Rust on Android"))?;
        assert_eq!(book.status, BookStatus::Draft);

        let detail = store.save_book_outline(
            book.id.clone(),
            vec![
                NewChapterPlan {
                    chapter_number: 1,
                    title: "Architecture".to_string(),
                    focus_area: "FFI boundaries".to_string(),
                    sections: vec![
                        NewSectionPlan {
                            section_number: 1,
                            title: "Overview".to_string(),
                            key_focus: "Why Rust".to_string(),
                        },
                        NewSectionPlan {
                            section_number: 2,
                            title: "UniFFI Setup".to_string(),
                            key_focus: "Gradle wiring".to_string(),
                        },
                    ],
                },
                NewChapterPlan {
                    chapter_number: 2,
                    title: "Persistence".to_string(),
                    focus_area: "SQLite WAL".to_string(),
                    sections: vec![NewSectionPlan {
                        section_number: 1,
                        title: "FTS5 Search".to_string(),
                        key_focus: "Trigrams".to_string(),
                    }],
                },
            ],
        )?;
        assert_eq!(detail.summary.status, BookStatus::Outlined);
        assert_eq!(detail.chapters.len(), 2);

        let ch1 = detail.chapters.first().ok_or("missing ch1")?;
        let sec1_id = ch1.sections.first().ok_or("missing sec1")?.id.clone();
        let sec2_id = ch1.sections.get(1).ok_or("missing sec2")?.id.clone();

        // Populate sec1 and sec2 independently
        store.upsert_section_content(
            sec1_id.clone(),
            "Rust provides deterministic memory safety across FFI.".to_string(),
            MarkupFormat::Markdown,
            "ai_generate".to_string(),
            "Draft 1".to_string(),
        )?;
        store.upsert_section_content(
            sec2_id.clone(),
            "Use cargo-ndk with 16 KiB ELF alignment.".to_string(),
            MarkupFormat::Markdown,
            "ai_generate".to_string(),
            "Draft 1".to_string(),
        )?;

        // Update sec1 without disturbing sec2 (regression check for legacy update_chapter bug)
        let edited = store.upsert_section_content(
            sec1_id.clone(),
            "日本語とRustを組み合わせた安全な設計。".to_string(),
            MarkupFormat::Markdown,
            "manual_edit".to_string(),
            "Edited title".to_string(),
        )?;
        assert_eq!(edited.status, SectionStatus::Completed);
        assert_eq!(edited.revision_count, 2);

        let after_edit = store.get_book(book.id.clone())?;
        assert_eq!(after_edit.summary.status, BookStatus::Partial);
        let ch1_after = after_edit.chapters.first().ok_or("missing ch1")?;
        assert_eq!(
            ch1_after.sections.get(1).ok_or("missing sec2")?.content,
            "Use cargo-ndk with 16 KiB ELF alignment."
        );

        // Rollback sec1 to first revision
        let revs = store.list_section_revisions(sec1_id.clone())?;
        assert_eq!(revs.len(), 2);
        let first_rev = revs.last().ok_or("missing first rev")?;
        let restored = store.restore_section_revision(sec1_id, first_rev.id)?;
        assert_eq!(
            restored.content,
            "Rust provides deterministic memory safety across FFI."
        );
        assert_eq!(restored.revision_count, 3);

        Ok(())
    }

    #[test]
    fn empty_sections_incomplete_resume_reorder_and_progress(
    ) -> Result<(), Box<dyn std::error::Error>> {
        let store = BookStore::open(":memory:".to_string())?;
        let book = store.create_book(sample_draft("Resumable Book"))?;
        let detail = store.save_book_outline(
            book.id.clone(),
            vec![
                NewChapterPlan {
                    chapter_number: 1,
                    title: "First".to_string(),
                    focus_area: String::new(),
                    sections: vec![
                        NewSectionPlan {
                            section_number: 1,
                            title: "Sec A".to_string(),
                            key_focus: String::new(),
                        },
                        NewSectionPlan {
                            section_number: 2,
                            title: "Sec B".to_string(),
                            key_focus: String::new(),
                        },
                    ],
                },
                NewChapterPlan {
                    chapter_number: 2,
                    title: "Second".to_string(),
                    focus_area: String::new(),
                    sections: vec![NewSectionPlan {
                        section_number: 1,
                        title: "Sec C".to_string(),
                        key_focus: String::new(),
                    }],
                },
            ],
        )?;

        let ch1 = detail.chapters.first().ok_or("missing ch1")?;
        let ch2 = detail.chapters.get(1).ok_or("missing ch2")?;
        let sec_a = ch1.sections.first().ok_or("missing sec_a")?.id.clone();
        let sec_b = ch1.sections.get(1).ok_or("missing sec_b")?.id.clone();
        let sec_c = ch2.sections.first().ok_or("missing sec_c")?.id.clone();

        // Whitespace-only content must be flagged SectionStatus::Empty rather than Completed.
        let empty_sec = store.upsert_section_content(
            sec_a.clone(),
            "   \n\t  ".to_string(),
            MarkupFormat::AsciiDoc,
            "ai_generate".to_string(),
            String::new(),
        )?;
        assert_eq!(empty_sec.status, SectionStatus::Empty);

        store.set_section_status(
            sec_b.clone(),
            SectionStatus::Failed,
            Some("HTTP 429".to_string()),
        )?;
        store.upsert_section_content(
            sec_c.clone(),
            "Completed chapter 2 body.".to_string(),
            MarkupFormat::Markdown,
            "ai_generate".to_string(),
            String::new(),
        )?;

        let incomplete = store.list_incomplete_sections(book.id.clone())?;
        assert_eq!(incomplete.len(), 2);
        assert_eq!(
            incomplete.first().ok_or("inc0")?.status,
            SectionStatus::Empty
        );
        assert_eq!(
            incomplete.get(1).ok_or("inc1")?.status,
            SectionStatus::Failed
        );

        // Reorder chapters
        let reordered = store.reorder_chapters(
            book.id.clone(),
            vec![ch2.chapter.id.clone(), ch1.chapter.id.clone()],
        )?;
        assert_eq!(
            reordered.chapters.first().ok_or("r0")?.chapter.title,
            "Second"
        );
        assert_eq!(
            reordered
                .chapters
                .first()
                .ok_or("r0")?
                .chapter
                .chapter_number,
            1
        );

        // Save and read reading progress
        let saved = store.save_reading_progress(
            book.id.clone(),
            ch2.chapter.id.clone(),
            Some(sec_c),
            0.42,
        )?;
        assert!((saved.scroll_offset - 0.42).abs() < f64::EPSILON);
        assert!(store.get_reading_progress(book.id)?.is_some());

        Ok(())
    }

    #[test]
    fn fts5_trigram_and_short_cjk_search_return_snippets() -> Result<(), Box<dyn std::error::Error>>
    {
        let store = BookStore::open(":memory:".to_string())?;
        let book = store.create_book(sample_draft("Quantum Computing入門"))?;
        let detail = store.save_book_outline(
            book.id.clone(),
            vec![NewChapterPlan {
                chapter_number: 1,
                title: "量子ビットの基礎".to_string(),
                focus_area: String::new(),
                sections: vec![NewSectionPlan {
                    section_number: 1,
                    title: "Superposition".to_string(),
                    key_focus: String::new(),
                }],
            }],
        )?;
        let sec_id = detail
            .chapters
            .first()
            .and_then(|c| c.sections.first())
            .ok_or("missing sec")?
            .id
            .clone();
        store.upsert_section_content(
            sec_id,
            "量子アルゴリズムとC++実装、そしてUniffi境界設計について詳説する。".to_string(),
            MarkupFormat::Markdown,
            "ai_generate".to_string(),
            String::new(),
        )?;

        // 3+ char trigram search (Japanese + punctuation-heavy English)
        let fts_hits = store.search("アルゴリズム".to_string(), None, 10)?;
        assert_eq!(fts_hits.len(), 1);
        assert!(fts_hits
            .first()
            .ok_or("hit0")?
            .snippet
            .contains("«アルゴリズム»"));

        let cpp_hits = store.search("C++".to_string(), Some(book.id.clone()), 10)?;
        assert_eq!(cpp_hits.len(), 1);

        // < 3 char CJK fallback search
        let short_hits = store.search("量子".to_string(), Some(book.id), 10)?;
        assert_eq!(short_hits.len(), 1);
        assert!(short_hits
            .first()
            .ok_or("short0")?
            .snippet
            .contains("«量子»"));

        Ok(())
    }

    #[test]
    fn legacy_importer_handles_db_quirks_and_disk_books_idempotently(
    ) -> Result<(), Box<dyn std::error::Error>> {
        let temp = TempDir::new()?;
        let legacy_root = temp.path().join("books");
        fs::create_dir_all(&legacy_root)?;

        // 1) Legacy SQLite app_data.db with anomalous section numbers (71..72) & empty content
        let legacy_db_path = legacy_root.join("app_data.db");
        {
            let conn = Connection::open(&legacy_db_path)?;
            conn.execute_batch(
                "
                CREATE TABLE books (id TEXT PRIMARY KEY, title TEXT NOT NULL, genre_key TEXT, genre_name TEXT, target_audience TEXT, overview TEXT, plan_json TEXT NOT NULL, created_at TEXT NOT NULL);
                CREATE TABLE chapters (book_id TEXT NOT NULL, chapter_number INTEGER NOT NULL, content TEXT NOT NULL, updated_at TEXT NOT NULL, PRIMARY KEY (book_id, chapter_number));
                CREATE TABLE chapter_sections (book_id TEXT NOT NULL, chapter_number INTEGER NOT NULL, section_number INTEGER NOT NULL, content TEXT NOT NULL, updated_at TEXT NOT NULL, PRIMARY KEY (book_id, chapter_number, section_number));
                ",
            )?;
            let plan = json!({
                "id": "db_book_1",
                "book_title": "DB Legacy Guide",
                "genre_key": "self_help_career",
                "genre_name": "Self Help",
                "target_audience": "Engineers",
                "overview": "Imported DB test",
                "chapters": [
                    {
                        "chapter_number": 7,
                        "title": "Chapter Seven",
                        "focus_area": "Habits",
                        "sections": [
                            {"section_number": 71, "title": "Routine", "key_focus": "Morning"},
                            {"section_number": 72, "title": "Empty Slot", "key_focus": "Blank"}
                        ]
                    }
                ]
            });
            conn.execute(
                "INSERT INTO books VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
                params![
                    "db_book_1",
                    "DB Legacy Guide",
                    "self_help_career",
                    "Self Help",
                    "Engineers",
                    "Imported DB test",
                    plan.to_string(),
                    "2026-01-01T00:00:00Z"
                ],
            )?;
            conn.execute(
                "INSERT INTO chapter_sections VALUES ('db_book_1', 7, 71, '== Habit\nGood routine body.', '2026-01-01T00:10:00Z')",
                [],
            )?;
            conn.execute(
                "INSERT INTO chapter_sections VALUES ('db_book_1', 7, 72, '   ', '2026-01-01T00:11:00Z')",
                [],
            )?;
        }

        // 2) Legacy disk folder with book.json + chapter_1.md + .ratelimit dotdir (ignored)
        fs::create_dir_all(legacy_root.join(".ratelimit"))?;
        let fs_dir = legacy_root.join("fs_book_2");
        fs::create_dir_all(&fs_dir)?;
        fs::write(
            fs_dir.join("book.json"),
            json!({
                "id": "fs_book_2",
                "book_title": "Filesystem Book",
                "genre_key": "tech",
                "chapters": [
                    {
                        "chapter_number": 1,
                        "title": "Getting Started",
                        "focus_area": "Basics",
                        "sections": [
                            {"section_number": 1, "title": "Install", "key_focus": "Setup"},
                            {"section_number": 2, "title": "Verify", "key_focus": "Test"}
                        ]
                    }
                ]
            })
            .to_string(),
        )?;
        fs::write(
            fs_dir.join("chapter_1.md"),
            "# Chapter 1\n\n## Install\nRun installer.\n\n## Verify\nCheck output.\n",
        )?;

        let store = BookStore::open(temp.path().join("android.db").to_string_lossy().to_string())?;
        let report = store.import_legacy_bookgen(legacy_root.to_string_lossy().to_string())?;
        assert_eq!(report.db_books_imported, 1);
        assert_eq!(report.fs_books_imported, 1);
        assert_eq!(report.empty_sections_flagged, 1);
        assert!(report.normalized_section_numbers >= 2);
        assert!(report.warnings.is_empty());

        let db_book = store.get_book("db_book_1".to_string())?;
        let db_sec0 = db_book
            .chapters
            .first()
            .and_then(|c| c.sections.first())
            .ok_or("missing db_sec0")?;
        assert_eq!(db_sec0.section_number, 1);
        assert_eq!(db_sec0.format, MarkupFormat::AsciiDoc);
        assert_eq!(
            db_book
                .chapters
                .first()
                .and_then(|c| c.sections.get(1))
                .ok_or("sec1")?
                .status,
            SectionStatus::Empty
        );

        // Second run must be idempotent
        let second_report =
            store.import_legacy_bookgen(legacy_root.to_string_lossy().to_string())?;
        assert_eq!(second_report.db_books_imported, 0);
        assert_eq!(second_report.fs_books_imported, 0);
        assert_eq!(second_report.books_skipped_existing, 2);

        Ok(())
    }
}
