package backend.event;

import backend.event.dto.EventCreateRequest;
import backend.event.dto.EventResponse;
import backend.event.dto.EventUpdateRequest;
import backend.teacher.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Business operations for events. Mutations are open to ADMIN and SUB_ADMIN
 * (gated by SecurityConfig), but a SUB_ADMIN is scoped to their managed state:
 * they can only see/create/edit/delete events in that state, enforced here.
 */
public interface EventService {

    // ── Public ────────────────────────────────────────────────────────────
    /** Published events, newest event date first, with derived status. */
    List<EventResponse> listPublished();

    // ── Admin ─────────────────────────────────────────────────────────────
    /** All events (drafts included), scoped to a sub-admin's state, paginated. */
    PageResponse<EventResponse> listForAdmin(String search, String state, Pageable pageable);

    /** Single event by id (drafts included); scope-checked for sub-admins. */
    EventResponse getForAdmin(Long id);

    /** Create an event as a hidden DRAFT; state force-set for sub-admins. */
    EventResponse create(EventCreateRequest req);

    /** Partial update; scope-checked; slug kept stable. */
    EventResponse update(Long id, EventUpdateRequest req);

    /** Delete an event; scope-checked. */
    void delete(Long id);

    /** Make the event publicly visible; scope-checked. */
    EventResponse publish(Long id);

    /** Hide the event from the public pages again; scope-checked. */
    EventResponse unpublish(Long id);
}
