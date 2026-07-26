package backend.user;

import backend.auth.AuthService;
import backend.security.SecurityService;
import backend.student.StudentService;
import backend.student.dto.StudentResponse;
import backend.student.exception.StudentNotFoundException;
import backend.teacher.TeacherService;
import backend.teacher.dto.TeacherResponse;
import backend.teacher.exception.TeacherNotFoundException;
import backend.user.dto.UserMeResponse;
import backend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * Unified current-user identity endpoint.
 *
 * <p>{@code GET /api/v1/user/me} is the single bootstrap call the SPA makes on
 * first load. It returns the full identity — the account (with belt and unlocked
 * cosmetics resolved by {@link AuthService#getCurrentUser(String)}), plus the
 * caller's student and teacher applications when present. This replaces three
 * separate {@code /me} lookups and fixes a first-load bug where the navbar belt
 * and cosmetic unlocks were empty until a manual refresh.
 *
 * <p>The student/teacher sub-lookups are best-effort: if the caller has no
 * application, the respective {@code *NotFoundException} is caught and mapped to
 * {@code null} rather than failing the whole request.
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final SecurityService securityService;
    private final AuthService authService;
    private final StudentService studentService;
    private final TeacherService teacherService;

    /**
     * Returns the current user's full identity.
     */
    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> me() {
        UserEntity currentUser = securityService.requireCurrentUser();

        UserResponse user = authService.getCurrentUser(currentUser.getEmail());
        StudentResponse student = orNull(() -> studentService.getMine(currentUser),
                StudentNotFoundException.class);
        TeacherResponse teacher = orNull(() -> teacherService.getMine(currentUser),
                TeacherNotFoundException.class);

        return ResponseEntity.ok(new UserMeResponse(user, student, teacher));
    }

    /**
     * Null-safe resolver: runs the supplier and returns its value, or {@code null}
     * if it throws the given (expected, "not found") exception type. Any other
     * exception propagates so genuine errors are not swallowed.
     */
    private <T, E extends RuntimeException> T orNull(Supplier<T> supplier, Class<E> caughtType) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            if (caughtType.isInstance(ex)) {
                return null;
            }
            throw ex;
        }
    }
}
