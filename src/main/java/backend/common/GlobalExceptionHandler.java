package backend.common;

import backend.common.exception.EmailAlreadyExistsException;
import backend.common.exception.EmailNotVerifiedException;
import backend.common.exception.InvalidTokenException;
import backend.common.exception.UserNotFoundException;
import backend.common.exception.RateLimitExceededException;
import backend.common.exception.TokenExpiredException;
import backend.camp.exception.CampNotFoundException;
import backend.event.exception.EventNotFoundException;
import backend.storage.StorageException;
import backend.teacher.exception.TeacherNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 400 — bean-validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        List<ApiError.FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "VALIDATION_FAILED",
                        "Request validation failed", req.getRequestURI(), fields));
    }

    // 401 — bad credentials (don't reveal which half was wrong)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCreds(BadCredentialsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "AUTH_INVALID_CREDENTIALS",
                        "Invalid email or password", req.getRequestURI()));
    }

    // 401 — any other auth failure
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "AUTH_FAILED",
                        "Authentication failed", req.getRequestURI()));
    }

    // 401 — account exists and password is correct, but the account has been
    // blocked by an admin (see UserEntity.isEnabled(), which folds in `blocked`).
    // More specific than the generic AuthenticationException handler so the
    // frontend can show a clear "your account is blocked" message instead of a
    // misleading "invalid credentials" one. Placed before handleAuth so Spring
    // resolves the most specific handler (DisabledException extends
    // AuthenticationException).
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "AUTH_ACCOUNT_BLOCKED",
                        "Your account has been blocked. Please contact an administrator.", req.getRequestURI()));
    }

    // 401 — account exists, password matches, but email not verified yet.
    // More specific than the generic AuthenticationException handler so the
    // frontend can show a "please verify your email" prompt with a resend
    // link, instead of a generic "invalid credentials" message.
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailNotVerified(EmailNotVerifiedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "AUTH_EMAIL_NOT_VERIFIED",
                        "Please verify your email before signing in", req.getRequestURI()));
    }

    // 401 — invalid / malformed token
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex, HttpServletRequest req) {
        String code = (ex instanceof TokenExpiredException) ? "AUTH_TOKEN_EXPIRED" : "AUTH_INVALID_TOKEN";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", code, ex.getMessage(), req.getRequestURI()));
    }

    // 403 — authenticated but lacking permission
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden", "AUTH_ACCESS_DENIED",
                        "Access denied", req.getRequestURI()));
    }

    // 409 — duplicate email / unique-constraint violation
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailTaken(EmailAlreadyExistsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", "EMAIL_ALREADY_EXISTS",
                        ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", "RESOURCE_CONFLICT",
                        "Resource already exists or violates a constraint", req.getRequestURI()));
    }

    // 429 — rate limit
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiError.of(429, "Too Many Requests", "RATE_LIMIT_EXCEEDED",
                        ex.getMessage(), req.getRequestURI()));
    }

    // 404 — teacher not found (or not publicly visible)
    @ExceptionHandler(TeacherNotFoundException.class)
    public ResponseEntity<ApiError> handleTeacherNotFound(TeacherNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", "TEACHER_NOT_FOUND",
                        ex.getMessage(), req.getRequestURI()));
    }

    // 404 — student not found (or not publicly visible)
    @ExceptionHandler(backend.student.exception.StudentNotFoundException.class)
    public ResponseEntity<ApiError> handleStudentNotFound(backend.student.exception.StudentNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", "STUDENT_NOT_FOUND",
                        ex.getMessage(), req.getRequestURI()));
    }

    // 404 — camp not found, or a public request for a hidden DRAFT camp
    @ExceptionHandler(CampNotFoundException.class)
    public ResponseEntity<ApiError> handleCampNotFound(CampNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", "CAMP_NOT_FOUND",
                        ex.getMessage(), req.getRequestURI()));
    }

    // 404 — event not found, or a public request for a hidden DRAFT event
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> handleEventNotFound(EventNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", "EVENT_NOT_FOUND",
                        ex.getMessage(), req.getRequestURI()));
    }

    // 502 — image storage / upload failure (Cloudflare R2 or local fallback)
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiError> handleStorage(StorageException ex, HttpServletRequest req) {
        log.error("Storage/upload failed at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(502, "Bad Gateway", "STORAGE_UPLOAD_FAILED",
                        "Image upload failed: " + ex.getMessage(), req.getRequestURI()));
    }

    // 413 — uploaded file exceeds the configured max size (spring.servlet.multipart)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleFileTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(413, "Payload Too Large", "FILE_TOO_LARGE",
                        "Uploaded file is too large", req.getRequestURI()));
    }

    // 400 — bad admin argument (e.g. SUB_ADMIN without a state, self role-change)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "INVALID_ARGUMENT",
                        ex.getMessage(), req.getRequestURI()));
    }

    // 404 — user not found (admin user-management)
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", "USER_NOT_FOUND",
                        ex.getMessage(), req.getRequestURI()));
    }

    // 500 — last-resort catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error", "INTERNAL_ERROR",
                        "An unexpected error occurred", req.getRequestURI()));
    }
}
