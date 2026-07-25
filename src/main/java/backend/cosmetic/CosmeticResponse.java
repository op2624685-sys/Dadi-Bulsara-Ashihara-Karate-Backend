package backend.cosmetic;

/**
 * The full cosmetic projection returned by BOTH the public catalog
 * ({@code GET /api/v1/cosmetics}) and the admin manage list
 * ({@code GET /api/v1/admin/cosmetics}). The frontend merges this with its
 * static {@code cosmetics.ts} fallback and resolves {@code id -> item} when
 * rendering equippable avatars / banners.
 */
public record CosmeticResponse(
        String id,
        String type,            // "AVATAR" | "BANNER"
        String name,
        String description,
        String seasonId,
        String imageUrl,        // null for CSS/SVG cosmetics
        String unlockType,      // "BELT" | "EVENT_REWARD" | "CAMP_REWARD"
        String unlockBelt,      // always present — drives the visual effects
        Integer beltRank,
        Integer requiredRank,    // 1/2/3 for rewards, null for BELT
        Long eventId,
        Long campId,
        String primaryColor,
        String accentColor,
        String glowColor,
        String kanji,
        String patternId
) {
    public static CosmeticResponse of(Cosmetic c) {
        return new CosmeticResponse(
                c.getId(),
                c.getType() != null ? c.getType().name() : null,
                c.getName(),
                c.getDescription(),
                c.getSeasonId(),
                c.getImageUrl(),
                c.getUnlockType() != null ? c.getUnlockType().name() : null,
                c.getUnlockBelt(),
                c.getBeltRank(),
                c.getRequiredRank(),
                c.getEventId(),
                c.getCampId(),
                c.getPrimaryColor(),
                c.getAccentColor(),
                c.getGlowColor(),
                c.getKanji(),
                c.getPatternId());
    }
}
