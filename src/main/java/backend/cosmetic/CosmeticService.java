package backend.cosmetic;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages the cosmetic catalogue and the reward-grant pipeline.
 *
 * <ul>
 *   <li>{@link #catalog()} — the full public list (id, type, name, visuals,
 *       unlock info, imageUrl) used to render the frontend grid.</li>
 *   <li>{@link #grantCosmetic} — idempotent append of a cosmetic id to a user's
 *       {@code unlockedCosmetics}, used when a linked participant places in an
 *       event/camp.</li>
 *   <li>Admin CRUD — create / update / delete image cosmetics.</li>
 * </ul>
 */
public interface CosmeticService {

    /** Full catalogue (all cosmetics) for the public frontend grid. */
    List<CosmeticResponse> catalog();

    /** Admin manage list — currently the same set as the public catalogue. */
    List<CosmeticResponse> listAdmin();

    /** Idempotent: appends {@code cosmeticId} to the user's unlocked list. */
    void grantCosmetic(Long userId, String cosmeticId);

    /**
     * JSON-only create. The {@code imageUrl} on the request must already be a
     * fully-resolved storage URL (or null for a CSS/SVG cosmetic). Kept for
     * the legacy POST {@code /api/v1/admin/cosmetics} endpoint and for callers
     * that already have a hosted URL.
     */
    CosmeticResponse create(CosmeticCreateRequest req);

    /**
     * Multipart create for the CMS form. The caller sends the metadata in
     * {@code req} (with {@code imageUrl=null}) plus an optional {@code file}.
     * If a file is attached, it is uploaded to the active {@code StorageService}
     * and the resulting URL is written into the new row.
     *
     * <p>The upload happens BEFORE the DB save. If the DB save fails after a
     * successful upload, the uploaded image is best-effort deleted to avoid
     * leaving an orphan in storage. (If the upload itself fails, no DB row is
     * attempted, so there is no orphan.)</p>
     */
    CosmeticResponse createWithOptionalImage(CosmeticCreateRequest req, MultipartFile file);

    CosmeticResponse update(String id, CosmeticUpdateRequest req);

    /** Only image cosmetics (admin-created) may be deleted; built-in belt
     *  cosmetics are protected. */
    void delete(String id);
}
