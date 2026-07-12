package backend.student;

import backend.security.SecurityService;
import backend.student.dto.*;
import backend.teacher.TeacherEntity;
import backend.teacher.TeacherRepository;
import backend.teacher.dto.PageResponse;
import backend.teacher.dto.TeacherReviewRequest;
import backend.user.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Sensei-facing student approval. A logged-in teacher sees only the
 * applications routed to them ({@code sensei_id == their directory id}) and can
 * approve / reject them. On approval the applicant is promoted to STUDENT by
 * the service layer.
 *
 * <p>Routed by {@code /api/v1/teacher/** -> hasRole("TEACHER")} in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/teacher/students")
@RequiredArgsConstructor
public class TeacherStudentController {

    private final StudentService studentService;
    private final TeacherRepository teacherRepository;
    private final SecurityService securityService;

    private static final Set<String> SORTABLE = Set.of(
            "id", "firstName", "lastName", "state", "belt", "age", "status", "createdAt");

    @GetMapping
    public ResponseEntity<PageResponse<StudentAdminSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        long senseiId = currentSenseiId();
        Pageable pageable = buildPageable(page, size, sort, dir);
        return ResponseEntity.ok(studentService.listForTeacher(senseiId, blankToNull(search), status, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<StudentResponse> approve(@PathVariable Long id) {
        long senseiId = currentSenseiId();
        return ResponseEntity.ok(studentService.approveAsTeacher(id, senseiId));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<StudentResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TeacherReviewRequest request) {
        long senseiId = currentSenseiId();
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(studentService.rejectAsTeacher(id, senseiId, reason));
    }

    // ── Resolve the logged-in teacher's directory id ───────────────────────────
    private long currentSenseiId() {
        UserEntity user = securityService.requireCurrentUser();
        TeacherEntity teacher = teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Your account is not linked to a teacher profile"));
        return teacher.getId();
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
