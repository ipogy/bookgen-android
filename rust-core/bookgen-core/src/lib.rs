#![forbid(unsafe_code)]
#![deny(
    clippy::unwrap_used,
    clippy::expect_used,
    clippy::panic,
    clippy::unreachable,
    clippy::indexing_slicing,
    clippy::todo,
    clippy::unimplemented
)]
#![cfg_attr(not(test), deny(clippy::panic_in_result_fn))]

pub mod error;
pub mod models;
pub mod store;

pub use error::BookGenError;
pub use models::{
    BookDetail, BookMetadataUpdate, BookStatus, BookSummary, ChapterRecord, ChapterWithSections,
    CoreHealthStatus, IncompleteSection, LegacyImportReport, MarkupFormat, NewBookDraft,
    NewChapterPlan, NewSectionPlan, ReadingProgress, SearchHit, SectionRecord, SectionRevision,
    SectionStatus,
};
pub use store::BookStore;

uniffi::setup_scaffolding!();

/// Minimal smoke-check entry point exported to Kotlin over Mozilla UniFFI.
#[uniffi::export]
pub fn core_health_check(echo_input: String) -> Result<CoreHealthStatus, BookGenError> {
    error::guard_ffi("core_health_check", || {
        let trimmed = echo_input.trim();
        if trimmed.is_empty() {
            return Err(BookGenError::InvalidInput {
                detail: "echo_input must not be blank".to_string(),
            });
        }
        Ok(CoreHealthStatus {
            crate_version: env!("CARGO_PKG_VERSION").to_string(),
            engine_name: env!("CARGO_PKG_NAME").to_string(),
            uniffi_ready: true,
            echo_note: format!("BookGen Rust Core ready: {trimmed}"),
        })
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn health_check_returns_expected_status() {
        let status = core_health_check("smoke".to_string());
        assert_eq!(
            status,
            Ok(CoreHealthStatus {
                crate_version: "0.1.0".to_string(),
                engine_name: "bookgen-core".to_string(),
                uniffi_ready: true,
                echo_note: "BookGen Rust Core ready: smoke".to_string(),
            })
        );
    }

    #[test]
    fn health_check_rejects_blank_input() {
        let err = core_health_check("   ".to_string());
        assert!(matches!(err, Err(BookGenError::InvalidInput { .. })));
    }
}
