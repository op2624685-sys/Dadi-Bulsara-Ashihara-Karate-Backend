package backend.common.exception;

/**
 * Thrown when an authentication token is malformed, has a bad signature,
 * or fails any structural check.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
