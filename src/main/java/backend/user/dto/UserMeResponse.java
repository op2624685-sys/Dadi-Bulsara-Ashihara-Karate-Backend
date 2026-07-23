package backend.user.dto;

import backend.student.dto.StudentResponse;
import backend.teacher.dto.TeacherResponse;

/**
 * Unified identity payload for the current user. This is the single bootstrap
 * call the SPA uses instead of hitting /auth/me, /students/me and /teachers/me
 * separately — it bundles the account, plus the caller's student and teacher
 * applications when those exist.
 *
 * <p>{@code student} and {@code teacher} are {@code null} when the caller has
 * no corresponding application/record (the controller catches the respective
 * {@code *NotFoundException} and maps it to {@code null}).
 */
public record UserMeResponse(
        UserResponse user,
        StudentResponse student,
        TeacherResponse teacher
) {}
