package backend.student.dto;

/**
 * Payload for updating the caller's own student record from the profile page.
 * Every field is optional — a null value means "leave unchanged" (PATCH-style),
 * so the UI can send only the fields the user actually edited.
 */
public record StudentUpdateRequest(
        String fatherName,
        String motherName,
        String dob,
        String bloodGroup,
        String mobileNumber,
        String email,
        String address,
        String state,
        String city,
        String pinCode
) {
}
