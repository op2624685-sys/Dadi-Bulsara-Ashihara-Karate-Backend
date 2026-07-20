package backend.camp.exception;

/**
 * Thrown when a camp cannot be resolved — either the id/slug does not exist,
 * or the caller asked for a public detail view of a camp that is still a
 * hidden DRAFT (published = false). The {@link backend.common.GlobalExceptionHandler}
 * maps this to HTTP 404 (CAMP_NOT_FOUND).
 */
public class CampNotFoundException extends RuntimeException {
    public CampNotFoundException(String message) {
        super(message);
    }
}
