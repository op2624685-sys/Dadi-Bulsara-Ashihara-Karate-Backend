package backend.student.dto;

import backend.student.StudentEntity;
import backend.teacher.TeacherEntity;

import java.time.Instant;
import java.util.List;

/**
 * Public, sanitized projection for the student public profile page
 * (GET /api/v1/students/{id}/public). This is the response contract that
 * anonymous visitors receive — it deliberately omits every identity-only /
 * personal field (email, phone, mobileNumber, fatherName, motherName, dob,
 * bloodGroup, address, pinCode, userId, status, rejectionReason).
 *
 * <p>The dojo is resolved from the owning sensei in the service and inlined
 * here so the public profile can show "Sensei X · Dojo Y" without the client
 * making a second call (and without exposing the sensei's own record).
 */
public record StudentPublicResponse(
        Long id,
        String firstName,
        String lastName,
        String belt,
        Integer age,
        String state,
        String city,
        Long senseiId,
        String senseiName,
        String dojoName,
        String dojoLocation,
        Double dojoLat,
        Double dojoLng,
        String photo,
        String equippedAvatarId,
        String equippedBannerId,
        Integer campsCount,
        Integer eventsCount,
        boolean isChampion,
        Integer championYear,
        List<String> achievements,
        Instant createdAt
) {
    public static StudentPublicResponse of(
            StudentEntity s, TeacherEntity sensei,
            String equippedAvatarId, String equippedBannerId) {
        String dojoName = sensei != null ? sensei.getDojoName() : null;
        String dojoLocation = sensei != null ? sensei.getDojoLocation() : null;
        Double dojoLat = sensei != null ? sensei.getDojoLat() : null;
        Double dojoLng = sensei != null ? sensei.getDojoLng() : null;
        return new StudentPublicResponse(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                s.getBelt(),
                s.getAge(),
                s.getState(),
                s.getCity(),
                s.getSenseiId(),
                s.getSenseiName(),
                dojoName,
                dojoLocation,
                dojoLat,
                dojoLng,
                s.getPhoto(),
                equippedAvatarId,
                equippedBannerId,
                s.getCampsCount(),
                s.getEventsCount(),
                s.isChampion(),
                s.getChampionYear(),
                s.getAchievements(),
                s.getCreatedAt());
    }
}
