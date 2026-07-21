package backend.event.exception;

/**
 * Thrown when an event cannot be resolved — either the id/slug does not exist,
 * or the caller asked for a public view of a hidden DRAFT (published = false).
 * The {@link backend.common.GlobalExceptionHandler} maps this to HTTP 404
 * (EVENT_NOT_FOUND).
 */
public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String message) {
        super(message);
    }
}
