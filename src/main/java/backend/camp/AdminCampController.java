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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Admin-only camp management. The route is gated by
 * {@code /api/v1/admin/camps/** -> hasRole("ADMIN")} in SecurityConfig, which is
 * declared BEFORE the broad {@code /api/v1/admin/**} rule so a SUB_ADMIN hitting
 * these endpoints gets a 403 (camps are admin-only; sub-admins manage events).
 *
 * <p>Two create/update paths:
 * <ul>
 *   <li>{@code POST /admin/camps} + {@code PUT /admin/camps/{id}} — JSON
 *       bodies for programmatic updates and the "no image change" re-edit.</li>
 *   <li>{@code POST /admin/camps/new} + {@code PUT /admin/camps/{id}/new} —
 *       multipart bodies with a {@code payload} JSON part plus optional
 *       image parts. The server uploads the images and saves the row in
 *       one shot from the form's perspective.</li>
 * </ul>
 * </p>
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

    /** Multipart create — uploads the images and saves the row in one call. */
    @PostMapping(value = "/new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampResponse> createWithImages(
            @RequestPart("payload") @Valid CampCreateRequest payload,
            @RequestPart(value = "heroImage", required = false) MultipartFile heroImage,
            @RequestPart(value = "aboutImage0", required = false) MultipartFile aboutImage0,
            @RequestPart(value = "aboutImage1", required = false) MultipartFile aboutImage1,
            @RequestPart(value = "galleryImages", required = false) List<MultipartFile> galleryImages,
            @RequestPart(value = "instructorImages", required = false) List<MultipartFile> instructorImages) {
        CampResponse created = campService.createWithImages(
                payload, heroImage, aboutImage0, aboutImage1, galleryImages, instructorImages);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampResponse> update(
            @PathVariable Long id, @Valid @RequestBody CampUpdateRequest request) {
        return ResponseEntity.ok(campService.update(id, request));
    }

    /** Multipart update — same shape as create. */
    @PutMapping(value = "/{id}/new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampResponse> updateWithImages(
            @PathVariable Long id,
            @RequestPart("payload") @Valid CampUpdateRequest payload,
            @RequestPart(value = "heroImage", required = false) MultipartFile heroImage,
            @RequestPart(value = "aboutImage0", required = false) MultipartFile aboutImage0,
            @RequestPart(value = "aboutImage1", required = false) MultipartFile aboutImage1,
            @RequestPart(value = "galleryImages", required = false) List<MultipartFile> galleryImages,
            @RequestPart(value = "instructorImages", required = false) List<MultipartFile> instructorImages) {
        return ResponseEntity.ok(campService.updateWithImages(
                id, payload, heroImage, aboutImage0, aboutImage1, galleryImages, instructorImages));
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
