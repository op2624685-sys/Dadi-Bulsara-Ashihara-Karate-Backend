package backend.teacher;

import backend.user.Provider;
import backend.user.Role;
import backend.user.UserEntity;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the directory with the federation's founding instructors on a fresh
 * database (ddl-auto=create wipes data each restart, so this keeps the
 * Teachers tab populated during development). Guarded by email so it never
 * duplicates. In production this runner would be disabled / behind a profile.
 *
 * <p>It also provisions a linked {@link Role#TEACHER} login account for every
 * seeded instructor and stamps {@code user_id} on the directory row, so a
 * teacher can actually sign in and — among other things — approve the student
 * applications routed to them. Credentials are dev-only and logged at startup.
 */
@Component
@Profile("!prod")
@Order(1)
@RequiredArgsConstructor
public class TeacherDataInitializer implements CommandLineRunner {

    /** Dev password every seeded teacher account shares. CHANGE IN PRODUCTION. */
    static final String SEED_TEACHER_PASSWORD = "Teacher123!";

    private static final Logger log = LoggerFactory.getLogger(TeacherDataInitializer.class);
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (teacherRepository.count() > 0) {
            log.info("Teachers already present ({}), skipping seed.", teacherRepository.count());
            return;
        }

        log.info("Seeding sample teachers…");
        List<TeacherEntity> saved = teacherRepository.saveAll(List.of(
            // ── PENDING applications (drive the approval queue) ────────────────
            // One in Kerala (the seeded sub-admin's state) and one in Maharashtra
            // (admin-only) so both roles have something to action.
            TeacherEntity.builder()
                .firstName("Asha").lastName("Nambiar")
                .email("asha.nambiar@ashihara.in").phone("+91 98765 43220").age(29)
                .belt("Brown").rank("Sandan").danGrade(3)
                .state("Kerala").city("Thiruvananthapuram")
                .dojoName("Nambiar Karate Academy").dojoLocation("Thiruvananthapuram, Kerala")
                .dojoLat(8.5241).dojoLng(76.9366)
                .yearsTraining(12).students(9).campsHosted(2).seminarsGiven(5)
                .speciality("Kata").certifiedBy("Dadi Bulsara Ashihara Karate")
                .bio("Aspiring instructor applying to join the federation. Trained under the Kerala chapter for 12 years.")
                .fullBio("Asha Nambiar is an aspiring instructor applying to join the federation. With 12 years of training under the Kerala chapter, she focuses on kata precision and youth development.")
                .achievements(List.of("State Kata Finalist 2022"))
                .certifications(List.of("Assistant Instructor Certification"))
                .studentsList(List.of())
                .timeline(List.of(
                    TimelineEntry.builder().year(2012).event("Started training in Kerala").icon("🥋").build(),
                    TimelineEntry.builder().year(2024).event("Applied for federation instructor status").icon("📝").build()))
                .photo(null).bannerUrl(null)
                .status(TeacherStatus.PENDING).featured(false).build(),

            TeacherEntity.builder()
                .firstName("Vikram").lastName("Deshmukh")
                .email("vikram.deshmukh@ashihara.in").phone("91 98765 43221").age(41)
                .belt("Black").rank("Godan").danGrade(5)
                .state("Maharashtra").city("Pune")
                .dojoName("Deshmukh Martial Arts").dojoLocation("Pune, Maharashtra")
                .dojoLat(18.5204).dojoLng(73.8567)
                .yearsTraining(22).students(30).campsHosted(10).seminarsGiven(25)
                .speciality("Sabaki").certifiedBy("International Ashihara Karate Organisation")
                .bio("Senior practitioner from Pune applying to teach under the federation banner.")
                .fullBio("Vikram Deshmukh is a 5th Dan with 22 years of training, applying to lead a federation dojo in Pune with a focus on Sabaki.")
                .achievements(List.of("National Championship Bronze 2015"))
                .certifications(List.of("ISKA Instructor Certification"))
                .studentsList(List.of())
                .timeline(List.of(
                    TimelineEntry.builder().year(2002).event("Began training").icon("🥋").build(),
                    TimelineEntry.builder().year(2024).event("Applied for federation instructor status").icon("📝").build()))
                .photo(null).bannerUrl(null)
                .status(TeacherStatus.PENDING).featured(false).build(),

            TeacherEntity.builder()
                .firstName("Dadi").lastName("Bulsara")
                .email("dadi@ashihara.in").phone("+91 98765 43210").age(52)
                .belt("Black").rank("Hachidan").danGrade(8)
                .state("Maharashtra").city("Mumbai")
                .dojoName("Bulsara Ashihara Karate Dojo").dojoLocation("Mumbai, Maharashtra")
                .dojoLat(19.0760).dojoLng(72.8777)
                .yearsTraining(34).students(48).campsHosted(22).seminarsGiven(65)
                .speciality("Sabaki").certifiedBy("International Ashihara Karate Organisation")
                .bio("Founder of the Indian chapter of Ashihara Karate. Trained directly under Hirokazu Kanazawa and has represented India at the World Ashihara Championships.")
                .fullBio("Dadi Bulsara is the founder and chief instructor of the Indian chapter of Ashihara Karate. With 34 years of dedicated training and teaching, she has been instrumental in popularizing Ashihara Karate across India. She trained directly under the legendary Hirokazu Kanazawa and has represented India at the World Ashihara Championships, earning a silver medal in 1998. Her expertise in Sabaki footwork is renowned across the karate community, and she has mentored countless students who have gone on to become national and international champions.")
                .achievements(List.of("World Championship — Silver 1998", "National Champion 5x", "ISKA Certified Master Instructor"))
                .certifications(List.of("ISKA Master Instructor Certification", "International Referee License", "Youth Development Specialist", "Kata Master Certification", "Kumite Coaching Advanced"))
                .studentsList(List.of("Arjun Sharma (Black Belt, Champion 2023)", "Sneha Patil (Black Belt, Champion 2024)", "Priya Mehta (Brown Belt, National Level)", "Ananya Iyer (Brown Belt, Coach)"))
                .timeline(List.of(
                    TimelineEntry.builder().year(1990).event("Started training under Hirokazu Kanazawa").icon("🥋").build(),
                    TimelineEntry.builder().year(1998).event("World Championship — Silver Medal").icon("🥈").build(),
                    TimelineEntry.builder().year(2005).event("Founded Indian Ashihara Karate Chapter").icon("🏢").build(),
                    TimelineEntry.builder().year(2010).event("Promoted to Hachidan (8th Dan)").icon("⭐").build(),
                    TimelineEntry.builder().year(2015).event("50+ Seminars Hosted Internationally").icon("🌍").build(),
                    TimelineEntry.builder().year(2024).event("48 Active Students, 5 Black Belts").icon("👥").build()))
                .photo(null).bannerUrl(null)
                .status(TeacherStatus.APPROVED).featured(true).build(),

            TeacherEntity.builder()
                .firstName("Raj").lastName("Nair")
                .email("raj@ashihara.in").phone("+91 98765 43211").age(38)
                .belt("Black").rank("Yondan").danGrade(4)
                .state("Kerala").city("Kochi")
                .dojoName("Nair Kumite Academy").dojoLocation("Kochi, Kerala")
                .dojoLat(9.9312).dojoLng(76.2673)
                .yearsTraining(20).students(24).campsHosted(8).seminarsGiven(18)
                .speciality("Kumite").certifiedBy("Dadi Bulsara Ashihara Karate")
                .bio("Head instructor of the Southern India chapter. Specialises in full-contact Kumite and has produced multiple national-level competitors.")
                .fullBio("Raj Nair leads the Southern India chapter of the federation with a focus on full-contact Kumite. A 4th Dan with two decades of training, he has developed several national-level competitors and is known for his rigorous sparring methodology.")
                .achievements(List.of("National Champion 2x", "All India Open — Gold 2017", "Certified ISKA Instructor"))
                .certifications(List.of("ISKA Instructor Certification", "Kumite Coaching Advanced"))
                .studentsList(List.of("Vivek Thomas (Black Belt)", "Meera Nambiar (Brown Belt)"))
                .timeline(List.of(
                    TimelineEntry.builder().year(2004).event("Began teaching in Kochi").icon("🥋").build(),
                    TimelineEntry.builder().year(2017).event("All India Open — Gold").icon("🥇").build(),
                    TimelineEntry.builder().year(2021).event("Promoted to Yondan (4th Dan)").icon("⭐").build()))
                .photo(null).bannerUrl(null)
                .status(TeacherStatus.APPROVED).featured(false).build(),

            TeacherEntity.builder()
                .firstName("Sunita").lastName("Rao")
                .email("sunita@ashihara.in").phone("+91 98765 43212").age(34)
                .belt("Black").rank("Sandan").danGrade(3)
                .state("Karnataka").city("Bangalore")
                .dojoName("Sunita Rao Kata Dojo").dojoLocation("Bangalore, Karnataka")
                .dojoLat(12.9716).dojoLng(77.5946)
                .yearsTraining(16).students(18).campsHosted(5).seminarsGiven(12)
                .speciality("Kata").certifiedBy("Dadi Bulsara Ashihara Karate")
                .bio("One of the first women to earn Sandan in the Indian Ashihara circuit. Leads women's and junior programmes across Bangalore.")
                .fullBio("Sunita Rao is one of the first women to earn Sandan in the Indian Ashihara circuit. She spearheads the women's and junior development programmes across Bangalore, combining technical kata precision with an inclusive teaching philosophy.")
                .achievements(List.of("National Kata Champion 3x", "Youth Development Award 2021"))
                .certifications(List.of("Kata Master Certification", "Youth Development Specialist"))
                .studentsList(List.of("Kavya Reddy (Brown Belt)", "Lakshmi Iyer (Green Belt)"))
                .timeline(List.of(
                    TimelineEntry.builder().year(2008).event("Started training").icon("🥋").build(),
                    TimelineEntry.builder().year(2019).event("National Kata Champion").icon("🥇").build(),
                    TimelineEntry.builder().year(2022).event("Promoted to Sandan (3rd Dan)").icon("⭐").build()))
                .photo(null).bannerUrl(null)
                .status(TeacherStatus.APPROVED).featured(false).build(),

            TeacherEntity.builder()
                .firstName("Arun").lastName("Menon")
                .email("arun@ashihara.in").phone("+91 98765 43213").age(45)
                .belt("Black").rank("Rokudan").danGrade(6)
                .state("Tamil Nadu").city("Chennai")
                .dojoName("Menon Technical Training Center").dojoLocation("Chennai, Tamil Nadu")
                .dojoLat(13.0827).dojoLng(80.2707)
                .yearsTraining(26).students(35).campsHosted(14).seminarsGiven(40)
                .speciality("Sabaki").certifiedBy("International Ashihara Karate Organisation")
                .bio("Senior technical adviser and chief examiner for belt gradings in South India. Known for his precise Sabaki footwork seminars.")
                .fullBio("Arun Menon is the senior technical adviser and chief examiner for belt gradings across South India. A 6th Dan with 26 years of training, his Sabaki footwork seminars are attended by instructors nationwide.")
                .achievements(List.of("Asian Championship — Bronze 2002", "National Champion 4x", "Technical Excellence Award 2019"))
                .certifications(List.of("ISKA Senior Examiner", "Technical Excellence Award 2019"))
                .studentsList(List.of("Karthik Subramaniam (Black Belt)", "Deepa Ramaswamy (Brown Belt)"))
                .timeline(List.of(
                    TimelineEntry.builder().year(1998).event("Began training").icon("🥋").build(),
                    TimelineEntry.builder().year(2002).event("Asian Championship — Bronze").icon("🥉").build(),
                    TimelineEntry.builder().year(2018).event("Promoted to Rokudan (6th Dan)").icon("⭐").build()))
                .photo(null).bannerUrl(null)
                .status(TeacherStatus.APPROVED).featured(false).build()
        ));

        // Provision a linked TEACHER login for each seeded instructor so they
        // can sign in and approve student applications. Stamps user_id.
        for (TeacherEntity t : saved) {
            String email = t.getEmail().toLowerCase().trim();
            if (userRepository.existsByEmailIgnoreCase(email)) {
                UserEntity existing = userRepository.findByEmailIgnoreCase(email).orElseThrow();
                t.setUserId(existing.getId());
                continue;
            }
            UserEntity teacherUser = UserEntity.builder()
                    .email(email)
                    .password(passwordEncoder.encode(SEED_TEACHER_PASSWORD))
                    .firstName(t.getFirstName())
                    .lastName(t.getLastName())
                    .role(Role.TEACHER)
                    .provider(Provider.LOCAL)
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            teacherUser = userRepository.save(teacherUser);
            t.setUserId(teacherUser.getId());
            log.warn("Seeded teacher account email='{}' with dev password.", email);
        }
        teacherRepository.saveAll(saved);

        log.info("Seeded {} sample teachers.", saved.size());
    }
}
