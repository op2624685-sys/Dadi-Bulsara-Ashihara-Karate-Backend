package backend.event;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Seeds the four events that previously lived as the hardcoded {@code EVENTS}
 * array in the frontend ({@code events/EventsPage.tsx}) so the public
 * {@code /events} page keeps working after the API rewire.
 *
 * <p>Idempotent: guarded on {@code eventRepository.count()}. Rows are inserted
 * directly via the repository (not through {@link EventService}) so seeding can
 * run without an authenticated admin session and can set explicit stable slugs.
 *
 * <p>Notes on the mapping:
 * <ul>
 *   <li>The mock's {@code date} strings ("15 August 2026") are parsed with
 *       {@code DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)}.
 *       {@code Locale.ENGLISH} is REQUIRED — under a non-English default locale
 *       the month name "August" would fail to parse.</li>
 *   <li>Status is NOT stored — it is derived from {@code eventDate} at read
 *       time, so no upcoming/past flag is seeded.</li>
 *   <li>Each event gets a single venue {@code state} (the SUB_ADMIN scoping
 *       axis): Patna→Bihar, Gaya→Bihar, Delhi→Delhi, Kolkata→West Bengal.</li>
 *   <li>Participants are seeded only for the two past events (e3, e4).</li>
 * </ul>
 */
@Component
@Profile("!prod")
@Order(3)
@RequiredArgsConstructor
public class EventDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EventDataInitializer.class);

    /** Locale.ENGLISH is mandatory so month names ("August") parse regardless
     *  of the server's default locale. */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final EventRepository eventRepository;

    @Override
    public void run(String... args) {
        if (eventRepository.count() > 0) {
            log.info("Events already present ({}), skipping seed.", eventRepository.count());
            return;
        }

        log.info("Seeding historical events…");

        EventEntity e1 = EventEntity.builder()
                .slug("national-ashihara-championship-2026")
                .title("National Ashihara Championship 2026")
                .eventDate(LocalDate.parse("15 August 2026", DATE_FMT))
                .location("Patliputra Sports Complex, Patna, Bihar")
                .organizer("Dadi Bulsara HQ India")
                .type(EventType.CHAMPIONSHIP)
                .prizePool("₹1,50,000")
                .highlight("India's largest full-contact Ashihara event of the year")
                .state("Bihar") // venue: Patna, Bihar
                .participants(List.of())
                .published(true)
                .createdById(null)
                .build();

        EventEntity e2 = EventEntity.builder()
                .slug("state-level-kumite-tournament-2026")
                .title("State Level Kumite Tournament")
                .eventDate(LocalDate.parse("10 September 2026", DATE_FMT))
                .location("Indoor Stadium, Gaya, Bihar")
                .organizer("Sensei Rajesh Kumar")
                .type(EventType.TOURNAMENT)
                .prizePool("₹50,000")
                .highlight("Open to all belt grades across Bihar state")
                .state("Bihar") // venue: Gaya, Bihar
                .participants(List.of())
                .published(true)
                .createdById(null)
                .build();

        EventEntity e3 = EventEntity.builder()
                .slug("all-india-full-contact-karate-championship-2025")
                .title("All India Full Contact Karate Championship 2025")
                .eventDate(LocalDate.parse("20 December 2025", DATE_FMT))
                .location("Talkatora Stadium, New Delhi")
                .organizer("AIKF & Dadi Bulsara")
                .type(EventType.CHAMPIONSHIP)
                .prizePool("₹2,00,000")
                .highlight("500+ competitors from 18 states across India")
                .state("Delhi") // venue: New Delhi
                .participants(List.of(
                        Participant.builder().id("p1").name("Rahul Singh").role("Student").state("Bihar").belt("Black Belt (1st Dan)").placement("1st").build(),
                        Participant.builder().id("p2").name("Amit Sharma").role("Student").state("Delhi").belt("Brown Belt").placement("2nd").build(),
                        Participant.builder().id("p3").name("Vikram Das").role("Teacher").state("West Bengal").belt("Black Belt (3rd Dan)").placement("3rd").build(),
                        Participant.builder().id("p4").name("Neha Gupta").role("Student").state("UP").belt("Green Belt").placement(null).build(),
                        Participant.builder().id("p5").name("Suresh Patil").role("Student").state("Maharashtra").belt("Blue Belt").placement(null).build()))
                .published(true)
                .createdById(null)
                .build();

        EventEntity e4 = EventEntity.builder()
                .slug("eastern-region-winter-tournament-2025")
                .title("Eastern Region Winter Tournament")
                .eventDate(LocalDate.parse("05 November 2025", DATE_FMT))
                .location("Kolkata, West Bengal")
                .organizer("Eastern Ashihara Council")
                .type(EventType.TOURNAMENT)
                .prizePool("₹30,000")
                .highlight("Regional qualifier for national championship")
                .state("West Bengal") // venue: Kolkata, West Bengal
                .participants(List.of(
                        Participant.builder().id("p6").name("Anjali Roy").role("Student").state("West Bengal").belt("Brown Belt").placement("1st").build(),
                        Participant.builder().id("p7").name("Ravi Teja").role("Teacher").state("Odisha").belt("Black Belt (2nd Dan)").placement("2nd").build(),
                        Participant.builder().id("p8").name("Manish Kumar").role("Student").state("Bihar").belt("Yellow Belt").placement(null).build()))
                .published(true)
                .createdById(null)
                .build();

        eventRepository.saveAll(List.of(e1, e2, e3, e4));
        log.info("Seeded 4 historical events.");
    }
}
