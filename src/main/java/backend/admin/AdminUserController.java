package backend.admin;

import backend.admin.dto.AdminUserSummaryResponse;
import backend.admin.dto.RoleChangeRequest;
import backend.admin.dto.SubAdminCreateRequest;
import backend.security.SecurityService;
import backend.teacher.dto.PageResponse;
import backend.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * ADMIN-only user management: list users, create state-scoped sub-admins, and
 * promote/demote roles. The whole {@code /api/v1/admin/**} tree is already open
 * to SUB_ADMIN in SecurityConfig, so each method here re-checks for ADMIN and
 * returns 403 otherwise (a sub-admin must not manage other accounts).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final SecurityService securityService;

    private static final Set<String> SORTABLE = Set.of(
            "id", "email", "firstName", "lastName", "role", "createdAt");

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserSummaryResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        requireAdmin();
        Pageable pageable = buildPageable(page, size, sort, dir);
        return ResponseEntity.ok(adminUserService.listUsers(blankToNull(search), role, pageable));
    }

    @PostMapping("/sub-admins")
    public ResponseEntity<AdminUserSummaryResponse> createSubAdmin(
            @Valid @RequestBody SubAdminCreateRequest request) {
        requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createSubAdmin(request));
    }

    @PostMapping("/users/{id}/role")
    public ResponseEntity<AdminUserSummaryResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleChangeRequest request) {
        requireAdmin();
        return ResponseEntity.ok(adminUserService.changeRole(id, request));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private void requireAdmin() {
        if (securityService.getCurrentUserOrNull() == null
                || securityService.getCurrentUserOrNull().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only an ADMIN may manage users");
        }
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
