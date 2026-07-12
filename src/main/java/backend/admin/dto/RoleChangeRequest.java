package backend.admin.dto;

import backend.user.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Body for promoting/demoting a user. When {@code role} is SUB_ADMIN,
 * {@code state} is required (a sub-admin must be bound to a state).
 */
public record RoleChangeRequest(
        @NotNull Role role,
        String state
) {
}
