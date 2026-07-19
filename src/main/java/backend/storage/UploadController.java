package backend.storage;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin image upload endpoint.
 *
 * <p>Receives a single multipart field named {@code file}, delegates to the
 * active {@link StorageService} (R2 or local fallback, chosen by
 * {@code app.storage.r2.enabled}), and returns {@code { "url": "..." }}.
 * The active service validates the file and throws {@link StorageException}
 * → HTTP 502 on failure.</p>
 *
 * <p>Authorization is enforced by {@code SecurityConfig}
 * ({@code /api/v1/admin/upload -> ADMIN|SUB_ADMIN}); no security annotations
 * are added here.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/upload")
@RequiredArgsConstructor
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final StorageService storageService;

    /**
     * @param file the image to store (field name {@code file}); must be a
     *            non-empty png/jpeg/webp/gif
     * @return {@link UploadResponse} containing the stored object's URL
     */
    @PostMapping
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") @NotNull MultipartFile file) {
        log.info("Image upload requested: name='{}' contentType='{}' size={}",
                file.getOriginalFilename(), file.getContentType(), file.getSize());

        UploadResult result = storageService.upload(file);
        log.info("Image upload succeeded -> {}", result.url());
        return ResponseEntity.ok(new UploadResponse(result.url()));
    }
}
