package backend.camp;

import backend.camp.dto.CampCreateRequest;
import backend.camp.dto.CampResponse;
import backend.camp.dto.CampSummaryResponse;
import backend.camp.dto.CampUpdateRequest;
import backend.teacher.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Business operations for camps. Public reads are restricted to published
 * camps; the admin operations (create/update/delete/publish) are gated to ADMIN
 * by SecurityConfig ({@code /api/v1/admin/camps/**}).
 */
public interface CampService {

    // ── Public ────────────────────────────────────────────────────────────
    /** Published camps as summaries, newest year first. */
    List<CampSummaryResponse> listPublished();

    /** Published camp detail by slug; 404 if missing or still a draft. */
    CampResponse getPublishedBySlug(String slug);

    // ── Admin ─────────────────────────────────────────────────────────────
    /** All camps (drafts included), paginated, for the manage list. */
    PageResponse<CampSummaryResponse> listForAdmin(String search, Pageable pageable);

    /** Single camp by id (drafts included). */
    CampResponse getForAdmin(Long id);

    /** Create a camp as a hidden DRAFT with a generated, unique slug. */
    CampResponse create(CampCreateRequest req);

    /** Partial update; the slug is kept stable. */
    CampResponse update(Long id, CampUpdateRequest req);

    /** Delete a camp. */
    void delete(Long id);

    /** Make the camp publicly visible. */
    CampResponse publish(Long id);

    /** Hide the camp from the public pages again. */
    CampResponse unpublish(Long id);
}
