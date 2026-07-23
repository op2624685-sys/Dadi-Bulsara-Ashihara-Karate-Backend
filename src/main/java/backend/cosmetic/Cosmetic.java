package backend.cosmetic;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single equippable cosmetic (avatar or banner), row in the {@code cosmetic}
 * table. This is the DB-backed catalogue that replaces the hard-coded
 * {@code CosmeticCatalogue}.
 *
 * <p>Two flavours coexist:
 * <ul>
 *   <li><b>CSS / SVG cosmetic</b> — {@code imageUrl == null}; the frontend
 *       renders a kanji + belt-driven aura/effects from the colour fields.</li>
 *   <li><b>Image cosmetic</b> — {@code imageUrl != null}; the frontend swaps
 *       the central content for the uploaded image but KEEPS the belt-driven
 *       effects (aura rings, glow, conic gradient) driven by {@code unlockBelt}.</li>
 * </ul>
 *
 * <p>{@code unlockBelt} is ALWAYS present. For a BELT cosmetic it is both the
 * unlock condition and the visual belt. For an EVENT/CAMP_REWARD cosmetic it is
 * only the chosen VISUAL belt (admin picks it at creation for the effect
 * colour/aura) — the actual unlock is the reward grant.
 */
@Entity
@Table(name = "cosmetic")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cosmetic {

    /** Stable id, e.g. "avatar_white_s1" (seeded CSS) or "avatar_img_a1b2c3d4" (uploaded). */
    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CosmeticType type;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "season_id", length = 20)
    private String seasonId;

    /** Null → pure CSS/SVG cosmetic; non-null → uploaded image cosmetic. */
    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "unlock_type", nullable = false, length = 20)
    private UnlockType unlockType;

    /** Always present (see class javadoc). The belt that drives the visual effects. */
    @Column(name = "unlock_belt", nullable = false, length = 20)
    private String unlockBelt;

    /** Derived rank of {@link #unlockBelt}; indexed for the belt-unlock query. */
    @Column(name = "belt_rank", nullable = false)
    private int beltRank;

    /** 1/2/3 for reward cosmetics; null for BELT cosmetics. */
    @Column(name = "required_rank")
    private Integer requiredRank;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "camp_id")
    private Long campId;

    // ── CSS visual fields (nullable; kept for backward-compat with cosmetics.ts) ──
    @Column(name = "primary_color", length = 32)
    private String primaryColor;

    @Column(name = "accent_color", length = 32)
    private String accentColor;

    @Column(name = "glow_color", length = 64)
    private String glowColor;

    @Column(name = "kanji", length = 8)
    private String kanji;

    @Column(name = "pattern_id", length = 32)
    private String patternId;
}
