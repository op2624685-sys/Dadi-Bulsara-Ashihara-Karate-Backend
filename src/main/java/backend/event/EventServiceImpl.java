package backend.event;

import backend.event.dto.EventCreateRequest;
import backend.event.dto.EventResponse;
import backend.event.dto.EventUpdateRequest;
import backend.event.exception.EventNotFoundException;
import backend.security.SecurityService;
import backend.teacher.dto.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Default {@link EventService}. Events are manageable by ADMIN and SUB_ADMIN,
 * but a SUB_ADMIN is bound to their managed state. The scoping helpers
 * ({@link #assertWithinScope} + {@link #effectiveState}) are copied from
 * {@link backend.teacher.TeacherServiceImpl}, retyped for {@link EventEntity}.
 *
 * <p>Key invariants:
 * <ul>
 *   <li>Create forces {@code state = effectiveState(req.state())} — a sub-admin's
 *       managed state always wins, so they can never plant an event elsewhere.</li>
 *   <li>Create produces a hidden DRAFT with a generated, unique slug.</li>
 *   <li>Update/delete/publish/unpublish load → {@code assertWithinScope} → mutate.</li>
 *   <li>Status is DERIVED from {@code eventDate} at read time (never stored).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EventServiceImpl implements EventService {

    private static final Logger log = LoggerFactory.getLogger(EventServiceImpl.class);

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");

    private final EventRepository eventRepository;
    private final SecurityService securityService;

    // ─────────────────────────────────────────────────────────────────────
    // Sub-admin scoping (copied from TeacherServiceImpl, retyped for EventEntity)
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Ensures a SUB_ADMIN can only touch events in their managed state. ADMIN
     * passes through untouched. Throws 403 (AccessDenied → handled by
     * AccessDeniedHandlerImpl) when a sub-admin reaches for another state.
     */
    private void assertWithinScope(EventEntity event) {
        SecurityService.AdminScope scope = securityService.scope();
        if (scope.isSubAdmin()
                && (event.getState() == null
                    || !event.getState().equalsIgnoreCase(scope.state()))) {
            throw new AccessDeniedException(
                    "Sub-admins may only manage events in state: " + scope.state());
        }
    }

    /** Returns the effective state filter: a sub-admin's state wins; admin's
     *  explicit param (or null) is honoured. */
    private String effectiveState(String requestedState) {
        SecurityService.AdminScope scope = securityService.scope();
        return scope.isSubAdmin() ? scope.state() : requestedState;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public reads
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> listPublished() {
        LocalDate today = LocalDate.now();
        List<EventResponse> body = eventRepository.findByPublishedTrueOrderByEventDateDesc()
                .stream()
                .map(e -> EventResponse.of(e, deriveStatus(e, today)))
                .toList();
        log.debug("Public event list served: {} published events", body.size());
        return body;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Admin reads
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listForAdmin(String search, String requestedState, Pageable pageable) {
        // Sub-admins are locked to their own state; admins see all (or a filter).
        final String state = effectiveState(requestedState);
        final LocalDate today = LocalDate.now();

        Specification<EventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (isNotBlank(state)) {
                predicates.add(cb.equal(cb.lower(root.get("state")), state.toLowerCase()));
            }
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("title"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("location"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("organizer"), "")), like)));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<EventEntity> page = eventRepository.findAll(spec, pageable);
        log.debug("Admin event list page={} size={} total={} scopedState={}",
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements(), state);
        return PageResponse.of(page, e -> EventResponse.of(e, deriveStatus(e, today)));
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getForAdmin(Long id) {
        EventEntity event = findOrThrow(id);
        assertWithinScope(event);
        return EventResponse.of(event, deriveStatus(event, LocalDate.now()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Admin mutations
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public EventResponse create(EventCreateRequest req) {
        Long creatorId = securityService.requireCurrentUser().getId();
        // Force the state to the caller's scope: a sub-admin's managed state
        // overrides whatever the client sent, so they can only plant events in
        // their own state.
        String state = effectiveState(req.state());

        EventEntity event = EventEntity.builder()
                .slug(generateUniqueSlug(req.title(), req.eventDate()))
                .title(req.title().trim())
                .eventDate(req.eventDate())
                .location(req.location())
                .organizer(req.organizer())
                .type(req.type())
                .prizePool(req.prizePool())
                .duration(req.duration())
                .totalParticipants(req.totalParticipants())
                .highlight(req.highlight())
                .state(state)
                .participants(nullToEmpty(req.participants()))
                // A new event always starts hidden — announced later via publish.
                .published(false)
                .createdById(creatorId)
                .build();

        event = eventRepository.save(event);
        log.info("Event created (DRAFT): id={} slug={} title='{}' state={} by userId={}",
                event.getId(), event.getSlug(), event.getTitle(), event.getState(), creatorId);
        return EventResponse.of(event, deriveStatus(event, LocalDate.now()));
    }

    @Override
    public EventResponse update(Long id, EventUpdateRequest req) {
        EventEntity event = findOrThrow(id);
        assertWithinScope(event); // may this caller touch this event's state?

        // PATCH semantics: only non-null fields applied. Slug stays stable.
        if (req.title() != null)             event.setTitle(req.title().trim());
        if (req.eventDate() != null)         event.setEventDate(req.eventDate());
        if (req.location() != null)          event.setLocation(req.location());
        if (req.organizer() != null)         event.setOrganizer(req.organizer());
        if (req.type() != null)              event.setType(req.type());
        if (req.prizePool() != null)         event.setPrizePool(req.prizePool());
        if (req.duration() != null)          event.setDuration(req.duration());
        if (req.totalParticipants() != null) event.setTotalParticipants(req.totalParticipants());
        if (req.highlight() != null)         event.setHighlight(req.highlight());
        if (req.participants() != null)      event.setParticipants(req.participants());
        if (req.state() != null) {
            // Coerce to the caller's scope so a sub-admin can never move an
            // event into another state; then re-check the (possibly new) state.
            event.setState(effectiveState(req.state()));
            assertWithinScope(event);
        }

        event = eventRepository.save(event);
        log.info("Event updated: id={} slug={} state={}", event.getId(), event.getSlug(), event.getState());
        return EventResponse.of(event, deriveStatus(event, LocalDate.now()));
    }

    @Override
    public void delete(Long id) {
        EventEntity event = findOrThrow(id);
        assertWithinScope(event);
        eventRepository.delete(event);
        log.info("Event deleted: id={} slug={}", id, event.getSlug());
    }

    @Override
    public EventResponse publish(Long id) {
        EventEntity event = findOrThrow(id);
        assertWithinScope(event);
        event.setPublished(true);
        event = eventRepository.save(event);
        log.info("Event published: id={} slug={}", event.getId(), event.getSlug());
        return EventResponse.of(event, deriveStatus(event, LocalDate.now()));
    }

    @Override
    public EventResponse unpublish(Long id) {
        EventEntity event = findOrThrow(id);
        assertWithinScope(event);
        event.setPublished(false);
        event = eventRepository.save(event);
        log.info("Event unpublished: id={} slug={}", event.getId(), event.getSlug());
        return EventResponse.of(event, deriveStatus(event, LocalDate.now()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private EventEntity findOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("No event found with id: " + id));
    }

    /**
     * Derives the UPCOMING/PAST status from the event date. An event is
     * UPCOMING when its date is today or in the future; PAST once the date has
     * passed. {@code today} is passed in so a whole list is derived against one
     * consistent "now".
     */
    static EventStatus deriveStatus(EventEntity event, LocalDate today) {
        return !event.getEventDate().isBefore(today) ? EventStatus.UPCOMING : EventStatus.PAST;
    }

    /**
     * Generates a URL-safe, unique slug from the title + year. Appends a numeric
     * suffix on collision. Runs only on create; the slug is stable thereafter.
     */
    private String generateUniqueSlug(String title, LocalDate date) {
        String base = slugify(date != null ? title + " " + date.getYear() : title);
        if (base.isBlank()) {
            base = "event";
        }
        String candidate = base;
        int suffix = 2;
        while (eventRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /** Lowercases, strips accents, and replaces non-alphanumeric runs with dashes. */
    static String slugify(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String lower = normalized.toLowerCase(Locale.ENGLISH);
        String dashed = NON_ALNUM.matcher(lower).replaceAll("-");
        return EDGE_DASHES.matcher(dashed).replaceAll("");
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
