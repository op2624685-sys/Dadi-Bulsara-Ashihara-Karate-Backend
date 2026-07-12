package backend.admin.dto;

import backend.user.Role;
import backend.user.UserEntity;

import java.time.Instant;

/**
 * Lightweight user row for the admin "users" table. Exposes the managed state
 * so an ADMIN can see which state each sub-admin owns.
 */
public record AdminUserSummaryResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role,
        String managedState,
        boolean emailVerified,
        boolean enabled,
        Instant createdAt
) {
    public static AdminUserSummaryResponse of(UserEntity u) {
        return new AdminUserSummaryResponse(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getRole(), u.getManagedState(), u.isEmailVerified(),
                u.isEnabled(), u.getCreatedAt());
    }
}
