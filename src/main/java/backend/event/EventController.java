package backend.event;

import backend.event.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, read-only event endpoints (permitAll via SecurityConfig GET rule).
 * Only published events are exposed; each carries its derived UPCOMING/PAST
 * status and (for past events) the participants roster.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /** Published events, newest event date first, with derived status. */
    @GetMapping
    public ResponseEntity<List<EventResponse>> list() {
        return ResponseEntity.ok(eventService.listPublished());
    }
}
