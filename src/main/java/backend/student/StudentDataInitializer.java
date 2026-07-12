package backend.student;

import backend.teacher.TeacherEntity;
import backend.teacher.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Seeds the student directory on a fresh database (ddl-auto=create wipes data
 * each restart). Creates a handful of APPROVED members so the public
 * /students directory is populated, plus a few PENDING applications routed to
 * seeded senseis so the teacher approval queue is reachable during development.
 *
 * <p>Runs after {@link backend.teacher.TeacherDataInitializer} (see @Order) so
 * the sensei directory rows already exist and can be linked via {@code senseiId}.
 * Guarded by count so it never duplicates. Disable / profile-gate in production.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class StudentDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StudentDataInitializer.class);
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    @Override
    public void run(String... args) {
        if (studentRepository.count() > 0) {
            log.info("Students already present ({}), skipping seed.", studentRepository.count());
            return;
        }

        Long dadi   = teacherId("dadi@ashihara.in");
        Long raj    = teacherId("raj@ashihara.in");
        Long sunita = teacherId("sunita@ashihara.in");
        Long arun   = teacherId("arun@ashihara.in");

        log.info("Seeding sample students…");
        studentRepository.saveAll(List.of(
            // ── APPROVED directory members (mirror the frontend demo set) ───────
            approved("Arjun", "Sharma", 14, "Black", "Maharashtra", dadi,
                    true, 2023, 6, 12, "arjun.sharma@email.com"),
            approved("Priya", "Mehta", 16, "Brown", "Gujarat", dadi,
                    false, null, 4, 8, "priya.mehta@email.com"),
            approved("Rahul", "Verma", 12, "Blue", "Delhi", raj,
                    false, null, 3, 5, "rahul.verma@email.com"),
            approved("Sneha", "Patil", 17, "Black", "Maharashtra", dadi,
                    true, 2024, 8, 15, "sneha.patil@email.com"),
            approved("Vikram", "Singh", 13, "Green", "Punjab", raj,
                    false, null, 2, 4, "vikram.singh@email.com"),
            approved("Ananya", "Iyer", 15, "Brown", "Tamil Nadu", dadi,
                    false, null, 5, 9, "ananya.iyer@email.com"),
            approved("Dev", "Kapoor", 11, "Green", "Delhi", raj,
                    false, null, 1, 2, "dev.kapoor@email.com"),
            approved("Meera", "Nair", 18, "Black", "Kerala", raj,
                    true, 2022, 9, 18, "meera.nair@email.com"),
            approved("Karan", "Joshi", 14, "Blue", "Rajasthan", dadi,
                    false, null, 3, 6, "karan.joshi@email.com"),
            approved("Ishaan", "Reddy", 16, "Brown", "Andhra Pradesh", dadi,
                    false, null, 4, 7, "ishaan.reddy@email.com"),
            approved("Tara", "Bose", 13, "Yellow", "West Bengal", raj,
                    false, null, 1, 3, "tara.bose@email.com"),
            approved("Aditya", "Kumar", 17, "Black", "Karnataka", sunita,
                    true, 2024, 7, 14, "aditya.kumar@email.com"),
            approved("Lakshmi", "Rao", 12, "White", "Karnataka", sunita,
                    false, null, 1, 2, "lakshmi.rao@email.com"),
            approved("Karthik", "Menon", 15, "Purple", "Tamil Nadu", arun,
                    false, null, 4, 6, "karthik.menon@email.com"),

            // ── PENDING applications (drive the sensei approval queue) ──────────
            pending("Rohan", "Gupta", 13, "Yellow", "Maharashtra", dadi,
                    "father.gupta@email.com", "+91 98111 22233"),
            pending("Ira", "Nair", 11, "White", "Kerala", raj,
                    "ira.nair@email.com", "+91 98111 22234"),
            pending("Veer", "Sharma", 16, "Green", "Karnataka", sunita,
                    "veer.sharma@email.com", "+91 98111 22235")
        ));

        log.info("Seeded {} sample students ({} approved).",
                studentRepository.count(), studentRepository.countByStatus(StudentStatus.APPROVED));
    }

    // ── Builders ────────────────────────────────────────────────────────────────
    private StudentEntity approved(
            String first, String last, int age, String belt, String state,
            Long senseiId, boolean champion, Integer championYear,
            int camps, int events, String email) {
        TeacherEntity sensei = senseiId != null ? teacherRepository.findById(senseiId).orElse(null) : null;
        return StudentEntity.builder()
                .firstName(first).lastName(last).age(age).belt(belt).state(state)
                .email(email)
                .senseiId(senseiId)
                .senseiName(sensei != null ? (sensei.getFirstName() + " " + sensei.getLastName()).trim() : null)
                .campsCount(camps).eventsCount(events)
                .isChampion(champion).championYear(championYear)
                .achievements(List.of())
                .status(StudentStatus.APPROVED)
                .build();
    }

    private StudentEntity pending(
            String first, String last, int age, String belt, String state,
            Long senseiId, String email, String mobile) {
        TeacherEntity sensei = senseiId != null ? teacherRepository.findById(senseiId).orElse(null) : null;
        return StudentEntity.builder()
                .firstName(first).lastName(last).age(age).belt(belt).state(state)
                .email(email).mobileNumber(mobile)
                .senseiId(senseiId)
                .senseiName(sensei != null ? (sensei.getFirstName() + " " + sensei.getLastName()).trim() : null)
                .campsCount(0).eventsCount(0)
                .achievements(List.of())
                .status(StudentStatus.PENDING)
                .build();
    }

    /** Resolve a seeded teacher's id by email; null if not found. */
    private Long teacherId(String email) {
        Optional<TeacherEntity> t = teacherRepository.findByEmailIgnoreCase(email);
        return t.map(TeacherEntity::getId).orElse(null);
    }
}
