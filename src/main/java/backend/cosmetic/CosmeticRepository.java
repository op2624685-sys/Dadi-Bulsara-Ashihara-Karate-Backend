package backend.cosmetic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Spring Data JPA access for the {@code cosmetic} catalogue table. */
@Repository
public interface CosmeticRepository extends JpaRepository<Cosmetic, String> {

    /** Belt cosmetics whose effect-belt rank is at or below {@code beltRank}. */
    List<Cosmetic> findByUnlockTypeAndBeltRankLessThanEqual(UnlockType type, int beltRank);

    /** All cosmetics of a given unlock type (e.g. every BELT cosmetic). */
    List<Cosmetic> findByUnlockType(UnlockType type);

    /** Every cosmetic, ordered by id (stable listing for the admin + public catalog). */
    List<Cosmetic> findAllByOrderByIdAsc();

    /** Duplicate-name guard for admin create (case-insensitive per type). */
    boolean existsByTypeAndNameIgnoreCase(CosmeticType type, String name);
}
