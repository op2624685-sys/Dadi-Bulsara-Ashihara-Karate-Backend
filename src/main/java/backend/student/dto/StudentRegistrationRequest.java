package backend.student.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Payload for a student membership application ("Register as Student"). The
 * applicant must be authenticated (the server records {@code userId} from the
 * session, not from this body) and MUST pick a sensei — {@code senseiId} is the
 * directory teacher who will approve the application. Validation failures
 * surface as 400 with field-level errors.
 */
public record StudentRegistrationRequest(

        /** Display name. Pre-filled from the account but editable here. */
        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email is required")
        String email,

        @Pattern(regexp = "^[0-9]{10}$", message = "A 10-digit mobile number is required")
        String mobileNumber,

        Integer age,

        /** Belt level — drives the directory avatar/banner colours. */
        @NotBlank(message = "Belt level is required")
        String belt,

        @NotBlank(message = "State is required")
        String state,

        String city,

        /** The chosen sensei (directory teacher id). Required. */
        Long senseiId,

        String fatherName,
        String motherName,
        String dob,
        String bloodGroup,
        String address,

        @Pattern(regexp = "^[0-9]{6}$", message = "A 6-digit PIN code is required")
        String pinCode,

        String photoUrl,

        List<String> achievements,

        @AssertTrue(message = "You must agree to the declaration")
        boolean declareTruth
) {
}
