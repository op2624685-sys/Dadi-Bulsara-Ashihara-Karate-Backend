package backend.admin;

import backend.admin.dto.AdminStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live aggregate counts for the admin dashboard. Reachable by ADMIN and
 * SUB_ADMIN (gated in SecurityConfig); the numbers returned are automatically
 * scoped to the caller (global for ADMIN, single-state for a SUB_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(adminStatsService.stats());
    }
}
