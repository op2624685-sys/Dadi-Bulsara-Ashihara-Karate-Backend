package backend.teacher;

import backend.teacher.dto.*;
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
 * Admin-only teacher management. Route is gated by
 * {@code /api/v1/admin/** -> hasRole("ADMIN")} in SecurityConfig. These
 * endpoints expose every teacher (including PENDING/REJECTED) and the
 * approve / reject / delete moderation actions that drive the registration
 * approval flow.
 */
@RestController
@RequestMapping("/api/v1/admin/teachers")
@RequiredArgsConstructor
public class AdminTeacherController {

    private final TeacherService teacherService;

    private static final Set<String> SORTABLE = Set.of(
            "id", "firstName", "lastName", "state", "danGrade",
            "yearsTraining", "status", "createdAt");

    @GetMapping
    public ResponseEntity<PageResponse<TeacherAdminSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TeacherStatus status,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        PageResponse<TeacherAdminSummaryResponse> body =
                teacherService.listForAdmin(blankToNull(search), status, blankToNull(state), pageable);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getForAdmin(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<TeacherResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<TeacherResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TeacherReviewRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(teacherService.reject(id, reason));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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
            sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        sortObj = sortObj.and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(safePage, safeSize, sortObj);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
