package backend.cosmetic;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Cosmetic catalogue + admin CRUD.
 *
 * <p>Public read: {@code GET /api/v1/cosmetics} (permitAll) returns the full
 * catalogue the frontend grid renders. Admin mutations live under
 * {@code /api/v1/admin/cosmetics} — gated by SecurityConfig's
 * {@code /api/v1/admin/** -> ADMIN|SUB_ADMIN} rule.</p>
 *
 * <p>Two create paths:
 * <ul>
 *   <li>{@code POST /admin/cosmetics} — JSON body, {@code imageUrl} must
 *       already be a hosted URL (or null for CSS). Reused for the
 *       "re-edit an image cosmetic without re-uploading" flow.</li>
 *   <li>{@code POST /admin/cosmetics/new} — multipart with a {@code payload}
 *       JSON part and an optional {@code file} part. The server uploads
 *       the file to the active {@code StorageService} and saves the row in
 *       one atomic call from the form's perspective.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CosmeticController {

    private final CosmeticService cosmeticService;

    /** Full public catalogue (id, type, name, visuals, unlock info, imageUrl). */
    @GetMapping("/cosmetics")
    public ResponseEntity<List<CosmeticResponse>> catalog() {
        return ResponseEntity.ok(cosmeticService.catalog());
    }

    /** Admin manage list (same set as the public catalogue today). */
    @GetMapping("/admin/cosmetics")
    public ResponseEntity<List<CosmeticResponse>> listAdmin() {
        return ResponseEntity.ok(cosmeticService.listAdmin());
    }

    /** Legacy JSON create — {@code imageUrl} must already be a hosted URL (or null). */
    @PostMapping("/admin/cosmetics")
    public ResponseEntity<CosmeticResponse> create(
            @RequestBody CosmeticCreateRequest request) {
        CosmeticResponse created = cosmeticService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Multipart create — the form posts a {@code payload} JSON part
     * (imageUrl ignored / should be null) plus an optional {@code file}.
     * The server uploads the file and saves the row in one shot.
     */
    @PostMapping(value = "/admin/cosmetics/new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CosmeticResponse> createWithImage(
            @RequestPart("payload") CosmeticCreateRequest payload,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        // The payload's imageUrl is irrelevant in the multipart flow — the
        // server fills it in from the uploaded file. Pass null so the
        // builder doesn't keep a stale value.
        CosmeticCreateRequest cleaned = new CosmeticCreateRequest(
                payload.type(), payload.name(), payload.description(),
                payload.seasonId(), null, payload.unlockType(), payload.unlockBelt(),
                payload.requiredRank(), payload.eventId(), payload.campId(),
                payload.primaryColor(), payload.accentColor(), payload.glowColor(),
                payload.kanji(), payload.patternId());
        CosmeticResponse created = cosmeticService.createWithOptionalImage(cleaned, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/admin/cosmetics/{id}")
    public ResponseEntity<CosmeticResponse> update(
            @PathVariable String id,
            @RequestBody CosmeticUpdateRequest request) {
        return ResponseEntity.ok(cosmeticService.update(id, request));
    }

    @DeleteMapping("/admin/cosmetics/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        cosmeticService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
