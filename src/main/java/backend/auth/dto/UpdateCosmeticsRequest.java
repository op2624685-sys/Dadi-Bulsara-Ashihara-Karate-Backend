package backend.auth.dto;

/**
 * PATCH-style payload for equipping cosmetics on the current user's account.
 * Either field may be null, which means "leave unchanged". The server validates
 * each non-null id against the user's {@code unlockedCosmetics} list.
 */
public record UpdateCosmeticsRequest(
        String equippedAvatarId,
        String equippedBannerId
) {
}
