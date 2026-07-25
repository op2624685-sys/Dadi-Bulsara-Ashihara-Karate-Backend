package backend.cosmetic;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload to create a new cosmetic (admin CMS). Two creation paths:
 *   * POST /api/v1/admin/cosmetics       — JSON, imageUrl may be a pre-existing URL
 *   * POST /api/v1/admin/cosmetics/new   — multipart with `payload` + optional `file`.
 * The server uploads the file in the multipart path and stores the resulting URL.
 *
 * <p>{@code type} / {@code unlockType} are sent lowercase
 * ("avatar"/"banner", "belt"/"event_reward"/"camp_reward") to match the
 * frontend; the service maps them to the backend enums. {@code unlockBelt} is
 * ALWAYS required — it drives the visual effects for every cosmetic flavour.
 */
public record CosmeticCreateRequest(

        @NotBlank(message = "Type is required")
        String type,                       // "avatar" | "banner"

        @NotBlank(message = "Name is required")
        String name,

        String description,
        String seasonId,

        /** Null → CSS/SVG cosmetic; non-null → uploaded image cosmetic. */
        String imageUrl,

        @NotBlank(message = "Unlock type is required")
        String unlockType,                 // "belt" | "event_reward" | "camp_reward"

        @NotBlank(message = "Effect belt is required")
        String unlockBelt,                // always present — drives the visual effects

        /** 1/2/3 for reward cosmetics; null for belt cosmetics. */
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
