package backend.teacher.dto;

import backend.teacher.TeacherEntity;
import backend.teacher.TeacherStatus;
import backend.teacher.TimelineEntry;

import java.time.Instant;
import java.util.List;

/**
 * Full teacher record as returned by GET /{id} and by registration. Carries
 * every column, including the jsonb collections, in one response object.
 */
public record TeacherResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Integer age,
        String belt,
        String rank,
        Integer danGrade,
        String state,
        String city,
        String dojoName,
        String dojoLocation,
        Double dojoLat,
        Double dojoLng,
        Integer yearsTraining,
        Integer students,
        Integer campsHosted,
        Integer seminarsGiven,
        String speciality,
        String bio,
        String fullBio,
        String certifiedBy,
        String photo,
        String bannerUrl,
        String equippedAvatarId,
        String equippedBannerId,
        List<String> achievements,
        List<String> certifications,
        List<String> studentsList,
        List<TimelineEntry> timeline,
        TeacherStatus status,
        String rejectionReason,
        boolean featured,
        Instant createdAt,
        Instant updatedAt
) {
    public static TeacherResponse of(
            TeacherEntity t, String equippedAvatarId, String equippedBannerId) {
        return new TeacherResponse(
                t.getId(),
                t.getFirstName(),
                t.getLastName(),
                t.getEmail(),
                t.getPhone(),
                t.getAge(),
                t.getBelt(),
                t.getRank(),
                t.getDanGrade(),
                t.getState(),
                t.getCity(),
                t.getDojoName(),
                t.getDojoLocation(),
                t.getDojoLat(),
                t.getDojoLng(),
                t.getYearsTraining(),
                t.getStudents(),
                t.getCampsHosted(),
                t.getSeminarsGiven(),
                t.getSpeciality(),
                t.getBio(),
                t.getFullBio(),
                t.getCertifiedBy(),
                t.getPhoto(),
                t.getBannerUrl(),
                equippedAvatarId,
                equippedBannerId,
                t.getAchievements(),
                t.getCertifications(),
                t.getStudentsList(),
                t.getTimeline(),
                t.getStatus(),
                t.getRejectionReason(),
                t.isFeatured(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
