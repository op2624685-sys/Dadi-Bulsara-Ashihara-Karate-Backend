package backend.teacher.dto;

import backend.teacher.TeacherEntity;

import java.time.Instant;
import java.util.List;

/**
 * Lightweight projection for the directory list / cards. Deliberately omits
 * the heavy detail-only fields (fullBio, studentsList, timeline,
 * certifications, email, phone, geo) so the paginated list query stays small.
 * Built in the service from the entity — the jsonb columns are simply not
 * copied across.
 */
public record TeacherSummaryResponse(
        Long id,
        String firstName,
        String lastName,
        String belt,
        String rank,
        Integer danGrade,
        Integer age,
        String state,
        String email,
        String phone,
        String certifiedBy,
        String dojoName,
        String dojoLocation,
        String photo,
        String bannerUrl,
        String equippedAvatarId,
        String equippedBannerId,
        String speciality,
        Integer yearsTraining,
        Integer students,
        Integer campsHosted,
        Integer seminarsGiven,
        String bio,
        List<String> achievements,
        boolean featured,
        Instant createdAt
) {
    public static TeacherSummaryResponse of(
            TeacherEntity t, String equippedAvatarId, String equippedBannerId) {
        return new TeacherSummaryResponse(
                t.getId(),
                t.getFirstName(),
                t.getLastName(),
                t.getBelt(),
                t.getRank(),
                t.getDanGrade(),
                t.getAge(),
                t.getState(),
                t.getEmail(),
                t.getPhone(),
                t.getCertifiedBy(),
                t.getDojoName(),
                t.getDojoLocation(),
                t.getPhoto(),
                t.getBannerUrl(),
                equippedAvatarId,
                equippedBannerId,
                t.getSpeciality(),
                t.getYearsTraining(),
                t.getStudents(),
                t.getCampsHosted(),
                t.getSeminarsGiven(),
                t.getBio(),
                t.getAchievements(),
                t.isFeatured(),
                t.getCreatedAt());
    }
}
