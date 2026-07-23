package backend.teacher.dto;

import backend.teacher.TeacherEntity;
import backend.teacher.TeacherStatus;

import java.time.Instant;

/**
 * Admin-facing teacher row. Unlike {@link TeacherSummaryResponse} it is NOT
 * restricted to APPROVED teachers and exposes {@code status} (and
 * {@code rejectionReason}) so an admin can triage the approval queue. Still a
 * single-row projection — no N+1.
 */
public record TeacherAdminSummaryResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String state,
        String belt,
        String rank,
        Integer danGrade,
        String speciality,
        Integer yearsTraining,
        TeacherStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        // ── Detail fields for the application-review modal ──
        String city,
        String dojoName,
        String dojoLocation,
        Integer age,
        String bio,
        String certifiedBy,
        String photo,
        String bannerUrl
) {
    public static TeacherAdminSummaryResponse of(TeacherEntity t) {
        return new TeacherAdminSummaryResponse(
                t.getId(),
                t.getFirstName(),
                t.getLastName(),
                t.getEmail(),
                t.getPhone(),
                t.getState(),
                t.getBelt(),
                t.getRank(),
                t.getDanGrade(),
                t.getSpeciality(),
                t.getYearsTraining(),
                t.getStatus(),
                t.getRejectionReason(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getCity(),
                t.getDojoName(),
                t.getDojoLocation(),
                t.getAge(),
                t.getBio(),
                t.getCertifiedBy(),
                t.getPhoto(),
                t.getBannerUrl());
    }
}
