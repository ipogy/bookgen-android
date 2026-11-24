use crate::error::BookGenError;

/// Lifecycle state of a generated book.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum BookStatus {
    Draft,
    Outlined,
    Generating,
    Completed,
    Partial,
    Failed,
}

impl BookStatus {
    pub(crate) fn as_str(self) -> &'static str {
        match self {
            Self::Draft => "draft",
            Self::Outlined => "outlined",
            Self::Generating => "generating",
            Self::Completed => "completed",
            Self::Partial => "partial",
            Self::Failed => "failed",
        }
    }

    pub(crate) fn from_db(value: &str) -> Self {
        match value {
            "draft" => Self::Draft,
            "outlined" => Self::Outlined,
            "generating" => Self::Generating,
            "completed" => Self::Completed,
            "partial" => Self::Partial,
            "failed" => Self::Failed,
            _ => Self::Draft,
        }
    }
}

/// Persistent per-section writing status enabling reliable resume.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SectionStatus {
    Pending,
    Generating,
    Completed,
    Failed,
    Empty,
}

impl SectionStatus {
    pub(crate) fn as_str(self) -> &'static str {
        match self {
            Self::Pending => "pending",
            Self::Generating => "generating",
            Self::Completed => "completed",
            Self::Failed => "failed",
            Self::Empty => "empty",
        }
    }

    pub(crate) fn from_db(value: &str) -> Result<Self, BookGenError> {
        match value {
            "pending" => Ok(Self::Pending),
            "generating" => Ok(Self::Generating),
            "completed" => Ok(Self::Completed),
            "failed" => Ok(Self::Failed),
            "empty" => Ok(Self::Empty),
            _ => Err(BookGenError::storage("unknown section status in database")),
        }
    }
}

/// Source markup format of a written section.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MarkupFormat {
    Markdown,
    AsciiDoc,
}

impl MarkupFormat {
    pub(crate) fn as_str(self) -> &'static str {
        match self {
            Self::Markdown => "markdown",
            Self::AsciiDoc => "asciidoc",
        }
    }

    pub(crate) fn from_db(value: &str) -> Self {
        match value {
            "asciidoc" => Self::AsciiDoc,
            _ => Self::Markdown,
        }
    }
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BookSummary {
    pub id: String,
    pub title: String,
    pub theme_prompt: String,
    pub genre_key: String,
    pub genre_name: String,
    pub target_audience: String,
    pub tone: String,
    pub target_char_count: i64,
    pub overview: String,
    pub status: BookStatus,
    pub total_chapters: u32,
    pub total_sections: u32,
    pub completed_sections: u32,
    pub empty_or_failed_sections: u32,
    pub word_count: u64,
    pub char_count: u64,
    pub last_read_chapter_id: Option<String>,
    pub last_read_section_id: Option<String>,
    pub last_read_scroll_offset: f64,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ChapterRecord {
    pub id: String,
    pub book_id: String,
    pub chapter_number: u32,
    pub position: u32,
    pub title: String,
    pub focus_area: String,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SectionRecord {
    pub id: String,
    pub chapter_id: String,
    pub book_id: String,
    pub chapter_number: u32,
    pub section_number: u32,
    pub position: u32,
    pub title: String,
    pub key_focus: String,
    pub format: MarkupFormat,
    pub content: String,
    pub status: SectionStatus,
    pub error_message: Option<String>,
    pub word_count: u64,
    pub char_count: u64,
    pub revision_count: u32,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ChapterWithSections {
    pub chapter: ChapterRecord,
    pub sections: Vec<SectionRecord>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BookDetail {
    pub summary: BookSummary,
    pub chapters: Vec<ChapterWithSections>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SectionRevision {
    pub id: i64,
    pub section_id: String,
    pub revision_number: u32,
    pub content: String,
    pub source: String,
    pub note: String,
    pub created_at: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ReadingProgress {
    pub book_id: String,
    pub chapter_id: String,
    pub section_id: Option<String>,
    pub scroll_offset: f64,
    pub updated_at: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchHit {
    pub book_id: String,
    pub book_title: String,
    pub chapter_id: String,
    pub chapter_number: u32,
    pub chapter_title: String,
    pub section_id: String,
    pub section_number: u32,
    pub section_title: String,
    pub snippet: String,
    pub rank: f64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct IncompleteSection {
    pub book_id: String,
    pub chapter_id: String,
    pub chapter_number: u32,
    pub chapter_title: String,
    pub section_id: String,
    pub section_number: u32,
    pub section_title: String,
    pub key_focus: String,
    pub status: SectionStatus,
    pub error_message: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewBookDraft {
    pub id: Option<String>,
    pub title: String,
    pub theme_prompt: String,
    pub genre_key: String,
    pub genre_name: String,
    pub target_audience: String,
    pub tone: String,
    pub target_char_count: i64,
    pub overview: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct BookMetadataUpdate {
    pub book_id: String,
    pub title: String,
    pub overview: String,
    pub genre_key: String,
    pub genre_name: String,
    pub target_audience: String,
    pub tone: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewChapterPlan {
    pub chapter_number: u32,
    pub title: String,
    pub focus_area: String,
    pub sections: Vec<NewSectionPlan>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewSectionPlan {
    pub section_number: u32,
    pub title: String,
    pub key_focus: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LegacyImportReport {
    pub db_books_imported: u32,
    pub fs_books_imported: u32,
    pub books_skipped_existing: u32,
    pub chapters_imported: u32,
    pub sections_imported: u32,
    pub empty_sections_flagged: u32,
    pub normalized_section_numbers: u32,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct CoreHealthStatus {
    pub crate_version: String,
    pub engine_name: String,
    pub uniffi_ready: bool,
    pub echo_note: String,
}

/// Counts CJK-aware word units and non-newline characters for a section body.
pub(crate) fn count_words_and_chars(content: &str) -> (u64, u64) {
    if content.trim().is_empty() {
        return (0, 0);
    }

    let mut chars: u64 = 0;
    let mut words: u64 = 0;
    let mut in_ascii_word = false;

    for ch in content.chars() {
        if ch != '\r' && ch != '\n' {
            chars = chars.saturating_add(1);
        }
        if is_cjk_or_kana(ch) {
            if in_ascii_word {
                words = words.saturating_add(1);
                in_ascii_word = false;
            }
            words = words.saturating_add(1);
        } else if ch.is_alphanumeric() {
            in_ascii_word = true;
        } else if in_ascii_word {
            words = words.saturating_add(1);
            in_ascii_word = false;
        }
    }
    if in_ascii_word {
        words = words.saturating_add(1);
    }

    (words, chars)
}

fn is_cjk_or_kana(ch: char) -> bool {
    let code = ch as u32;
    matches!(
        code,
        0x3040..=0x309F // Hiragana
            | 0x30A0..=0x30FF // Katakana
            | 0x3400..=0x4DBF // CJK Ext A
            | 0x4E00..=0x9FFF // CJK Unified Ideographs
            | 0xF900..=0xFAFF // CJK Compatibility Ideographs
            | 0xAC00..=0xD7AF // Hangul
    )
}

/// Computes derived parent book status from child section states.
pub(crate) fn derive_book_status(
    has_chapters: bool,
    total_sections: u32,
    completed: u32,
    generating: u32,
    failed_or_empty: u32,
) -> BookStatus {
    if !has_chapters || total_sections == 0 {
        BookStatus::Draft
    } else if generating > 0 {
        BookStatus::Generating
    } else if completed == total_sections {
        BookStatus::Completed
    } else if completed > 0 {
        BookStatus::Partial
    } else if failed_or_empty == total_sections {
        BookStatus::Failed
    } else {
        BookStatus::Outlined
    }
}
