package backend.user;

import backend.student.StudentEntity;
import backend.student.StudentRepository;
import backend.teacher.TeacherEntity;
import backend.teacher.TeacherRepository;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Normalizes legacy / non-federation belt labels already stored against
 * student and teacher records to the canonical federation belt label. Runs on
 * every startup but is idempotent: a belt that is already a federation label
 * (or already normalized, e.g. lower-case "blue" → "Blue") is left unchanged.
 *
 * <p>Maps Purple->Blue, Orange->Yellow, Red-Black/Red->Black, and preserves kyu grades.
 * Mirrors {@link Belt#normalizeLabel}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeltNormalizationRunner {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    @PostConstruct
    public void normalize() {
        int updated = 0;

        for (StudentEntity student : studentRepository.findAll()) {
            String belt = student.getBelt();
            if (isBlank(belt)) continue;
            String normalized = Belt.normalizeLabel(belt);
            if (!normalized.equals(belt)) {
                student.setBelt(normalized);
                studentRepository.save(student);
                updated++;
            }
        }

        for (TeacherEntity teacher : teacherRepository.findAll()) {
            String belt = teacher.getBelt();
            if (isBlank(belt)) continue;
            String normalized = Belt.normalizeLabel(belt);
            if (!normalized.equals(belt)) {
                teacher.setBelt(normalized);
                teacherRepository.save(teacher);
                updated++;
            }
        }

        if (updated > 0) {
            log.info("Belt normalization: migrated {} legacy belt label(s) to federation ranks", updated);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
