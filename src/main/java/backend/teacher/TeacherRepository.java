package backend.teacher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherRepository
        extends JpaRepository<TeacherEntity, Long>,
                JpaSpecificationExecutor<TeacherEntity> {

    /**
     * Used by the seeder so it only inserts sample data on a fresh database.
     */
    boolean existsByEmailIgnoreCase(String email);

    Optional<TeacherEntity> findByEmailIgnoreCase(String email);

    /** Resolve the directory profile owned by a given account (role TEACHER). */
    Optional<TeacherEntity> findByUserId(Long userId);

    // ── Counts for the admin dashboard ────────────────────────────────────────
    long countByStatus(TeacherStatus status);

    long countByStatusAndStateIgnoreCase(TeacherStatus status, String state);

    long countByStateIgnoreCase(String state);
}
