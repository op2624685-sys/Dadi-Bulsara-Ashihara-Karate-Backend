package backend.student;

import backend.security.SecurityService;
import backend.student.dto.*;
import backend.student.dto.StudentPublicResponse;
import backend.teacher.dto.PageResponse;
import backend.user.UserEntity;
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
 * Public student directory + self-service application.
 *
 * <p>GET endpoints are public (see SecurityConfig). POST /register and GET /me
 * require an authenticated session — registration creates an application tied
 * to the caller's account, and /me returns the caller's own application.
 */
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final SecurityService securityService;

    private static final Set<String> SORTABLE = Set.of(
            "age", "firstName", "lastName", "campsCount", "eventsCount", "createdAt");

    @GetMapping
    public ResponseEntity<PageResponse<StudentSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String belt,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        PageResponse<StudentSummaryResponse> body = studentService.listStudents(
                blankToNull(search), blankToNull(state), blankToNull(belt), pageable);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getStudent(id));
    }

    /**
     * Public, sanitized single-student profile. Returns only non-personal
     * fields plus the sensei's dojo — safe for anonymous visitors. This is the
     * endpoint the public profile page calls; the full {@code GET /{id}} leaks
     * identity data and must not be used publicly.
     */
    @GetMapping("/{id}/public")
    public ResponseEntity<StudentPublicResponse> getPublic(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getPublicStudent(id));
    }

    /**
     * The caller's own application (any status). 404 if they have none. Lets the
     * profile page show "pending approval" vs the full directory record.
     */
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> getMine() {
        UserEntity user = securityService.requireCurrentUser();
        return ResponseEntity.ok(studentService.getMine(user));
    }

    /**
     * Update the caller's own application (PATCH-style — null fields are left
     * unchanged). Lets the profile page edit contact / dossier / location.
     */
    @PutMapping("/me")
    public ResponseEntity<StudentResponse> updateMine(
            @Valid @RequestBody StudentUpdateRequest req) {
        UserEntity user = securityService.requireCurrentUser();
        return ResponseEntity.ok(studentService.updateMine(user, req));
    }

    /**
     * 
     * Apply for student membership. Authenticated users only — the application
     * is linked to the caller and routed to the chosen sensei for approval.
     */
    @PostMapping("/register")
    public ResponseEntity<StudentResponse> register(
            @Valid @RequestBody StudentRegistrationRequest request) {
        UserEntity user = securityService.requireCurrentUser();
        StudentResponse created = studentService.register(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
