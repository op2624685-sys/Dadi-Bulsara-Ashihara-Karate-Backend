package backend.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for creating a state-scoped sub-admin. The creating ADMIN supplies the
 * state the sub-admin will be bound to.
 */
public record SubAdminCreateRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String state
) {
}
