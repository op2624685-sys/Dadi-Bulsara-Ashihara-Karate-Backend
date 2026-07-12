package backend.student.dto;

import backend.student.StudentEntity;

import java.time.Instant;
import java.util.List;

/**
 * Lightweight projection for the /students directory cards. Deliberately omits
 * the heavy identity-only fields (fatherName, motherName, dob, bloodGroup,
 * address, pinCode, mobileNumber, email, phone) so the paginated list query
 * stays small. Built in the service from the entity — the jsonb column is
 * simply not copied across.
 */
public record StudentSummaryResponse(
        Long id,
        String firstName,
        String lastName,
        String belt,
        Integer age,
        String state,
        String city,
        String senseiName,
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
    public static StudentSummaryResponse of(
            StudentEntity s, String equippedAvatarId, String equippedBannerId) {
        return new StudentSummaryResponse(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                s.getBelt(),
                s.getAge(),
                s.getState(),
                s.getCity(),
                s.getSenseiName(),
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
