package backend.camp;

import backend.camp.dto.CampResponse;
import backend.camp.dto.CampSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, read-only camp endpoints (permitAll via SecurityConfig GET rule).
 * Only published camps are exposed here — drafts live behind the admin API.
 */
@RestController
@RequestMapping("/api/v1/camps")
@RequiredArgsConstructor
public class CampController {

    private final CampService campService;

    /** Published camps as summaries, newest year first. */
    @GetMapping
    public ResponseEntity<List<CampSummaryResponse>> list() {
        return ResponseEntity.ok(campService.listPublished());
    }

    /** Published camp detail by slug. 404 if unknown or still a draft. */
    @GetMapping("/{slug}")
    public ResponseEntity<CampResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(campService.getPublishedBySlug(slug));
    }
}
