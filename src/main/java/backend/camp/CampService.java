package backend.camp;

import backend.camp.dto.CampCreateRequest;
import backend.camp.dto.CampResponse;
import backend.camp.dto.CampSummaryResponse;
import backend.camp.dto.CampUpdateRequest;
import backend.teacher.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * Multipart create: upload all provided image files, attach the resulting
     * URLs to the request, then save. See {@link #create} for the JSON-only
     * alternative. The {@code payload} should have all image fields
     * {@code null} (or empty) — they're filled in by the uploads.
     */
    CampResponse createWithImages(CampCreateRequest payload,
                                  MultipartFile heroImage,
                                  MultipartFile aboutImage0,
                                  MultipartFile aboutImage1,
                                  List<MultipartFile> galleryImages,
                                  List<MultipartFile> instructorImages);

    /**
     * Multipart update. Same contract as {@link #createWithImages} but
     * applies to the existing camp. Image fields that are non-null in the
     * payload AND have a new file part are replaced; fields with a URL but
     * no file keep the existing URL; fields without a URL and no file
     * resolve to {@code null}.
     */
    CampResponse updateWithImages(Long id, CampUpdateRequest payload,
                                  MultipartFile heroImage,
                                  MultipartFile aboutImage0,
                                  MultipartFile aboutImage1,
                                  List<MultipartFile> galleryImages,
                                  List<MultipartFile> instructorImages);

    /** Delete a camp. */
    void delete(Long id);

    /** Make the camp publicly visible. */
    CampResponse publish(Long id);

    /** Hide the camp from the public pages again. */
    CampResponse unpublish(Long id);
}
