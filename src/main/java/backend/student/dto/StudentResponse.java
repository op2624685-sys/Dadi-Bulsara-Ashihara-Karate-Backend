package backend.student.dto;

import backend.student.StudentEntity;
import backend.student.StudentStatus;
import backend.teacher.TeacherEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full student record as returned by GET /{id}, /me, and by registration /
 * approval actions. Carries every column in one response object. The dojo is
 * resolved from the owning sensei (when one is present) and inlined, mirroring
 * StudentPublicResponse, so the student's own profile can show "Sensei X ·
 * Dojo Y" without a second round-trip — and consistently with the public page.
 */
public record StudentResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Integer age,
        String belt,
        String state,
        String city,
        Long senseiId,
        String senseiName,
        String dojoName,
        String dojoLocation,
        Double dojoLat,
        Double dojoLng,
        Long userId,
        String fatherName,
        String motherName,
        LocalDate dob,
        String bloodGroup,
        String mobileNumber,
        String address,
        String pinCode,
        String photo,
        String equippedAvatarId,
        String equippedBannerId,
        Integer campsCount,
        Integer eventsCount,
        boolean isChampion,
        Integer championYear,
        List<String> achievements,
        StudentStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static StudentResponse of(
            StudentEntity s, TeacherEntity sensei,
            String equippedAvatarId, String equippedBannerId) {
        String dojoName = sensei != null ? sensei.getDojoName() : null;
        String dojoLocation = sensei != null ? sensei.getDojoLocation() : null;
        Double dojoLat = sensei != null ? sensei.getDojoLat() : null;
        Double dojoLng = sensei != null ? sensei.getDojoLng() : null;
        return new StudentResponse(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                s.getEmail(),
                s.getPhone(),
                s.getAge(),
                s.getBelt(),
                s.getState(),
                s.getCity(),
                s.getSenseiId(),
                s.getSenseiName(),
                dojoName,
                dojoLocation,
                dojoLat,
                dojoLng,
                s.getUserId(),
                s.getFatherName(),
                s.getMotherName(),
                s.getDob(),
                s.getBloodGroup(),
                s.getMobileNumber(),
                s.getAddress(),
                s.getPinCode(),
                s.getPhoto(),
                equippedAvatarId,
                equippedBannerId,
                s.getCampsCount(),
                s.getEventsCount(),
                s.isChampion(),
                s.getChampionYear(),
                s.getAchievements(),
                s.getStatus(),
                s.getRejectionReason(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
