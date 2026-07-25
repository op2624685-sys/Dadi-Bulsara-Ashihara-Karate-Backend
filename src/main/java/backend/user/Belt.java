package backend.user;

import java.util.Arrays;
import java.util.List;

/**
 * Canonical federation belt ranks. The DB stores belt as a free varchar, so this
 * enum both enumerates the allowed belts and normalizes any inbound / legacy
 * string to the nearest federation belt.
 *
 * <p>Federation order (low -> high):
 * White, Blue, Blue-Kyu, Yellow, Yellow-Kyu, Green, Green-Kyu, Brown,
 * Brown-Kyu, Black. Every "Kyu" grade is a deeper shade of its base colour on
 * the frontend. Legacy belts outside the federation are mapped to the nearest
 * federation belt: Purple->Blue, Orange->Yellow, Red-Black/Red->Black.
 */
public enum Belt {

    WHITE("White", 1),
    BLUE("Blue", 2),
    BLUE_KYU("Blue Kyu", 3),
    YELLOW("Yellow", 4),
    YELLOW_KYU("Yellow Kyu", 5),
    GREEN("Green", 6),
    GREEN_KYU("Green Kyu", 7),
    BROWN("Brown", 8),
    BROWN_KYU("Brown Kyu", 9),
    BLACK("Black", 10);

    private final String label;
    private final int rank;

    Belt(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public int rank() {
        return rank;
    }

    /** Normalizes a (possibly legacy) belt string to a federation {@link Belt}. */
    public static Belt normalize(String raw) {
        if (raw == null) return WHITE;
        String b = raw.toLowerCase().trim();
        if (b.contains("kyu")) {
            if (b.contains("blue"))   return BLUE_KYU;
            if (b.contains("yellow")) return YELLOW_KYU;
            if (b.contains("green"))  return GREEN_KYU;
            if (b.contains("brown"))  return BROWN_KYU;
            return WHITE;
        }
        if (b.contains("red"))    return BLACK; // red, red-black, red-white
        if (b.contains("black"))  return BLACK;
        if (b.contains("brown"))  return BROWN;
        if (b.contains("purple")) return BLUE;
        if (b.contains("blue"))   return BLUE;
        if (b.contains("green"))  return GREEN;
        if (b.contains("orange")) return YELLOW;
        if (b.contains("yellow")) return YELLOW;
        if (b.contains("white"))  return WHITE;
        return WHITE;
    }

    /** The canonical (title-case) belt label to persist in the DB. */
    public static String normalizeLabel(String raw) {
        return normalize(raw).label();
    }

    /** Belt rank used for cosmetic-unlock progression (matches frontend BELT_RANK). */
    public static int rank(String belt) {
        return normalize(belt).rank();
    }

    /** Every canonical federation belt label, in rank order. */
    public static List<String> allLabels() {
        return Arrays.stream(Belt.values()).map(Belt::label).toList();
    }
}
