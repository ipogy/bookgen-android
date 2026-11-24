use rusqlite::Connection;

use crate::error::BookGenError;

const MIGRATION_V1: &str = r#"
CREATE TABLE IF NOT EXISTS books (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    theme_prompt TEXT NOT NULL DEFAULT '',
    genre_key TEXT NOT NULL DEFAULT 'tech',
    genre_name TEXT NOT NULL DEFAULT '',
    target_audience TEXT NOT NULL DEFAULT '',
    tone TEXT NOT NULL DEFAULT '',
    target_char_count INTEGER NOT NULL DEFAULT 15000,
    overview TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'draft',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS chapters (
    id TEXT PRIMARY KEY,
    book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    chapter_number INTEGER NOT NULL,
    position INTEGER NOT NULL,
    title TEXT NOT NULL,
    focus_area TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chapters_book_pos
    ON chapters (book_id, position, chapter_number);

CREATE TABLE IF NOT EXISTS sections (
    id TEXT PRIMARY KEY,
    chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    chapter_number INTEGER NOT NULL,
    section_number INTEGER NOT NULL,
    position INTEGER NOT NULL,
    title TEXT NOT NULL,
    key_focus TEXT NOT NULL DEFAULT '',
    format TEXT NOT NULL DEFAULT 'markdown',
    content TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending',
    error_message TEXT,
    word_count INTEGER NOT NULL DEFAULT 0,
    char_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sections_chapter_pos
    ON sections (chapter_id, position, section_number);
CREATE INDEX IF NOT EXISTS idx_sections_book_status
    ON sections (book_id, status);

CREATE TABLE IF NOT EXISTS section_revisions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    section_id TEXT NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    content TEXT NOT NULL,
    source TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_revisions_section_num
    ON section_revisions (section_id, revision_number DESC);

CREATE TABLE IF NOT EXISTS reading_progress (
    book_id TEXT PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    section_id TEXT REFERENCES sections(id) ON DELETE SET NULL,
    scroll_offset REAL NOT NULL DEFAULT 0.0,
    updated_at TEXT NOT NULL
);

CREATE VIRTUAL TABLE IF NOT EXISTS sections_fts USING fts5(
    section_id UNINDEXED,
    book_id UNINDEXED,
    chapter_id UNINDEXED,
    book_title,
    chapter_title,
    section_title,
    content,
    tokenize = 'trigram'
);
"#;

pub(crate) fn configure_and_migrate(
    conn: &mut Connection,
    now_iso: &str,
) -> Result<(), BookGenError> {
    conn.execute_batch(
        "
        PRAGMA journal_mode = WAL;
        PRAGMA synchronous = NORMAL;
        PRAGMA foreign_keys = ON;
        PRAGMA busy_timeout = 5000;
        ",
    )?;

    conn.execute_batch(
        "
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version INTEGER PRIMARY KEY,
            applied_at TEXT NOT NULL
        );
        ",
    )?;

    let current_version: i64 = conn.query_row(
        "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
        [],
        |row| row.get(0),
    )?;

    if current_version < 1 {
        let tx = conn.transaction()?;
        tx.execute_batch(MIGRATION_V1)?;
        tx.execute(
            "INSERT INTO schema_migrations (version, applied_at) VALUES (?1, ?2)",
            rusqlite::params![1_i64, now_iso],
        )?;
        tx.commit()?;
    }

    Ok(())
}
