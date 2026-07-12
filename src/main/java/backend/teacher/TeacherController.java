package backend.teacher;

import backend.security.SecurityService;
import backend.teacher.dto.*;
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

@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final SecurityService securityService;

    /** Whitelist of sortable fields (must mirror TeacherServiceImpl.SORTABLE). */
    private static final Set<String> SORTABLE = Set.of(
            "danGrade", "yearsTraining", "students", "createdAt",
            "firstName", "lastName", "featured");

    /**
     * Public, paginated, filterable, searchable directory.
     *
     * <p>Example: {@code GET /api/v1/teachers?page=0&size=12&search=sabaki
     * &state=Kerala&rank=Yondan&minDan=4&sort=danGrade&dir=desc}
     */
    @GetMapping
    public ResponseEntity<PageResponse<TeacherSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String rank,
            @RequestParam(required = false) String belt,
            @RequestParam(required = false) Integer minDan,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {

        Pageable pageable = buildPageable(page, size, sort, dir);
        PageResponse<TeacherSummaryResponse> body = teacherService.listTeachers(
                blankToNull(search), blankToNull(state), blankToNull(rank),
                blankToNull(belt), minDan, pageable);
        return ResponseEntity.ok(body);
    }

    /**
     * The caller's own teacher record (any status). 404 if they have none.
     * Lets the profile page render the teacher's full data.
     */
    @GetMapping("/me")
    public ResponseEntity<TeacherResponse> getMine() {
        UserEntity user = securityService.requireCurrentUser();
        return ResponseEntity.ok(teacherService.getMine(user));
    }

    /**
     * Public teacher detail. 404 if unknown or not publicly visible.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeacherResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getTeacher(id));
    }

    /**
     * Public "Join as Sensei" registration. CSRF-exempt on the security side
     * (it's a public JSON entry point with no session), so it behaves like the
     * auth endpoints. Returns the created teacher (201).
     */
    @PostMapping("/register")
    public ResponseEntity<TeacherResponse> register(
            @Valid @RequestBody TeacherRegistrationRequest request) {
        TeacherResponse created = teacherService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update the caller's own teacher record (PATCH-style — null fields are left
     * unchanged). Lets the profile page edit contact / dojo / training fields.
     * Mirrors StudentController#updateMine.
     */
    @PutMapping("/me")
    public ResponseEntity<TeacherResponse> updateMine(
            @Valid @RequestBody TeacherUpdateRequest req) {
        UserEntity user = securityService.requireCurrentUser();
        return ResponseEntity.ok(teacherService.updateMine(user, req));
    }

    // ---------------------------------------------------------------------
    // Pageable construction
    // ---------------------------------------------------------------------
    private Pageable buildPageable(int page, int size, String sort, String dir) {
        int safeSize = Math.min(Math.max(size, 1), 100); // cap page size
        int safePage = Math.max(page, 0);

        Sort sortObj;
        if (sort != null && SORTABLE.contains(sort)) {
            Sort.Direction direction = "asc".equalsIgnoreCase(dir)
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortObj = Sort.by(direction, sort);
        } else {
            // Sensible default: featured first, then senior dan.
            sortObj = Sort.by(Sort.Direction.DESC, "featured", "danGrade");
        }
        // Stable tie-breaker for consistent pagination.
        sortObj = sortObj.and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(safePage, safeSize, sortObj);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
