package backend.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stores uploaded images in Cloudinary.
 *
 * <p>Instantiated by {@link backend.storage.StorageConfig} only when
 * {@code app.storage.cloudinary.enabled=true}. The SDK client is built from a
 * {@code cloudinary://api-key:api-secret@cloud-name} URL; uploads return the
 * CDN {@code secure_url}.</p>
 *
 * <p>Rejected writes (bad/empty file, SDK error) are wrapped in
 * {@link StorageException} → HTTP 502 via {@code GlobalExceptionHandler}, so callers
 * treat a failure as "upload unavailable" exactly like the other implementations.</p>
 */
public class CloudinaryStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorageService.class);

    /** Allowed upload content-types (images only). */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final Cloudinary cloudinary;

    /**
     * Build the service (and the Cloudinary client) from config.
     * Only invoked when Cloudinary is enabled, so missing credentials fail fast here.
     */
    public CloudinaryStorageService(backend.config.StorageProperties.Cloudinary cfg) {
        if (isBlank(cfg.cloudName()) || isBlank(cfg.apiKey()) || isBlank(cfg.apiSecret())) {
            throw new StorageException(
                    "Cloudinary is enabled but required config is missing " +
                    "(cloud-name, api-key, api-secret).");
        }
        String url = "cloudinary://" + cfg.apiKey() + ":" + cfg.apiSecret() + "@" + cfg.cloudName();
        this.cloudinary = new Cloudinary(url);
        log.info("CloudinaryStorageService initialised for cloud '{}'", cfg.cloudName());
    }

    @Override
    public UploadResult upload(MultipartFile file) {
        validate(file);
        log.info("Uploading to Cloudinary: name='{}' contentType='{}' size={}",
                file.getOriginalFilename(), file.getContentType(), file.getSize());
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
            String url = (String) result.get("secure_url");
            if (url == null) {
                url = (String) result.get("url");
            }
            if (url == null) {
                throw new StorageException("Cloudinary did not return a URL for the upload.");
            }
            log.info("Cloudinary upload succeeded: {}", url);
            return new UploadResult(url);
        } catch (Exception ex) {
            throw new StorageException("Failed to upload image to Cloudinary", ex);
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) return;
        String publicId = extractPublicId(url);
        if (publicId == null) {
            log.warn("Cloudinary delete skipped: could not parse public_id from url={}", url);
            return;
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            Object status = result != null ? result.get("result") : null;
            log.info("Cloudinary delete attempted: publicId={} status={}", publicId, status);
        } catch (Exception ex) {
            throw new StorageException("Failed to delete image from Cloudinary (publicId=" + publicId + ")", ex);
        }
    }

    /**
     * Parse the Cloudinary public_id from a CDN URL.
     *
     * <p>URL shape: {@code https://res.cloudinary.com/<cloud>/image/upload/[v123/]<publicId>.<ext>}}
     * or {@code https://res.cloudinary.com/<cloud>/image/upload/<transformations>/<publicId>.<ext>}}
     * The public_id is the last path segment with its extension stripped.
     * Folder prefixes (e.g. {@code uploads/abc}) are kept as part of the id
     * (Cloudinary treats {@code folder/id} as one public_id).</p>
     */
    private static String extractPublicId(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();          // e.g. /<cloud>/image/upload/v123/abc.jpg
            int marker = path.indexOf("/image/upload/");
            if (marker < 0) return null;
            String suffix = path.substring(marker + "/image/upload/".length()); // "v123/abc.jpg" or "abc.jpg"
            // Strip version segment if present (starts with "v" + digits).
            if (suffix.matches("v\\d+/.+")) {
                suffix = suffix.substring(suffix.indexOf('/') + 1);
            }
            // Drop the file extension.
            int dot = suffix.lastIndexOf('.');
            if (dot > 0) suffix = suffix.substring(0, dot);
            return suffix.isEmpty() ? null : suffix;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    /** Validate that the file is a non-empty, allowed image type. */
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
