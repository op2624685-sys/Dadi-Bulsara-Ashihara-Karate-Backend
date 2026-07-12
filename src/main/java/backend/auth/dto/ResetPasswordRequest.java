package backend.auth.dto;

import backend.common.validation.PasswordComplexity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,

        @NotBlank
        @Size(min = 8, max = 100, message = "must be between 8 and 100 characters")
        @PasswordComplexity
        String newPassword
) {}
