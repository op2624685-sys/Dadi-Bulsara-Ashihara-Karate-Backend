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
 * Registration document upload endpoint for unprivileged applicants.
 *
 * <p>Unlike {@link UploadController} (which is restricted to ADMIN|SUB_ADMIN and
 * backs the admin CMS image picker), this endpoint is open to any authenticated
 * user so that a student/teacher applicant can upload the identity documents
 * their registration form requires (Aadhar, passport photo, belt certificate,
 * signature, profile photo). Authorization is enforced by {@code SecurityConfig}
 * ({@code POST /api/v1/uploads/registration -> authenticated()}); no security
 * annotations are added here.</p>
 *
 * <p>Receives a single multipart field named {@code file}, delegates to the
 * active {@link StorageService} (R2 or local fallback), and returns
 * {@code { "url": "..." }}. The active service validates the file and throws
 * {@link StorageException} → HTTP 502 on failure.</p>
 */
@RestController
@RequestMapping("/api/v1/uploads/registration")
@RequiredArgsConstructor
public class RegistrationUploadController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationUploadController.class);

    private final StorageService storageService;

    /**
     * @param file the image to store (field name {@code file}); must be a
     *            non-empty png/jpeg/webp/gif
     * @return {@link UploadResponse} containing the stored object's URL
     */
    @PostMapping
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") @NotNull MultipartFile file) {
        log.info("Registration document upload requested: name='{}' contentType='{}' size={}",
                file.getOriginalFilename(), file.getContentType(), file.getSize());

        UploadResult result = storageService.upload(file);
        log.info("Registration document upload succeeded -> {}", result.url());
        return ResponseEntity.ok(new UploadResponse(result.url()));
    }
}
