package backend.auth.dto;

import backend.common.validation.PasswordComplexity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(min = 1, max = 100, message = "must be between 1 and 100 characters")
        String firstName,

        @NotBlank
        @Size(min = 1, max = 100, message = "must be between 1 and 100 characters")
        String lastName,

        @NotBlank
        @Email(message = "must be a valid email")
        String email,

        @NotBlank
        @Size(min = 8, max = 100, message = "must be between 8 and 100 characters")
        @PasswordComplexity
        String password
) {
        // No `role` field on purpose. Every new account is created as
        // Role.USER (see AuthServiceImpl.signup). Promotion to STUDENT /
        // TEACHER / ADMIN happens through the admin panel, never through
        // the public signup endpoint. A client attempting to POST a
        // `role` field has it silently ignored by Jackson's default
        // FAIL_ON_UNKNOWN_PROPERTIES=false.
}
