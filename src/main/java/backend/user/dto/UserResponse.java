package backend.user.dto;

import backend.user.Role;
import lombok.Builder;

import java.util.List;

@Builder
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role,
        String fullName,
        String managedState,
        String equippedAvatarId,
        String equippedBannerId,
        List<String> unlockedCosmetics,
        /** Belt resolved from the linked student/teacher record (null if none).
         *  A single source of truth so the navbar and profile agree without the
         *  frontend making separate student/teacher calls. */
        String belt
) {
    public static UserResponse of(backend.user.UserEntity u) {
        return UserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole())
                .fullName(u.getFirstName() + " " + u.getLastName())
                .managedState(u.getManagedState())
                .equippedAvatarId(u.getEquippedAvatarId())
                .equippedBannerId(u.getEquippedBannerId())
                .unlockedCosmetics(u.getUnlockedCosmetics())
                .belt(null)
                .build();
    }

    /** Instance variant that resolves the belt from the linked profile. */
    public static UserResponse of(backend.user.UserEntity u, backend.user.CosmeticCatalogue catalogue) {
        return UserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole())
                .fullName(u.getFirstName() + " " + u.getLastName())
                .managedState(u.getManagedState())
                .equippedAvatarId(u.getEquippedAvatarId())
                .equippedBannerId(u.getEquippedBannerId())
                .unlockedCosmetics(catalogue.effectiveUnlocks(u))
                .belt(catalogue.resolveBelt(u))
                .build();
    }
}
