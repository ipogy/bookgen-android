#![forbid(unsafe_code)]
#![deny(
    clippy::unwrap_used,
    clippy::panic,
    clippy::unreachable,
    clippy::indexing_slicing
)]

uniffi::setup_scaffolding!();

/// Structured error type exposed across the UniFFI boundary.
/// Note: avoid naming error fields `message` because Kotlin's `Exception` already defines `message`.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum BookGenError {
    #[error("Invalid input: {detail}")]
    InvalidInput { detail: String },
    #[error("Storage error: {detail}")]
    Storage { detail: String },
    #[error("Parse error: {detail}")]
    Parse { detail: String },
    #[error("Export error: {detail}")]
    Export { detail: String },
    #[error("Operation cancelled")]
    Cancelled,
    #[error("Internal engine panic: {detail}")]
    InternalPanic { detail: String },
}

/// Smoke-test status record verifying UniFFI struct & string bridging.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct CoreHealthStatus {
    pub crate_version: String,
    pub engine_name: String,
    pub uniffi_ready: bool,
    pub echo_note: String,
}

/// Returns core engine metadata and echoes a sanitized label to verify FFI round-trip.
#[uniffi::export]
pub fn core_health_check(label: String) -> Result<CoreHealthStatus, BookGenError> {
    let trimmed = label.trim();
    if trimmed.is_empty() {
        return Err(BookGenError::InvalidInput {
            detail: "health check label must not be empty".to_string(),
        });
    }

    Ok(CoreHealthStatus {
        crate_version: env!("CARGO_PKG_VERSION").to_string(),
        engine_name: "bookgen-core".to_string(),
        uniffi_ready: true,
        echo_note: format!("BookGen Rust Core ready: {trimmed}"),
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
