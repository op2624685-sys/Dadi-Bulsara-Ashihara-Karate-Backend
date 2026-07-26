package backend.user;

import backend.cosmetic.Cosmetic;
import backend.cosmetic.CosmeticRepository;
import backend.cosmetic.CosmeticType;
import backend.cosmetic.UnlockType;
import backend.student.StudentEntity;
import backend.student.StudentRepository;
import backend.teacher.TeacherEntity;
import backend.teacher.TeacherRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side mirror of the frontend cosmetics catalogue. Historically hard-coded
 * (kept in sync with {@code cosmetics.ts}); now backed by the {@code cosmetic}
 * table so a new cosmetic can ship without a code deploy, and image cosmetics can
 * be added by admins at runtime.
 *
 * <p>Seeds the original 20 CSS/SVG belt cosmetics (White→Black avatars +
 * banners) on first boot if the table is empty, preserving the exact ids the
 * frontend's static {@code cosmetics.ts} still uses as a fallback.</p>
 *
 * <p>Method semantics are unchanged from the hard-coded era, so every call site
 * (StudentServiceImpl, TeacherServiceImpl, AuthServiceImpl, CosmeticsBackfillRunner)
 * keeps working after being switched from static calls to an injected instance.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CosmeticCatalogue {

    private final CosmeticRepository cosmeticRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    private static final String SEASON = "S1";

    /** Belt rank, matching the frontend BELT_RANK (White=0 ... Black=9). */
    public int beltRank(String belt) {
        return Belt.rank(belt);
    }

    /** Every catalogue id (used to unlock-all for staff with no belt). */
    public List<String> allIds() {
        return cosmeticRepository.findAllByOrderByIdAsc().stream().map(Cosmetic::getId).toList();
    }

    /** Ids whose unlock belt rank is at or below the given belt's rank. */
    public List<String> unlocksForBelt(String belt) {
        int rank = beltRank(belt);
        return cosmeticRepository
                .findByUnlockTypeAndBeltRankLessThanEqual(UnlockType.BELT, rank)
                .stream().map(Cosmetic::getId).toList();
    }

    /**
     * Union of {@code existing} (nullable) and the belt-based unlocks — used when
     * a belt is set or raised. Idempotent and order-stable enough for a jsonb
     * list; preserves any event/achievement unlocks already present.
     */
    public List<String> mergeUnlocks(List<String> existing, String belt) {
        List<String> result = new ArrayList<>();
        if (existing != null) result.addAll(existing);
        for (String id : unlocksForBelt(belt)) {
            if (!result.contains(id)) result.add(id);
        }
        return result;
    }

    /**
     * The cosmetics a user can actually equip, computed at read time. This is the
     * stored snapshot (event/reward unlocks) PLUS every BELT cosmetic whose belt
     * rank the user qualifies for — so a cosmetic an admin creates later for the
     * user's belt shows up immediately, without a backfill or stored-list rewrite.
     * Admins/sub-admins get the full catalogue.
     */
    public List<String> effectiveUnlocks(UserEntity user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUB_ADMIN) {
            return allIds();
        }
        // White is the baseline belt every account starts at. If no linked
        // student/teacher belt resolves (fresh account, or an orphaned student
        // row with a null user_id), fall back to White so White-belt cosmetics —
        // including ones an admin creates later — still unlock. A real belt,
        // once linked, takes over on the next read.
        String belt = resolveBelt(user);
        if (belt == null) belt = "White";
        return mergeUnlocks(user.getUnlockedCosmetics(), belt);
    }

    /** Throws if the user is not allowed to equip {@code id}. */
    public void requireUnlocked(UserEntity user, String id) {
        if (id == null) return;
        if (!effectiveUnlocks(user).contains(id)) {
            throw new IllegalArgumentException("Cosmetic not unlocked: " + id);
        }
    }

    /**
     * Resolve the user's belt from their linked student/teacher profile (the
     * belt lives on those records, not on the account). Falls back to the
     * account's own belt field if present. Returns null if no belt resolves.
     */
    public String resolveBelt(UserEntity user) {
        StudentEntity student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student != null && isNotBlank(student.getBelt())) {
            return student.getBelt();
        }
        TeacherEntity teacher = teacherRepository.findByUserId(user.getId()).orElse(null);
        if (teacher != null && isNotBlank(teacher.getBelt())) {
            return teacher.getBelt();
        }
        return null;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // First-boot seed of the 20 original CSS belt cosmetics.
    // ─────────────────────────────────────────────────────────────────────────
    @PostConstruct
    public void seedIfEmpty() {
        if (cosmeticRepository.count() > 0) {
            return;
        }
        log.info("Seeding {} original CSS belt cosmetics…", SEED.size());
        cosmeticRepository.saveAll(SEED.stream().map(Seed::toEntity).toList());
    }

    // (type, id, name, description, belt, primary, accent, glow, kanji, pattern)
    private record Seed(
            CosmeticType type, String id, String name, String description,
            String belt, String primary, String accent, String glow, String kanji, String pattern) {
        Cosmetic toEntity() {
            return Cosmetic.builder()
                    .id(id).type(type).name(name).description(description)
                    .seasonId(SEASON).imageUrl(null)
                    .unlockType(UnlockType.BELT).unlockBelt(belt)
                    .beltRank(Belt.rank(belt))
                    .requiredRank(null).eventId(null).campId(null)
                    .primaryColor(primary).accentColor(accent)
                    .glowColor(glow).kanji(kanji).patternId(pattern)
                    .build();
        }
    }

    // Mirror of cosmetics.ts AVATARS + BANNERS (ids must stay identical).
    private static final List<Seed> SEED = List.of(
        new Seed(CosmeticType.AVATAR, "avatar_white_s1",      "Blank Slate",        "The beginning of every journey.",          "White",     "#e8e8e8", "#aaaaaa", "rgba(255,255,255,0.4)", "無", "white_orb"),
        new Seed(CosmeticType.AVATAR, "avatar_blue_s1",       "Deep Current",      "Unshakeable as the ocean floor.",         "Blue",      "#2563EB", "#1E3A8A", "rgba(37,99,235,0.55)",  "流", "blue_current"),
        new Seed(CosmeticType.AVATAR, "avatar_blue_kyu_s1",  "Deep Current — Kyu","The current, drawn deeper still.",       "Blue Kyu",  "#1E4FA0", "#16335F", "rgba(30,79,160,0.55)",  "流", "blue_current"),
        new Seed(CosmeticType.AVATAR, "avatar_yellow_s1",     "First Light",       "The spark that ignites discipline.",         "Yellow",    "#FBBF24", "#D97706", "rgba(251,191,36,0.5)",  "光", "yellow_flame"),
        new Seed(CosmeticType.AVATAR, "avatar_yellow_kyu_s1","First Light — Kyu","The spark, tempered to ember.",           "Yellow Kyu","#C99A0A", "#8C6F08", "rgba(201,154,10,0.5)",  "光", "yellow_flame"),
        new Seed(CosmeticType.AVATAR, "avatar_green_s1",      "Still Forest",      "Patience rooted deeper than oak.",         "Green",     "#16A34A", "#14532D", "rgba(22,163,74,0.5)",   "森", "green_forest"),
        new Seed(CosmeticType.AVATAR, "avatar_green_kyu_s1", "Still Forest — Kyu","The forest, deeper in shadow.",         "Green Kyu", "#15803D", "#0F3D24", "rgba(21,128,61,0.5)",  "森", "green_forest"),
        new Seed(CosmeticType.AVATAR, "avatar_brown_s1",      "Iron Earth",       "Hardened by a thousand strikes.",           "Brown",     "#92400E", "#451A03", "rgba(146,64,14,0.6)",  "鉄", "brown_iron"),
        new Seed(CosmeticType.AVATAR, "avatar_brown_kyu_s1", "Iron Earth — Kyu","The iron, hardened further.",             "Brown Kyu", "#6B2E0A", "#3E1A04", "rgba(107,46,10,0.6)",  "鉄", "brown_iron"),
        new Seed(CosmeticType.AVATAR, "avatar_black_s1",      "Void Sovereign",    "Mastery forged in the dark.",               "Black",     "#1a1a1a", "#C9A84C", "rgba(201,168,76,0.7)", "武", "black_sovereign"),
        new Seed(CosmeticType.BANNER, "banner_white_s1",      "Morning Mist",     "Clarity before the first kata.",           "White",     "#d1d5db", "#9ca3af", "rgba(255,255,255,0.2)", "始", "mist"),
        new Seed(CosmeticType.BANNER, "banner_blue_s1",       "Tidal Force",      "Overwhelming, inevitable, precise.",       "Blue",      "#2563EB", "#0C1A4E", "rgba(37,99,235,0.35)", "潮", "tidal"),
        new Seed(CosmeticType.BANNER, "banner_blue_kyu_s1", "Tidal Force — Kyu","The tide, drawn into the deep.",          "Blue Kyu",  "#1E4FA0", "#081A33", "rgba(30,79,160,0.35)", "潮", "tidal"),
        new Seed(CosmeticType.BANNER, "banner_yellow_s1",     "Golden Dawn",      "The horizon breaks for the determined.",    "Yellow",    "#FBBF24", "#78350F", "rgba(251,191,36,0.3)", "朝", "dawn"),
        new Seed(CosmeticType.BANNER, "banner_yellow_kyu_s1","Golden Dawn — Kyu","The dawn, dimmed to amber.",             "Yellow Kyu","#C99A0A", "#5C4706", "rgba(201,154,10,0.3)", "朝", "dawn"),
        new Seed(CosmeticType.BANNER, "banner_green_s1",      "Ancient Grove",     "Where warriors have trained for centuries.","Green",     "#16A34A", "#052E16", "rgba(22,163,74,0.3)",  "道", "grove"),
        new Seed(CosmeticType.BANNER, "banner_green_kyu_s1", "Ancient Grove — Kyu","The grove, deeper in shadow.",          "Green Kyu", "#15803D", "#03210F", "rgba(21,128,61,0.3)", "道", "grove"),
        new Seed(CosmeticType.BANNER, "banner_brown_s1",      "Broken Mountain",   "Nothing remains unbeaten by patience.",    "Brown",     "#92400E", "#1C0A00", "rgba(146,64,14,0.4)",  "山", "mountain"),
        new Seed(CosmeticType.BANNER, "banner_brown_kyu_s1", "Broken Mountain — Kyu","The mountain, worn to stone.",          "Brown Kyu", "#6B2E0A", "#150A02", "rgba(107,46,10,0.4)", "山", "mountain"),
        new Seed(CosmeticType.BANNER, "banner_black_s1",      "Void Banner",      "The final mark. Worn by the few.",         "Black",     "#0a0a0a", "#C9A84C", "rgba(201,168,76,0.5)", "武", "void")
    );
}
