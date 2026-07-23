package backend.user;

import backend.student.StudentEntity;
import backend.student.StudentRepository;
import backend.teacher.TeacherEntity;
import backend.teacher.TeacherRepository;
import jakarta.annotation.PostConstruct;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One-time seeding of {@code unlockedCosmetics} for users that already exist
 * when this feature ships. New users are seeded at creation time (signup is
 * role USER with no profile yet → left empty; the student/teacher application
 * then merges belt unlocks; admins/sub-admins are unlocked-all at creation).
 *
 * <p>This runner only fills users whose list is still null/empty, so it is
 * idempotent and safe to run on every startup. It mirrors the creation-time
 * rules: a linked student/teacher record → belt unlocks; an admin/sub-admin →
 * all; a bare USER account with no profile → left empty (seeded on application).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CosmeticsBackfillRunner {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CosmeticCatalogue cosmeticCatalogue;

    @PostConstruct
    public void backfill() {
        var users = userRepository.findAll();
        int updated = 0;

        for (UserEntity user : users) {
            if (user.getUnlockedCosmetics() != null && !user.getUnlockedCosmetics().isEmpty()) {
                continue;
            }

            List<String> unlocks;
            StudentEntity student = studentRepository.findByUserId(user.getId()).orElse(null);
            if (student != null && isNotBlank(student.getBelt())) {
                unlocks = cosmeticCatalogue.mergeUnlocks(user.getUnlockedCosmetics(), student.getBelt());
            } else {
                TeacherEntity teacher = teacherRepository.findByUserId(user.getId()).orElse(null);
                if (teacher != null && isNotBlank(teacher.getBelt())) {
                    unlocks = cosmeticCatalogue.mergeUnlocks(user.getUnlockedCosmetics(), teacher.getBelt());
                } else if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUB_ADMIN) {
                    unlocks = cosmeticCatalogue.allIds();
                } else {
                    // Bare USER account — no profile yet. Seeded when they apply.
                    continue;
                }
            }

            user.setUnlockedCosmetics(unlocks);
            userRepository.save(user);
            updated++;
        }

        if (updated > 0) {
            log.info("Cosmetics backfill: seeded unlockedCosmetics for {} existing user(s)", updated);
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
