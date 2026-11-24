use bookgen_core::{
    BookGenError, BookMetadataUpdate, BookStatus, BookStore, MarkupFormat, NewBookDraft,
    NewChapterPlan, NewSectionPlan, SectionStatus,
};
use rusqlite::{params, Connection};
use serde_json::json;
use std::fs;
use tempfile::TempDir;

fn make_draft(id: Option<&str>, title: &str) -> NewBookDraft {
    NewBookDraft {
        id: id.map(ToString::to_string),
        title: title.to_string(),
        theme_prompt: format!("Theme for {title}"),
        genre_key: "tech".to_string(),
        genre_name: "Technical & Engineering".to_string(),
        target_audience: "Software Engineers".to_string(),
        tone: "Practical".to_string(),
        target_char_count: 15000,
        overview: format!("Overview of {title}"),
    }
}

#[test]
fn validation_and_conflict_errors_are_structured() {
    assert!(matches!(
        BookStore::open("   ".to_string()),
        Err(BookGenError::InvalidInput { .. })
    ));

    let store = BookStore::open(":memory:".to_string()).expect("open memory store");
    assert!(matches!(
        store.create_book(make_draft(None, "   ")),
        Err(BookGenError::InvalidInput { .. })
    ));

    let mut bad_chars = make_draft(None, "Negative Chars");
    bad_chars.target_char_count = -1;
    assert!(matches!(
        store.create_book(bad_chars),
        Err(BookGenError::InvalidInput { .. })
    ));

    let first = store
        .create_book(make_draft(Some("dup_id"), "First"))
        .expect("create first");
    assert_eq!(first.id, "dup_id");
    assert!(matches!(
        store.create_book(make_draft(Some("dup_id"), "Second")),
        Err(BookGenError::Conflict { .. })
    ));

    assert!(matches!(
        store.get_book("nonexistent".to_string()),
        Err(BookGenError::NotFound { .. })
    ));
}

#[test]
fn metadata_update_chapter_section_mutations_delete_and_fts_cleanup() {
    let store = BookStore::open(":memory:".to_string()).expect("open memory store");
    let created = store
        .create_book(make_draft(None, "Initial Book"))
        .expect("create book");

    let ch1 = store
        .add_chapter(
            created.id.clone(),
            "Chapter One".to_string(),
            "Foundations".to_string(),
        )
        .expect("add ch1");
    let sec1 = store
        .add_section(
            ch1.chapter.id.clone(),
            "Section Alpha".to_string(),
            "Core concepts".to_string(),
        )
        .expect("add sec1");
    let sec2 = store
        .add_section(
            ch1.chapter.id.clone(),
            "Section Beta".to_string(),
            "Examples".to_string(),
        )
        .expect("add sec2");

    store
        .upsert_section_content(
            sec1.id.clone(),
            "Alpha body mentioning observability.".to_string(),
            MarkupFormat::Markdown,
            String::new(),
            String::new(),
        )
        .expect("populate sec1");
    store
        .upsert_section_content(
            sec2.id.clone(),
            "Beta body mentioning scalability.".to_string(),
            MarkupFormat::Markdown,
            String::new(),
            String::new(),
        )
        .expect("populate sec2");

    let updated_meta = store
        .update_book_metadata(BookMetadataUpdate {
            book_id: created.id.clone(),
            title: "Renamed Book".to_string(),
            overview: "New overview".to_string(),
            genre_key: "business".to_string(),
            genre_name: "Business".to_string(),
            target_audience: "Managers".to_string(),
            tone: "Executive".to_string(),
        })
        .expect("update meta");
    assert_eq!(updated_meta.title, "Renamed Book");

    let renamed_ch = store
        .update_chapter_meta(
            ch1.chapter.id.clone(),
            "Renamed Chapter".to_string(),
            "Updated focus".to_string(),
        )
        .expect("update ch meta");
    assert_eq!(renamed_ch.chapter.title, "Renamed Chapter");

    // Reorder sections inside chapter & verify validation errors
    assert!(matches!(
        store.reorder_sections(ch1.chapter.id.clone(), vec![sec1.id.clone()]),
        Err(BookGenError::InvalidInput { .. })
    ));
    assert!(matches!(
        store.reorder_sections(
            ch1.chapter.id.clone(),
            vec![sec1.id.clone(), sec1.id.clone()],
        ),
        Err(BookGenError::InvalidInput { .. })
    ));
    assert!(matches!(
        store.reorder_sections(
            ch1.chapter.id.clone(),
            vec![sec1.id.clone(), "foreign_id".to_string()],
        ),
        Err(BookGenError::InvalidInput { .. })
    ));

    let reordered = store
        .reorder_sections(
            ch1.chapter.id.clone(),
            vec![sec2.id.clone(), sec1.id.clone()],
        )
        .expect("reorder sections");
    assert_eq!(reordered.sections[0].id, sec2.id);
    assert_eq!(reordered.sections[0].section_number, 1);
    assert_eq!(reordered.sections[1].id, sec1.id);
    assert_eq!(reordered.sections[1].section_number, 2);

    // Search hits reflect renamed book/chapter and punctuation/multi-token queries
    let hits = store
        .search("\"observability\" \"Renamed\"".to_string(), None, 10)
        .expect("fts search");
    assert_eq!(hits.len(), 1);
    assert_eq!(hits[0].book_title, "Renamed Book");
    assert_eq!(hits[0].chapter_title, "Renamed Chapter");

    // Delete section reindexes remaining siblings cleanly
    let after_del_sec = store.delete_section(sec2.id.clone()).expect("delete sec2");
    assert_eq!(after_del_sec.chapters[0].sections.len(), 1);
    assert_eq!(after_del_sec.chapters[0].sections[0].section_number, 1);

    // Reading progress validation
    assert!(matches!(
        store.save_reading_progress(created.id.clone(), ch1.chapter.id.clone(), None, f64::NAN),
        Err(BookGenError::InvalidInput { .. })
    ));
    assert!(matches!(
        store.save_reading_progress(created.id.clone(), "wrong_ch".to_string(), None, 0.5),
        Err(BookGenError::NotFound { .. })
    ));
    let clamped = store
        .save_reading_progress(
            created.id.clone(),
            ch1.chapter.id.clone(),
            Some(sec1.id.clone()),
            3.5,
        )
        .expect("save clamped progress");
    assert!((clamped.scroll_offset - 1.0).abs() < f64::EPSILON);

    // Delete book cleans FTS index completely
    store.delete_book(created.id.clone()).expect("delete book");
    assert!(store.list_books(None, None).expect("list").is_empty());
    assert!(store
        .search("observability".to_string(), None, 10)
        .expect("search after delete")
        .is_empty());
}

#[test]
fn importer_handles_malformed_plan_json_legacy_chapters_table_and_orphans() {
    let temp = TempDir::new().expect("tempdir");
    let legacy_root = temp.path().join("books");
    fs::create_dir_all(&legacy_root).expect("mkdir");

    let db_path = legacy_root.join("app_data.db");
    {
        let conn = Connection::open(&db_path).expect("open legacy db");
        conn.execute_batch(
            "
            CREATE TABLE books (id TEXT PRIMARY KEY, title TEXT NOT NULL, genre_key TEXT, genre_name TEXT, target_audience TEXT, overview TEXT, plan_json TEXT NOT NULL, created_at TEXT NOT NULL);
            CREATE TABLE chapters (book_id TEXT NOT NULL, chapter_number INTEGER NOT NULL, content TEXT NOT NULL, updated_at TEXT NOT NULL, PRIMARY KEY (book_id, chapter_number));
            CREATE TABLE chapter_sections (book_id TEXT NOT NULL, chapter_number INTEGER NOT NULL, section_number INTEGER NOT NULL, content TEXT NOT NULL, updated_at TEXT NOT NULL, PRIMARY KEY (book_id, chapter_number, section_number));
            ",
        )
        .expect("create legacy tables");

        // Broken JSON book triggers a non-fatal warning
        conn.execute(
            "INSERT INTO books VALUES ('broken_json', 'Broken', 'tech', 'Tech', '', '', '{not valid json', '2026-01-01T00:00:00Z')",
            [],
        )
        .expect("insert broken book");

        // Pre-sections legacy book using `chapters` blob table + `=== ` headings
        let plan_blob = json!({
            "id": "blob_book",
            "book_title": "Legacy Blob Book",
            "genre_key": "tech",
            "chapters": [{
                "chapter_number": 1,
                "title": "Ch 1",
                "focus_area": "Intro",
                "sections": [
                    {"section_number": 1, "title": "First Sub", "key_focus": "A"},
                    {"section_number": 2, "title": "Second Sub", "key_focus": "B"}
                ]
            }]
        });
        conn.execute(
            "INSERT INTO books VALUES ('blob_book', 'Legacy Blob Book', 'tech', 'Tech', '', '', ?1, '2026-01-02T00:00:00Z')",
            params![plan_blob.to_string()],
        )
        .expect("insert blob_book");
        conn.execute(
            "INSERT INTO chapters VALUES ('blob_book', 1, '== Ch 1\n=== First Sub\nBody one.\n=== Second Sub\nBody two.\n=== Extra Unplanned\nOrphan bonus body.', '2026-01-02T00:05:00Z')",
            [],
        )
        .expect("insert legacy chapter blob");

        // Book with orphan extra row in chapter_sections + missing planned section
        let plan_orphan = json!({
            "id": "orphan_book",
            "book_title": "Orphan Row Book",
            "genre_key": "tech",
            "chapters": [{
                "chapter_number": 1,
                "title": "Ch 1",
                "sections": [
                    {"section_number": 1, "title": "Planned Only", "key_focus": "P"}
                ]
            }]
        });
        conn.execute(
            "INSERT INTO books VALUES ('orphan_book', 'Orphan Row Book', 'tech', 'Tech', '', '', ?1, '2026-01-03T00:00:00Z')",
            params![plan_orphan.to_string()],
        )
        .expect("insert orphan_book");
        conn.execute(
            "INSERT INTO chapter_sections VALUES ('orphan_book', 1, 9, 'Extra generated text.', '2026-01-03T00:05:00Z')",
            [],
        )
        .expect("insert orphan row");
    }

    let store = BookStore::open(temp.path().join("target.db").to_string_lossy().to_string())
        .expect("open target");
    let report = store
        .import_legacy_bookgen(legacy_root.to_string_lossy().to_string())
        .expect("run import");
    assert_eq!(report.db_books_imported, 2);
    assert_eq!(report.warnings.len(), 1);

    let blob_detail = store
        .get_book("blob_book".to_string())
        .expect("get blob_book");
    assert_eq!(blob_detail.chapters[0].sections.len(), 3);
    assert_eq!(blob_detail.summary.status, BookStatus::Completed);

    let orphan_detail = store
        .get_book("orphan_book".to_string())
        .expect("get orphan_book");
    assert_eq!(orphan_detail.chapters[0].sections.len(), 2);
    assert_eq!(
        orphan_detail.chapters[0].sections[0].status,
        SectionStatus::Pending
    );
    assert_eq!(
        orphan_detail.chapters[0].sections[1].status,
        SectionStatus::Completed
    );
    assert_eq!(orphan_detail.summary.status, BookStatus::Partial);

    // Filter list_books by status and genre
    assert_eq!(
        store
            .list_books(Some("tech".to_string()), Some(BookStatus::Partial))
            .expect("filter list")
            .len(),
        1
    );

    // Also verify outline replacement preserves nothing broken & updates status
    let replaced = store
        .save_book_outline(
            "orphan_book".to_string(),
            vec![NewChapterPlan {
                chapter_number: 1,
                title: "Clean Restart".to_string(),
                focus_area: String::new(),
                sections: vec![NewSectionPlan {
                    section_number: 1,
                    title: "Fresh".to_string(),
                    key_focus: String::new(),
                }],
            }],
        )
        .expect("replace outline");
    assert_eq!(replaced.summary.status, BookStatus::Outlined);
}
