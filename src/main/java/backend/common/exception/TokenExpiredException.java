package backend.common.exception;

/**
 * Thrown when a token's {@code exp} (or {@code nbf}) claim is in the past.
 * Subclass of {@link InvalidTokenException} so handlers can fall through
 * with a single catch if they want to treat them alike.
 */
public class TokenExpiredException extends InvalidTokenException {
    public TokenExpiredException(String message) {
        super(message);
    }
}
