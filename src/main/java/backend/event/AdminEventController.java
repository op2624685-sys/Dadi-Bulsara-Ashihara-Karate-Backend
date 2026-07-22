package backend.event;

import backend.event.dto.EventCreateRequest;
import backend.event.dto.EventResponse;
import backend.event.dto.EventUpdateRequest;
import backend.teacher.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Admin event management, open to ADMIN and SUB_ADMIN (gated by
 * {@code /api/v1/admin/events/** -> hasAnyRole("ADMIN","SUB_ADMIN")} in
 * SecurityConfig). A SUB_ADMIN is further scoped to their managed state inside
 * the service, so this controller stays role-agnostic.
 *
 * <p>Exposes the full CRUD + publish/unpublish lifecycle. Create returns a
 * hidden DRAFT (201).
 */
@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final EventService eventService;

    private static final Set<String> SORTABLE = Set.of(
            "id", "title", "state", "type", "eventDate", "published", "createdAt");

    @GetMapping
    public ResponseEntity<PageResponse<EventResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        return ResponseEntity.ok(
                eventService.listForAdmin(blankToNull(search), blankToNull(state), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getForAdmin(id));
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventCreateRequest request) {
        EventResponse created = eventService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> update(
            @PathVariable Long id, @Valid @RequestBody EventUpdateRequest request) {
        return ResponseEntity.ok(eventService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<EventResponse> publish(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.publish(id));
    }

    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<EventResponse> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.unpublish(id));
    }

    // ---------------------------------------------------------------------
    private Pageable buildPageable(int page, int size, String sort, String dir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Sort sortObj;
        if (sort != null && SORTABLE.contains(sort)) {
            Sort.Direction direction = "asc".equalsIgnoreCase(dir)
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortObj = Sort.by(direction, sort);
        } else {
            sortObj = Sort.by(Sort.Direction.DESC, "eventDate");
        }
        sortObj = sortObj.and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(safePage, safeSize, sortObj);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
