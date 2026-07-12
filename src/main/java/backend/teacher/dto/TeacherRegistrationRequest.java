package backend.teacher.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for a public "Join as Sensei" application. Mirrors the fields the
 * frontend registration form collects. Validation failures surface as 400
 * with field-level errors (handled by GlobalExceptionHandler).
 */
public record TeacherRegistrationRequest(

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email is required")
        String email,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "A 10-digit phone number is required")
        String phone,

        @Min(value = 18, message = "Must be at least 18 years old")
        Integer age,

        @NotBlank(message = "State is required")
        String state,

        String city,

        @NotBlank(message = "Dojo name is required")
        String dojoName,

        @NotBlank(message = "Dojo location is required")
        String dojoLocation,

        @Min(value = 0, message = "Years of training cannot be negative")
        Integer yearsTraining,

        @NotBlank(message = "Speciality is required")
        String speciality,

        @NotBlank(message = "Belt level is required")
        String belt,

        /** 1..8 dan, optional for non-dan applicants. */
        Integer danGrade,

        @NotBlank(message = "Bio is required")
        @Size(max = 400, message = "Bio is too long")
        String bio,

        /** Long-form biography; optional. */
        String fullBio,

        /** Certifying organisation; optional on the form — the service
         *  defaults it to the federation when blank. */
        String certifiedBy,

        List<String> achievements,

        List<String> certifications,

        /** Optional custom avatar URL. Null → frontend default avatar. */
        String photoUrl,

        Double dojoLat,
        Double dojoLng,

        @AssertTrue(message = "You must agree to the terms")
        boolean agreeTerms
) {
}
