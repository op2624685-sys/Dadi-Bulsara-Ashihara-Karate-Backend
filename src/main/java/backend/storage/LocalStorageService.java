package backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dev fallback for image uploads: writes the file to {@code ./uploads/} on the
 * local filesystem and returns a {@code /uploads/...} URL served by
 * {@link backend.config.WebStaticConfig}.
 *
 * <p>Activated when R2 is disabled (or the property is absent), so local
 * development runs without any Cloudflare credentials. Same input validation as
 * {@link R2StorageService}; failures raise {@link StorageException} → HTTP 502.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /** Directory (relative to the working dir) where uploads are written. */
    private static final Path UPLOAD_DIR = Paths.get("uploads");

    @Override
    public UploadResult upload(MultipartFile file) {
        validate(file);
        String originalName = file.getOriginalFilename();
        String storedName = UUID.randomUUID() + "-" + sanitizeName(originalName);
        Path target = UPLOAD_DIR.resolve(storedName).normalize().toAbsolutePath();

        // Guard against path traversal: the resolved file must stay inside ./uploads.
        if (!target.startsWith(UPLOAD_DIR.toAbsolutePath().normalize())) {
            throw new StorageException("Invalid upload filename: " + originalName);
        }

        log.info("Uploading locally: target='{}' contentType='{}' size={}",
                target, file.getContentType(), file.getSize());

        try {
            Files.createDirectories(UPLOAD_DIR);
            Files.copy(file.getInputStream(), target);

            String url = "/uploads/" + storedName;
            log.info("Local upload succeeded: {}", url);
            return new UploadResult(url);
        } catch (IOException ex) {
            throw new StorageException("Failed to write upload to local disk: " + storedName, ex);
        }
    }

    // ---------------------------------------------------------------------
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Uploaded file is empty.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            List<String> allowed = ALLOWED_CONTENT_TYPES.stream().sorted().toList();
            throw new StorageException(
                    "Unsupported file type '" + contentType + "'. Allowed: " + allowed);
        }
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "file";
        }
        String base = name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (base.isBlank()) {
            return "file";
        }
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
