package backend.user;

import backend.student.StudentEntity;
import backend.student.StudentRepository;
import backend.teacher.TeacherEntity;
import backend.teacher.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time backfill: the {@code state} column on {@link UserEntity} was added
 * after students/teachers already existed, and is normally populated from the
 * linked profile at registration time. This runner copies the profile state
 * onto any STUDENT/TEACHER account whose {@code state} is still null, so
 * sub-admin state-scoping works for pre-existing data too. Idempotent — it
 * only touches accounts with a null state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountStateBackfillRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = 0;

        for (StudentEntity s : studentRepository.findAll()) {
            if (s.getUserId() == null || s.getState() == null) continue;
            Long userId = s.getUserId();
            String state = s.getState();
            updated += userRepository.findById(userId).filter(u -> u.getState() == null
                    && u.getRole() == Role.STUDENT)
                    .map(u -> {
                        u.setState(state);
                        userRepository.save(u);
                        return 1;
                    }).orElse(0);
        }

        for (TeacherEntity t : teacherRepository.findAll()) {
            if (t.getUserId() == null || t.getState() == null) continue;
            Long userId = t.getUserId();
            String state = t.getState();
            updated += userRepository.findById(userId).filter(u -> u.getState() == null
                    && u.getRole() == Role.TEACHER)
                    .map(u -> {
                        u.setState(state);
                        userRepository.save(u);
                        return 1;
                    }).orElse(0);
        }

        if (updated > 0) {
            log.info("Backfilled account.state for {} existing student/teacher account(s)", updated);
        }
    }
}
