package backend.storage;

/**
 * Raised when an image upload fails — whether the Cloudflare R2 putObject
 * errored, the local fallback write failed, or pre-upload validation
 * (empty file / bad content-type) was violated. The
 * {@link backend.common.GlobalExceptionHandler} maps this to HTTP 502
 * (STORAGE_UPLOAD_FAILED).
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
