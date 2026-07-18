package backend.camp;

import backend.camp.dto.CampCreateRequest;
import backend.camp.dto.CampResponse;
import backend.camp.dto.CampSummaryResponse;
import backend.camp.dto.CampUpdateRequest;
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
 * Admin-only camp management. The route is gated by
 * {@code /api/v1/admin/camps/** -> hasRole("ADMIN")} in SecurityConfig, which is
 * declared BEFORE the broad {@code /api/v1/admin/**} rule so a SUB_ADMIN hitting
 * these endpoints gets a 403 (camps are admin-only; sub-admins manage events).
 *
 * <p>Exposes the full CRUD + publish/unpublish lifecycle. Create returns a
 * hidden DRAFT (201); the camp is announced later via {@code PATCH /{id}/publish}.
 */
@RestController
@RequestMapping("/api/v1/admin/camps")
@RequiredArgsConstructor
public class AdminCampController {

    private final CampService campService;

    private static final Set<String> SORTABLE = Set.of(
            "id", "name", "state", "year", "status", "published", "createdAt");

    @GetMapping
    public ResponseEntity<PageResponse<CampSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        return ResponseEntity.ok(campService.listForAdmin(blankToNull(search), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(campService.getForAdmin(id));
    }

    @PostMapping
    public ResponseEntity<CampResponse> create(@Valid @RequestBody CampCreateRequest request) {
        CampResponse created = campService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampResponse> update(
            @PathVariable Long id, @Valid @RequestBody CampUpdateRequest request) {
        return ResponseEntity.ok(campService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        campService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<CampResponse> publish(@PathVariable Long id) {
        return ResponseEntity.ok(campService.publish(id));
    }

    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<CampResponse> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(campService.unpublish(id));
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
            sortObj = Sort.by(Sort.Direction.DESC, "year");
        }
        sortObj = sortObj.and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(safePage, safeSize, sortObj);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
