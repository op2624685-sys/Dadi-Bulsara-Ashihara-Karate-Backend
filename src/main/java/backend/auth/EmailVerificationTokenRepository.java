package backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
           UPDATE EmailVerificationToken t
              SET t.used = true,
                  t.usedAt = CURRENT_TIMESTAMP
            WHERE t.user.id = :userId
              AND t.used = false
           """)
    int invalidateAllForUser(@Param("userId") Long userId);
}
