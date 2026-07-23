package backend.camp;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the two historical camps that previously lived as static mock data in
 * the frontend ({@code src/data/camps.ts}) so the public {@code /camps} pages
 * keep working after the API rewire.
 *
 * <p>Idempotent: guarded on {@code campRepository.count()}, so it only runs on a
 * fresh database. The rows are inserted <b>directly</b> via the repository (not
 * through {@link CampService}) so the exact legacy slugs can be preserved —
 * bypassing slug generation keeps the public URLs
 * ({@code /camps/sis-gto-training-camp-2011}, {@code /camps/sabaki-challenge-camp-2013})
 * valid. Both are seeded {@code published = true}, {@code status = PAST},
 * {@code createdById = null} (no admin owns a seeded row). Image paths are kept
 * as the frontend's {@code /img/NN.jpg} static assets.
 */
@Component
@Profile("!prod")
@Order(2)
@RequiredArgsConstructor
public class CampDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CampDataInitializer.class);

    private final CampRepository campRepository;

    @Override
    public void run(String... args) {
        if (campRepository.count() > 0) {
            log.info("Camps already present ({}), skipping seed.", campRepository.count());
            return;
        }

        log.info("Seeding historical camps…");

        CampEntity sisGto = CampEntity.builder()
                .slug("sis-gto-training-camp-2011") // exact legacy frontend slug
                .name("SIS GTO")
                .subtitle("Training Camp")
                .location("Mahabaleshwar")
                .state("Maharashtra")
                .year(2011)
                .duration("5 Days")
                .participants(75)
                .sessions(14)
                .instructorCount(4)
                .kana("武道合宿 · Budo Gasshuku")
                .heroImage("/img/01.jpg")
                .aboutImages(List.of("/img/02.jpg", "/img/03.jpg"))
                .quote("A gasshuku is not a retreat from ordinary life — it is a concentrated collision with it.")
                .quoteAuthor("Sensei R. Desai, Camp Director")
                .description("The 2011 SIS GTO Training Camp in Mahabaleshwar marked a defining chapter for our federation. Held across five relentless days in the misty highlands of Maharashtra, this gasshuku brought together 75 practitioners from across India under the unified pursuit of technical mastery. International instructors attended for the first time — a milestone that elevated the camp's standard and gave Indian practitioners direct access to Japanese classical lineage.")
                .pillars(List.of(
                        TrainingPillar.builder().id("01").method("Method 01").title("Kata Refinement").description("Deep-form analysis of Heian through Bassai sequences. Each movement broken down individually — kime, transition speed, and bunkai application in live context.").hoursPerDay("4 hrs / day").icon("kata").build(),
                        TrainingPillar.builder().id("02").method("Method 02").title("Kumite Pressure").description("Controlled sparring cycles designed to test combative composure under fatigue. Progressive intensity — technical patterns to full resistance by day four.").hoursPerDay("3 hrs / day").icon("kumite").build(),
                        TrainingPillar.builder().id("03").method("Method 03").title("Physical Conditioning").description("Camp-specific circuits targeting hip mobility, core stability and explosive power chains central to karate movement. 6AM every morning. No exceptions.").hoursPerDay("1.5 hrs / day").icon("conditioning").build(),
                        TrainingPillar.builder().id("04").method("Method 04").title("Bunkai & Application").description("Senior practitioners guided groups through practical defensive interpretations of kata sequences in structured partner drills.").hoursPerDay("2 hrs / day").icon("bunkai").build(),
                        TrainingPillar.builder().id("05").method("Method 05").title("Grading Preparation").description("Candidates preparing for kyu or dan examinations received focused correction and mock assessment from visiting instructors each evening.").hoursPerDay("Evening sessions").icon("grading").build(),
                        TrainingPillar.builder().id("06").method("Method 06").title("Dojo Culture & Philosophy").description("Nightly seminars on Budo philosophy, the origin of the gasshuku tradition in Okinawan karate, and federation lineage.").hoursPerDay("Nightly").icon("philosophy").build()))
                .instructors(List.of(
                        Instructor.builder().id("1").name("Sensei K. Tanaka").grade("6th Dan").role("Head Instructor").origin("Osaka, Japan").image("/img/04.jpg").build(),
                        Instructor.builder().id("2").name("Sensei R. Desai").grade("5th Dan").role("Camp Director").origin("Pune, Maharashtra").image("/img/05.jpg").build(),
                        Instructor.builder().id("3").name("Sensei V. Mehta").grade("4th Dan").origin("New Delhi").image("/img/06.jpg").build(),
                        Instructor.builder().id("4").name("Sensei P. Sharma").grade("4th Dan").role("Youth Coach").origin("Mumbai, Maharashtra").image("/img/07.jpg").build()))
                .schedule(List.of(
                        ScheduleDay.builder().dayNum("01").dayLabel("Monday · Arrival").title("Arrival & Opening Ceremony").description("Participants arrived through the afternoon. Opening ceremony at dusk — formal introductions, recitation of the Dojo Kun, and a ceremonial first warm-up as one group.").tags(List.of("Orientation", "Dojo Kun", "Evening Ceremony")).build(),
                        ScheduleDay.builder().dayNum("02").dayLabel("Tuesday · Core").title("Technical Foundations Deep Dive").description("Conditioning at 6AM. Four hours of kata deconstruction led by Sensei Tanaka. Afternoon kumite patterns and bunkai partner work with rotations across all instructors.").tags(List.of("Kata", "Bunkai", "Kumite", "Conditioning")).build(),
                        ScheduleDay.builder().dayNum("03").dayLabel("Wednesday · Core").title("Advanced Kata & Partner Work").description("Deeper focus on advanced kata sequences. Afternoon dedicated to structured kumite with resistance partners. First formal grading mock sessions in the evening.").tags(List.of("Advanced Kata", "Resistance Kumite", "Mock Grading")).build(),
                        ScheduleDay.builder().dayNum("04").dayLabel("Thursday · Peak").title("Pressure Testing & Full Resistance").description("The hardest day by design. Extended kumite with full gear. Grading candidates underwent formal evaluations. Evening seminar on gasshuku history by Sensei Desai.").tags(List.of("Full Kumite", "Formal Grading", "Budo Seminar")).build(),
                        ScheduleDay.builder().dayNum("05").dayLabel("Friday · Close").title("Final Session & Closing Ceremony").description("Joint kata performance by all 75 participants. Certificates presented. Closing remarks from Sensei Tanaka. Formal close with Dojo Kun recitation and group photo at sunset.").tags(List.of("Joint Kata", "Certificates", "Closing Ceremony")).build()))
                .galleryImages(List.of("/img/08.jpg", "/img/09.jpg", "/img/10.jpg", "/img/02.jpg", "/img/03.jpg"))
                .status(CampStatus.PAST)
                .published(true)
                .createdById(null)
                .build();

        CampEntity sabaki = CampEntity.builder()
                .slug("sabaki-challenge-camp-2013") // exact legacy frontend slug
                .name("Sabaki Challenge")
                .subtitle("Camp")
                .location("Alibaug Beach")
                .state("Maharashtra")
                .year(2013)
                .duration("3 Days")
                .participants(95)
                .sessions(9)
                .instructorCount(3)
                .kana("武道合宿 · Budo Gasshuku")
                .heroImage("/img/06.jpg")
                .aboutImages(List.of("/img/07.jpg", "/img/08.jpg"))
                .quote("On the beach, there is nowhere to hide — from the ocean, or from yourself.")
                .quoteAuthor("Sensei K. Tanaka, Head Instructor")
                .description("The 2013 Sabaki Challenge Camp at Alibaug Beach was unlike any before it. Training on sand and in surf, 95 practitioners pushed the limits of conditioning and kata precision in an environment that demanded constant adaptation. Beach sabaki training over three intensive days.")
                .pillars(List.of(
                        TrainingPillar.builder().id("01").method("Method 01").title("Beach Sabaki").description("Sabaki movement patterns trained on unstable sand surface — demanding superior balance, hip rotation, and continuous footwork adjustment.").hoursPerDay("4 hrs / day").icon("kata").build(),
                        TrainingPillar.builder().id("02").method("Method 02").title("Ocean Conditioning").description("Resistance training in surf and open water — developing explosive power and cardiovascular endurance under natural resistance.").hoursPerDay("2 hrs / day").icon("conditioning").build(),
                        TrainingPillar.builder().id("03").method("Method 03").title("Kumite Adaptation").description("Sparring on sand forces competitors to develop new footwork and stance patterns, translating directly to tournament performance.").hoursPerDay("3 hrs / day").icon("kumite").build()))
                .instructors(List.of(
                        Instructor.builder().id("1").name("Sensei K. Tanaka").grade("6th Dan").role("Head Instructor").origin("Osaka, Japan").image("/img/04.jpg").build(),
                        Instructor.builder().id("2").name("Sensei R. Desai").grade("5th Dan").origin("Pune, Maharashtra").image("/img/05.jpg").build(),
                        Instructor.builder().id("3").name("Sensei V. Mehta").grade("4th Dan").origin("New Delhi").image("/img/06.jpg").build()))
                .schedule(List.of(
                        ScheduleDay.builder().dayNum("01").dayLabel("Friday · Arrival").title("Arrival & Beach Opening").description("Evening arrival at Alibaug. Opening ceremony conducted on the beach at dusk with waves as backdrop. First conditioning session in the surf.").tags(List.of("Arrival", "Beach Ceremony", "Evening")).build(),
                        ScheduleDay.builder().dayNum("02").dayLabel("Saturday · Peak").title("Full Day Beach Training").description("Sunrise to sunset training — morning sabaki on sand, afternoon ocean resistance work, evening kumite under floodlights.").tags(List.of("Sabaki", "Ocean Work", "Night Kumite")).build(),
                        ScheduleDay.builder().dayNum("03").dayLabel("Sunday · Close").title("Final Session & Ceremony").description("Morning final kata on the beach. Group photograph at the shore. Closing ceremony and certificates.").tags(List.of("Final Kata", "Certificates", "Closing")).build()))
                .galleryImages(List.of("/img/06.jpg", "/img/07.jpg", "/img/08.jpg", "/img/09.jpg", "/img/10.jpg"))
                .status(CampStatus.PAST)
                .published(true)
                .createdById(null)
                .build();

        campRepository.saveAll(List.of(sisGto, sabaki));
        log.info("Seeded 2 historical camps (slugs: {}, {}).", sisGto.getSlug(), sabaki.getSlug());
    }
}
