package backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standardized error response body. Always returned as JSON with status &lt; 500.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {

    public record FieldViolation(String field, String message) {}

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiError of(int status, String error, String code, String message,
                              String path, List<FieldViolation> fieldErrors) {
        return new ApiError(Instant.now(), status, error, code, message, path, fieldErrors);
    }
}
