package backend.camp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link CampEntity}. Extends {@link JpaSpecificationExecutor}
 * so the admin listing can build dynamic filters the same way the teacher admin
 * listing does, while the public queries use plain derived methods.
 */
public interface CampRepository
        extends JpaRepository<CampEntity, Long>, JpaSpecificationExecutor<CampEntity> {

    /** Look up by slug regardless of publish state (admin / uniqueness checks). */
    Optional<CampEntity> findBySlug(String slug);

    /** Public detail lookup — only resolves published camps. */
    Optional<CampEntity> findBySlugAndPublishedTrue(String slug);

    /** Uniqueness guard used by slug generation. */
    boolean existsBySlug(String slug);

    /** Public list — published only, most recent camps first. */
    List<CampEntity> findByPublishedTrueOrderByYearDesc();
}
