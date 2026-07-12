package backend.user;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side mirror of the frontend cosmetics catalogue ({@code cosmetics.ts}
 * AVATARS / BANNERS). The frontend decides what to *show* as unlocked, but the
 * equip endpoint must validate against a source of truth the client cannot
 * fake — so the unlock rules and id white-list live here too.
 *
 * <p>Today the catalogue is hard-coded (kept in sync with {@code cosmetics.ts}).
 * A future follow-up can move it to a DB table so new cosmetics launch without a
 * code deploy; the per-user {@code unlockedCosmetics} list already supports that.
 */
public final class CosmeticCatalogue {

    private CosmeticCatalogue() {}

    /** A catalogue entry: the cosmetic id and the belt that unlocks it. */
    public record Entry(String id, String type, String unlockBelt) {}

    // ── Keep in sync with frontend src/app/(main)/profile/avatar/cosmetics.ts ──
    private static final List<Entry> CATALOGUE = List.of(
            new Entry("avatar_white_s1",   "avatar", "White"),
            new Entry("avatar_yellow_s1",  "avatar", "Yellow"),
            new Entry("avatar_orange_s1",  "avatar", "Orange"),
            new Entry("avatar_green_s1",   "avatar", "Green"),
            new Entry("avatar_blue_s1",    "avatar", "Blue"),
            new Entry("avatar_purple_s1",  "avatar", "Purple"),
            new Entry("avatar_brown_s1",   "avatar", "Brown"),
            new Entry("avatar_black_s1",   "avatar", "Black"),
            new Entry("banner_white_s1",   "banner", "White"),
            new Entry("banner_yellow_s1",  "banner", "Yellow"),
            new Entry("banner_orange_s1",  "banner", "Orange"),
            new Entry("banner_green_s1",   "banner", "Green"),
            new Entry("banner_blue_s1",    "banner", "Blue"),
            new Entry("banner_purple_s1",  "banner", "Purple"),
            new Entry("banner_brown_s1",   "banner", "Brown"),
            new Entry("banner_black_s1",   "banner", "Black")
    );

    /** Belt → rank, matching the frontend BELT_RANK (White=0 … Black=7). */
    public static int beltRank(String belt) {
        if (belt == null) return 0;
        String b = belt.toLowerCase();
        if (b.contains("black"))  return 7;
        if (b.contains("brown"))  return 6;
        if (b.contains("purple")) return 5;
        if (b.contains("blue"))   return 4;
        if (b.contains("green"))  return 3;
        if (b.contains("orange")) return 2;
        if (b.contains("yellow")) return 1;
        return 0; // white / unknown
    }

    /** Every catalogue id (used to unlock-all for staff with no belt). */
    public static List<String> allIds() {
        return CATALOGUE.stream().map(Entry::id).toList();
    }

    /** Ids whose unlock belt rank is at or below the given belt's rank. */
    public static List<String> unlocksForBelt(String belt) {
        int rank = beltRank(belt);
        return CATALOGUE.stream()
                .filter(e -> beltRank(e.unlockBelt()) <= rank)
                .map(Entry::id)
                .toList();
    }

    /**
     * Union of {@code existing} (nullable) and the belt-based unlocks — used when
     * a belt is set or raised. Idempotent and order-stable enough for a jsonb
     * list; preserves any event/achievement unlocks already present.
     */
    public static List<String> mergeUnlocks(List<String> existing, String belt) {
        List<String> result = new ArrayList<>();
        if (existing != null) result.addAll(existing);
        for (String id : unlocksForBelt(belt)) {
            if (!result.contains(id)) result.add(id);
        }
        return result;
    }

    /** Throws if the user is not allowed to equip {@code id}. */
    public static void requireUnlocked(UserEntity user, String id) {
        if (id == null) return;
        if (user.getUnlockedCosmetics() == null || !user.getUnlockedCosmetics().contains(id)) {
            throw new IllegalArgumentException("Cosmetic not unlocked: " + id);
        }
    }
}
