use std::any::Any;
use std::mem::ManuallyDrop;
use std::panic::{self, AssertUnwindSafe};

/// Structured error type exposed across the UniFFI boundary.
/// Fields avoid `message` to prevent collision with Kotlin `Throwable.message`.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum BookGenError {
    #[error("Invalid input: {detail}")]
    InvalidInput { detail: String },
    #[error("Not found: {detail}")]
    NotFound { detail: String },
    #[error("Conflict: {detail}")]
    Conflict { detail: String },
    #[error("Storage error: {detail}")]
    Storage { detail: String },
    #[error("I/O error: {detail}")]
    IoFailed { detail: String },
    #[error("Parse error: {detail}")]
    Parse { detail: String },
    #[error("Export error: {detail}")]
    Export { detail: String },
    #[error("Operation cancelled")]
    Cancelled,
    #[error("Internal engine panic: {detail}")]
    InternalPanic { detail: String },
}

impl BookGenError {
    pub fn invalid_input(detail: impl Into<String>) -> Self {
        Self::InvalidInput {
            detail: detail.into(),
        }
    }

    pub fn not_found(detail: impl Into<String>) -> Self {
        Self::NotFound {
            detail: detail.into(),
        }
    }

    pub fn conflict(detail: impl Into<String>) -> Self {
        Self::Conflict {
            detail: detail.into(),
        }
    }

    pub fn storage(detail: impl Into<String>) -> Self {
        Self::Storage {
            detail: detail.into(),
        }
    }

    pub fn io_failed(detail: impl Into<String>) -> Self {
        Self::IoFailed {
            detail: detail.into(),
        }
    }

    pub fn parse(detail: impl Into<String>) -> Self {
        Self::Parse {
            detail: detail.into(),
        }
    }
}

impl From<rusqlite::Error> for BookGenError {
    fn from(err: rusqlite::Error) -> Self {
        Self::Storage {
            detail: format!("sqlite operation failed: {err}"),
        }
    }
}

impl From<serde_json::Error> for BookGenError {
    fn from(err: serde_json::Error) -> Self {
        Self::Parse {
            detail: format!("json parse failed: {err}"),
        }
    }
}

impl From<std::io::Error> for BookGenError {
    fn from(err: std::io::Error) -> Self {
        Self::IoFailed {
            detail: format!("filesystem operation failed ({:?})", err.kind()),
        }
    }
}

pub(crate) fn discard_panic_payload(payload: Box<dyn Any + Send>) {
    let second = panic::catch_unwind(AssertUnwindSafe(|| drop(payload)));
    if let Err(second_payload) = second {
        // Guard against a panicking destructor on the first panic payload.
        let _leaked = ManuallyDrop::new(second_payload);
    }
}

/// Executes an FFI-bound operation inside `catch_unwind`, discarding any payload safely.
pub(crate) fn guard_ffi<T, F>(op_name: &'static str, f: F) -> Result<T, BookGenError>
where
    F: FnOnce() -> Result<T, BookGenError>,
{
    // Invariant: callers recover or fail closed on mutex poison rather than relying on unwind rollback.
    match panic::catch_unwind(AssertUnwindSafe(f)) {
        Ok(result) => result,
        Err(payload) => {
            discard_panic_payload(payload);
            Err(BookGenError::InternalPanic {
                detail: format!("internal panic during {op_name}"),
            })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    struct PanicOnDrop;

    impl Drop for PanicOnDrop {
        #[expect(
            clippy::panic,
            reason = "simulates panicking payload destructor during unwind disposal"
        )]
        fn drop(&mut self) {
            panic!("destructor boom");
        }
    }

    #[test]
    #[expect(clippy::panic, reason = "verifies guard_ffi catches unwinds cleanly")]
    fn guard_ffi_catches_panic_without_leaking_payload_and_survives_drop_panic() {
        let prev_hook = panic::take_hook();
        panic::set_hook(Box::new(|_| {}));

        let secret_res: Result<(), BookGenError> = guard_ffi("test_secret_panic", || {
            panic!("TOP_SECRET_API_KEY_12345");
        });
        assert_eq!(
            secret_res,
            Err(BookGenError::InternalPanic {
                detail: "internal panic during test_secret_panic".to_string()
            })
        );

        let drop_res: Result<(), BookGenError> = guard_ffi("test_drop_panic", || {
            panic::panic_any(PanicOnDrop);
        });
        assert!(matches!(drop_res, Err(BookGenError::InternalPanic { .. })));

        panic::set_hook(prev_hook);
    }
}
