package backend.teacher.dto;

import java.util.List;

/**
 * Payload for updating the caller's own teacher record from the profile page.
 * Every field is optional — a null value means "leave unchanged" (PATCH-style),
 * so the UI can send only the fields the user actually edited.
 */
public record TeacherUpdateRequest(
        String phone,
        String dojoName,
        String dojoLocation,
        String city,
        String state,
        String belt,
        Integer danGrade,
        String speciality,
        Integer yearsTraining,
        Integer age,
        String bio,
        String certifiedBy,
        List<String> achievements,
        List<String> certifications,
        Integer students,
        Integer campsHosted,
        Integer seminarsGiven
) {
}
