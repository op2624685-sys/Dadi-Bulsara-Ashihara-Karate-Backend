package backend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link EventEntity}. Extends {@link JpaSpecificationExecutor}
 * so the admin listing can build dynamic (state-scoped) filters.
 */
public interface EventRepository
        extends JpaRepository<EventEntity, Long>, JpaSpecificationExecutor<EventEntity> {

    /** Look up by slug regardless of publish state (admin / uniqueness checks). */
    Optional<EventEntity> findBySlug(String slug);

    /** Public detail lookup — only resolves published events. */
    Optional<EventEntity> findBySlugAndPublishedTrue(String slug);

    /** Uniqueness guard used by slug generation. */
    boolean existsBySlug(String slug);

    /** Public list — published only, most recent event date first. */
    List<EventEntity> findByPublishedTrueOrderByEventDateDesc();
}
