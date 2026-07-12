package backend.teacher.dto;

/**
 * Optional body for the reject action. Reason is free text an admin can leave
 * for the applicant; null/blank is allowed.
 */
public record TeacherReviewRequest(String reason) {
}
