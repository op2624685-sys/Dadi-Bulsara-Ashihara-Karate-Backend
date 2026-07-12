package backend.student;

import backend.student.dto.*;
import backend.teacher.dto.PageResponse;
import backend.teacher.dto.TeacherReviewRequest;
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
 * Admin student management (mirrors the teacher-approval console). Routed by
 * {@code /api/v1/admin/** -> hasRole("ADMIN") | hasRole("SUB_ADMIN")} in
 * SecurityConfig. Exposes every student (including PENDING/REJECTED) plus the
 * approve / reject / delete moderation actions.
 */
@RestController
@RequestMapping("/api/v1/admin/students")
@RequiredArgsConstructor
public class AdminStudentController {

    private final StudentService studentService;

    private static final Set<String> SORTABLE = Set.of(
            "id", "firstName", "lastName", "state", "belt", "age", "status", "createdAt");

    @GetMapping
    public ResponseEntity<PageResponse<StudentAdminSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        PageResponse<StudentAdminSummaryResponse> body =
                studentService.listForAdmin(blankToNull(search), status, pageable);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getForAdmin(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<StudentResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<StudentResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TeacherReviewRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(studentService.reject(id, reason));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

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
