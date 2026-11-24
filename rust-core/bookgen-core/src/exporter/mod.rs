pub mod epub;
pub mod pdf;
pub mod text;

use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::BufWriter;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::time::{Duration, Instant};

use crate::error::{guard_ffi, BookGenError};
use crate::store::BookStore;

const MAX_ACTIVE_JOBS: usize = 64;
const MAX_JOB_ID_BYTES: usize = 128;
const RESERVATION_TTL: Duration = Duration::from_secs(30);

static NEXT_PART_SERIAL: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ExportFormat {
    Epub,
    Pdf,
    Markdown,
    AsciiDoc,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ProgressStage {
    Validating,
    Loading,
    Processing,
    Building,
    Encoding,
    Writing,
    Done,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ProgressEvent {
    pub job_id: String,
    pub stage: ProgressStage,
    pub fraction: f64,
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum ProgressCallbackError {
    #[error("progress callback failed")]
    Failed,
}

impl From<uniffi::UnexpectedUniFFICallbackError> for ProgressCallbackError {
    fn from(_: uniffi::UnexpectedUniFFICallbackError) -> Self {
        Self::Failed
    }
}

/// Foreign trait implemented by Kotlin (`callbackFlow` + `trySend`) to observe export progress.
#[uniffi::export(with_foreign)]
pub trait ExportProgressListener: Send + Sync {
    fn on_progress(&self, event: ProgressEvent) -> Result<(), ProgressCallbackError>;
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ExportRequest {
    pub job_id: String,
    pub book_id: String,
    pub format: ExportFormat,
    pub output_path: String,
    pub include_toc: bool,
    pub include_incomplete_notice: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ExportResult {
    pub job_id: String,
    pub format: ExportFormat,
    pub output_path: String,
    pub bytes_written: u64,
    pub chapter_count: u32,
    pub section_count: u32,
}

struct JobEntry {
    flag: Arc<AtomicBool>,
    reserved_at: Instant,
    running: bool,
}

#[derive(Default)]
pub(crate) struct JobRegistry {
    entries: Mutex<HashMap<String, JobEntry>>,
}

impl JobRegistry {
    pub fn reserve(&self, job_id: &str) -> Result<(), BookGenError> {
        validate_job_id(job_id)?;
        let mut entries = self
            .entries
            .lock()
            .map_err(|_| BookGenError::storage("export job registry poisoned"))?;
        Self::reap_expired(&mut entries);
        if entries.contains_key(job_id) {
            return Err(BookGenError::conflict("duplicate export job_id"));
        }
        if entries.len() >= MAX_ACTIVE_JOBS {
            return Err(BookGenError::conflict("too many active export jobs"));
        }
        entries.insert(
            job_id.to_owned(),
            JobEntry {
                flag: Arc::new(AtomicBool::new(false)),
                reserved_at: Instant::now(),
                running: false,
            },
        );
        Ok(())
    }

    pub fn register_or_start(&self, job_id: &str) -> Result<Arc<AtomicBool>, BookGenError> {
        validate_job_id(job_id)?;
        let mut entries = self
            .entries
            .lock()
            .map_err(|_| BookGenError::storage("export job registry poisoned"))?;
        Self::reap_expired(&mut entries);
        if let Some(entry) = entries.get_mut(job_id) {
            if entry.running {
                return Err(BookGenError::conflict("export job already running"));
            }
            entry.running = true;
            return Ok(Arc::clone(&entry.flag));
        }
        if entries.len() >= MAX_ACTIVE_JOBS {
            return Err(BookGenError::conflict("too many active export jobs"));
        }
        let flag = Arc::new(AtomicBool::new(false));
        entries.insert(
            job_id.to_owned(),
            JobEntry {
                flag: Arc::clone(&flag),
                reserved_at: Instant::now(),
                running: true,
            },
        );
        Ok(flag)
    }

    /// Non-throwing, panic-free cancellation signal.
    pub fn cancel(&self, job_id: &str) {
        if job_id.is_empty() || job_id.len() > MAX_JOB_ID_BYTES {
            return;
        }
        let mut entries = self.lock_recovered();
        Self::reap_expired(&mut entries);
        if let Some(entry) = entries.get(job_id) {
            // Release pairs with worker Acquire load.
            entry.flag.store(true, Ordering::Release);
        }
    }

    pub fn release(&self, job_id: &str) {
        self.lock_recovered().remove(job_id);
    }

    fn lock_recovered(&self) -> MutexGuard<'_, HashMap<String, JobEntry>> {
        // Recover poison rather than panicking inside `cancel_job` or `Drop`.
        self.entries.lock().unwrap_or_else(PoisonError::into_inner)
    }

    fn reap_expired(entries: &mut HashMap<String, JobEntry>) {
        entries.retain(|_, entry| entry.running || entry.reserved_at.elapsed() < RESERVATION_TTL);
    }
}

struct WorkerReleaseGuard<'a> {
    registry: &'a JobRegistry,
    job_id: String,
}

impl Drop for WorkerReleaseGuard<'_> {
    fn drop(&mut self) {
        self.registry.release(&self.job_id);
    }
}

struct PartialOutput {
    path: PathBuf,
    file: Option<File>,
    committed: bool,
}

impl PartialOutput {
    fn create(target: &Path) -> Result<Self, BookGenError> {
        let parent = target
            .parent()
            .filter(|p| !p.as_os_str().is_empty())
            .unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;

        for _ in 0..128 {
            let serial = NEXT_PART_SERIAL.fetch_add(1, Ordering::Relaxed);
            let candidate = parent.join(format!(
                ".bookgen-export-{}-{serial}.part",
                std::process::id()
            ));
            if candidate.file_name() == target.file_name() {
                continue;
            }
            match OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(&candidate)
            {
                Ok(file) => {
                    return Ok(Self {
                        path: candidate,
                        file: Some(file),
                        committed: false,
                    });
                }
                Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => continue,
                Err(e) => return Err(BookGenError::from(e)),
            }
        }
        Err(BookGenError::Export {
            detail: "could not allocate temporary export file".to_string(),
        })
    }

    fn commit(mut self, target: &Path) -> Result<u64, BookGenError> {
        drop(self.file.take());
        let len = fs::metadata(&self.path)?.len();
        fs::rename(&self.path, target)?;
        self.committed = true;
        Ok(len)
    }
}

impl Drop for PartialOutput {
    fn drop(&mut self) {
        drop(self.file.take());
        if !self.committed {
            let _ = fs::remove_file(&self.path);
        }
    }
}

/// Multi-format offline exporter (`EPUB 3`, `PDF 1.4`, `Markdown`, `AsciiDoc`) with
/// bounded job reservation, cooperative cancellation, and atomic `.part` file writes.
#[derive(Default, uniffi::Object)]
pub struct BookExporter {
    registry: JobRegistry,
}

#[uniffi::export]
impl BookExporter {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }

    /// Reserves a caller-supplied `job_id` synchronously prior to starting worker execution.
    pub fn reserve_job(&self, job_id: String) -> Result<(), BookGenError> {
        guard_ffi("BookExporter::reserve_job", || {
            self.registry.reserve(job_id.trim())
        })
    }

    /// Cooperatively cancels an active or pre-reserved job. Infallible, idempotent, non-blocking.
    pub fn cancel_job(&self, job_id: String) {
        self.registry.cancel(job_id.trim());
    }

    /// Exports a persisted book from `store` into `request.output_path`, reporting progress
    /// and atomically committing on completion (removing any temporary file on cancel/error).
    pub fn export_book(
        &self,
        store: Arc<BookStore>,
        request: ExportRequest,
        listener: Option<Arc<dyn ExportProgressListener>>,
    ) -> Result<ExportResult, BookGenError> {
        guard_ffi("BookExporter::export_book", || {
            let job_id = request.job_id.trim().to_string();
            let flag = self.registry.register_or_start(&job_id)?;
            let _guard = WorkerReleaseGuard {
                registry: &self.registry,
                job_id: job_id.clone(),
            };

            check_cancelled(&flag)?;
            emit_progress(&listener, &job_id, ProgressStage::Validating, 0.0)?;

            let book_id = request.book_id.trim().to_string();
            let output_trimmed = request.output_path.trim().to_string();
            if book_id.is_empty() || output_trimmed.is_empty() {
                return Err(BookGenError::invalid_input(
                    "book_id and output_path must not be blank",
                ));
            }
            let target_path = PathBuf::from(&output_trimmed);
            if target_path.is_dir() {
                return Err(BookGenError::invalid_input(
                    "output_path points to an existing directory",
                ));
            }

            check_cancelled(&flag)?;
            emit_progress(&listener, &job_id, ProgressStage::Loading, 0.15)?;
            let detail = store.get_book(book_id)?;
            let chapter_count = u32::try_from(detail.chapters.len()).unwrap_or(u32::MAX);
            let section_count = detail
                .chapters
                .iter()
                .map(|c| u32::try_from(c.sections.len()).unwrap_or(0))
                .fold(0u32, u32::saturating_add);

            check_cancelled(&flag)?;
            emit_progress(&listener, &job_id, ProgressStage::Building, 0.30)?;

            let mut partial = PartialOutput::create(&target_path)?;
            {
                let file = partial.file.as_mut().ok_or_else(|| BookGenError::Export {
                    detail: "temporary export handle missing".to_string(),
                })?;

                let mut stage_report = |sub_fraction: f64| -> Result<(), BookGenError> {
                    let overall = 0.30 + (sub_fraction.clamp(0.0, 1.0) * 0.60);
                    let stage = if sub_fraction < 0.75 {
                        ProgressStage::Processing
                    } else {
                        ProgressStage::Encoding
                    };
                    emit_progress(&listener, &job_id, stage, overall)
                };

                match request.format {
                    ExportFormat::Markdown => {
                        let mut writer = BufWriter::new(file);
                        text::write_markdown_book(
                            &detail,
                            request.include_toc,
                            request.include_incomplete_notice,
                            &flag,
                            &mut writer,
                            &mut stage_report,
                        )?;
                    }
                    ExportFormat::AsciiDoc => {
                        let mut writer = BufWriter::new(file);
                        text::write_asciidoc_book(
                            &detail,
                            request.include_toc,
                            request.include_incomplete_notice,
                            &flag,
                            &mut writer,
                            &mut stage_report,
                        )?;
                    }
                    ExportFormat::Epub => {
                        let writer = BufWriter::new(file);
                        epub::write_epub_book(
                            &detail,
                            request.include_toc,
                            request.include_incomplete_notice,
                            &flag,
                            writer,
                            &mut stage_report,
                        )?;
                    }
                    ExportFormat::Pdf => {
                        let mut writer = BufWriter::new(file);
                        pdf::write_pdf_book(
                            &detail,
                            request.include_toc,
                            request.include_incomplete_notice,
                            &flag,
                            &mut writer,
                            &mut stage_report,
                        )?;
                    }
                }
            }

            check_cancelled(&flag)?;
            emit_progress(&listener, &job_id, ProgressStage::Writing, 0.95)?;
            let bytes_written = partial.commit(&target_path)?;
            emit_progress(&listener, &job_id, ProgressStage::Done, 1.0)?;

            Ok(ExportResult {
                job_id,
                format: request.format,
                output_path: output_trimmed,
                bytes_written,
                chapter_count,
                section_count,
            })
        })
    }
}

pub(crate) fn check_cancelled(flag: &AtomicBool) -> Result<(), BookGenError> {
    if flag.load(Ordering::Acquire) {
        Err(BookGenError::Cancelled)
    } else {
        Ok(())
    }
}

fn validate_job_id(job_id: &str) -> Result<(), BookGenError> {
    if job_id.is_empty() || job_id.len() > MAX_JOB_ID_BYTES {
        Err(BookGenError::invalid_input("invalid export job_id length"))
    } else {
        Ok(())
    }
}

fn emit_progress(
    listener: &Option<Arc<dyn ExportProgressListener>>,
    job_id: &str,
    stage: ProgressStage,
    fraction: f64,
) -> Result<(), BookGenError> {
    let Some(cb) = listener else {
        return Ok(());
    };
    let safe_fraction = if fraction.is_finite() {
        fraction.clamp(0.0, 1.0)
    } else {
        0.0
    };
    cb.on_progress(ProgressEvent {
        job_id: job_id.to_owned(),
        stage,
        fraction: safe_fraction,
    })
    .map_err(|_| BookGenError::Export {
        detail: "progress listener rejected update".to_string(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::{MarkupFormat, NewBookDraft, NewChapterPlan, NewSectionPlan};
    use std::io::Read;
    use tempfile::TempDir;
    use zip::ZipArchive;

    struct RecordingListener {
        events: Mutex<Vec<ProgressEvent>>,
    }

    impl ExportProgressListener for RecordingListener {
        fn on_progress(&self, event: ProgressEvent) -> Result<(), ProgressCallbackError> {
            self.events
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
                .push(event);
            Ok(())
        }
    }

    fn seed_test_book() -> Result<(Arc<BookStore>, String), Box<dyn std::error::Error>> {
        let store = BookStore::open(":memory:".to_string())?;
        let summary = store.create_book(NewBookDraft {
            id: None,
            title: "RustとKotlinで作るAI電子書籍".to_string(),
            theme_prompt: "Offline Rust publishing".to_string(),
            genre_key: "tech".to_string(),
            genre_name: "IT技術".to_string(),
            target_audience: "Engineers".to_string(),
            tone: "Concise".to_string(),
            target_char_count: 10000,
            overview: "A complete walkthrough of EPUB and PDF export.".to_string(),
        })?;
        let detail = store.save_book_outline(
            summary.id.clone(),
            vec![NewChapterPlan {
                chapter_number: 1,
                title: "設計と基礎".to_string(),
                focus_area: "UniFFI boundary".to_string(),
                sections: vec![NewSectionPlan {
                    section_number: 1,
                    title: "Rust Core".to_string(),
                    key_focus: "Export".to_string(),
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
            "日本語の本文とサンプルコード。\n\n```rust\nprintln!(\"hello\");\n```\n\n| Col1 | Col2 |\n| --- | --- |\n| A | B |".to_string(),
            MarkupFormat::Markdown,
            "ai_generate".to_string(),
            "test".to_string(),
        )?;
        Ok((store, summary.id))
    }

    #[test]
    fn exports_valid_epub_pdf_md_and_adoc_with_progress() -> Result<(), Box<dyn std::error::Error>>
    {
        let temp = TempDir::new()?;
        let (store, book_id) = seed_test_book()?;
        let exporter = BookExporter::new();

        // EPUB 3 check
        let epub_path = temp.path().join("book.epub");
        let listener = Arc::new(RecordingListener {
            events: Mutex::new(Vec::new()),
        });
        exporter.reserve_job("job-epub".to_string())?;
        let epub_res = exporter.export_book(
            Arc::clone(&store),
            ExportRequest {
                job_id: "job-epub".to_string(),
                book_id: book_id.clone(),
                format: ExportFormat::Epub,
                output_path: epub_path.to_string_lossy().to_string(),
                include_toc: true,
                include_incomplete_notice: true,
            },
            Some(listener.clone()),
        )?;
        assert!(epub_res.bytes_written > 200);
        let events = listener
            .events
            .lock()
            .unwrap_or_else(PoisonError::into_inner);
        assert_eq!(events.last().ok_or("no events")?.stage, ProgressStage::Done);

        let epub_file = File::open(&epub_path)?;
        let mut archive = ZipArchive::new(epub_file)?;
        let mut mimetype = String::new();
        archive.by_index(0)?.read_to_string(&mut mimetype)?;
        assert_eq!(mimetype, "application/epub+zip");
        assert!(archive.by_name("OEBPS/content.opf").is_ok());
        assert!(archive.by_name("OEBPS/nav.xhtml").is_ok());
        assert!(archive.by_name("OEBPS/chapter_001.xhtml").is_ok());

        // PDF check (Japanese + ASCII header + EOF)
        let pdf_path = temp.path().join("book.pdf");
        let pdf_res = exporter.export_book(
            Arc::clone(&store),
            ExportRequest {
                job_id: "job-pdf".to_string(),
                book_id: book_id.clone(),
                format: ExportFormat::Pdf,
                output_path: pdf_path.to_string_lossy().to_string(),
                include_toc: true,
                include_incomplete_notice: true,
            },
            None,
        )?;
        assert!(pdf_res.bytes_written > 500);
        let pdf_bytes = fs::read(&pdf_path)?;
        assert!(pdf_bytes.starts_with(b"%PDF-1.4"));
        let pdf_text = String::from_utf8_lossy(&pdf_bytes);
        assert!(pdf_text.contains("%%EOF"));
        assert!(pdf_text.contains("UniJIS-UTF16-H"));

        // Markdown & AsciiDoc check
        let md_path = temp.path().join("book.md");
        exporter.export_book(
            Arc::clone(&store),
            ExportRequest {
                job_id: "job-md".to_string(),
                book_id: book_id.clone(),
                format: ExportFormat::Markdown,
                output_path: md_path.to_string_lossy().to_string(),
                include_toc: true,
                include_incomplete_notice: true,
            },
            None,
        )?;
        assert!(fs::read_to_string(&md_path)?.contains("```rust"));

        let adoc_path = temp.path().join("book.adoc");
        exporter.export_book(
            store,
            ExportRequest {
                job_id: "job-adoc".to_string(),
                book_id,
                format: ExportFormat::AsciiDoc,
                output_path: adoc_path.to_string_lossy().to_string(),
                include_toc: true,
                include_incomplete_notice: true,
            },
            None,
        )?;
        assert!(fs::read_to_string(&adoc_path)?.contains("[source,rust]"));

        Ok(())
    }

    #[test]
    fn pre_cancel_aborts_cleanly_without_overwriting_existing_file(
    ) -> Result<(), Box<dyn std::error::Error>> {
        let temp = TempDir::new()?;
        let (store, book_id) = seed_test_book()?;
        let exporter = BookExporter::new();

        let existing = temp.path().join("keep.pdf");
        fs::write(&existing, b"previous")?;

        exporter.reserve_job("job-pre-cancel".to_string())?;
        exporter.cancel_job("never-reserved".to_string()); // must be safe no-op
        exporter.cancel_job("job-pre-cancel".to_string());

        let err = exporter.export_book(
            store,
            ExportRequest {
                job_id: "job-pre-cancel".to_string(),
                book_id,
                format: ExportFormat::Pdf,
                output_path: existing.to_string_lossy().to_string(),
                include_toc: true,
                include_incomplete_notice: true,
            },
            None,
        );
        assert!(matches!(err, Err(BookGenError::Cancelled)));
        assert_eq!(fs::read(&existing)?, b"previous");
        Ok(())
    }
}
