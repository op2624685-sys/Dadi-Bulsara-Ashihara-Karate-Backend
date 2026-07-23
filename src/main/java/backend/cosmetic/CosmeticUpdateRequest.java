package backend.cosmetic;

/**
 * Partial-update payload for a cosmetic. Every field is nullable: a {@code null}
 * field is left unchanged (PATCH semantics), so the edit form can send only what
 * changed. {@code type} / {@code unlockType} are lowercase when present.
 */
public record CosmeticUpdateRequest(
        String type,
        String name,
        String description,
        String seasonId,
        String imageUrl,
        String unlockType,
        String unlockBelt,
        Integer requiredRank,
        Long eventId,
        Long campId,
        String primaryColor,
        String accentColor,
        String glowColor,
        String kanji,
        String patternId
) {
}
