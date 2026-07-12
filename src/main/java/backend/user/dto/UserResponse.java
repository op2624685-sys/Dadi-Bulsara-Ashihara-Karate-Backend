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
        List<String> unlockedCosmetics
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
                .build();
    }
}
