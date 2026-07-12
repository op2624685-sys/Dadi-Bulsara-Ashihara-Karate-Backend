package backend.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository
        extends JpaRepository<StudentEntity, Long>,
                JpaSpecificationExecutor<StudentEntity> {

    /** The current user's own application (if any). Used by GET /students/me. */
    Optional<StudentEntity> findByUserId(Long userId);

    /** All applications routed to a given sensei (teacher approval queue). */
    List<StudentEntity> findBySenseiId(Long senseiId);

    /** True if a user already has a live application (pending or approved). */
    boolean existsByUserIdAndStatusIn(Long userId, List<StudentStatus> statuses);

    // ── Counts for the admin dashboard ────────────────────────────────────────
    long countByStatus(StudentStatus status);
}
