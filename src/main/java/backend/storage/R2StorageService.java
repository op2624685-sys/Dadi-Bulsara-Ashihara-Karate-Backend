package backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded images in Cloudflare R2 (an S3-compatible object store).
 *
 * <p>Instantiated by {@link backend.storage.StorageConfig} only when
 * {@code app.storage.r2.enabled=true}. An {@link S3Client} is built once against
 * R2's S3-compatible endpoint using the account credentials;
 * {@code pathStyleAccessEnabled(true)} makes the bucket part of the request path,
 * which is how R2 expects requests.</p>
 *
 * <p>Rejected writes (bad/empty file, SDK error) are wrapped in
 * {@link StorageException} → HTTP 502 via {@code GlobalExceptionHandler}.</p>
 */
public class R2StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

    /** Allowed upload content-types (images only). */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /** Object-key prefix inside the bucket. */
    private static final String KEY_PREFIX = "uploads/";

    private final S3Client s3Client;
    private final String bucket;
    private final String publicUrl;

    /**
     * Build the service (and the R2 client) from R2 config.
     * Only invoked when R2 is enabled, so missing credentials fail fast here.
     */
    public R2StorageService(backend.config.StorageProperties.R2 r2) {
        this.bucket = r2.bucket();
        this.publicUrl = stripTrailingSlash(r2.publicUrl());

        if (isBlank(r2.accountId()) || isBlank(r2.accessKey()) || isBlank(r2.secretKey())
                || isBlank(bucket) || isBlank(publicUrl)) {
            throw new StorageException(
                    "R2 is enabled but required config is missing " +
                    "(account-id, access-key, secret-key, bucket, public-url).");
        }

        URI endpoint = URI.create("https://" + r2.accountId() + ".r2.cloudflarestorage.com");
        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                // R2 ignores the region, but the SDK requires one to be set.
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.accessKey(), r2.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        log.info("R2StorageService initialised for bucket '{}' at {}", bucket, endpoint);
    }

    @Override
    public UploadResult upload(MultipartFile file) {
        validate(file);
        String originalName = file.getOriginalFilename();
        // uploads/<uuid>-<sanitizedName>
        String key = KEY_PREFIX + UUID.randomUUID() + "-" + sanitizeName(originalName);

        log.info("Uploading to R2: bucket='{}' key='{}' contentType='{}' size={}",
                bucket, key, file.getContentType(), file.getSize());

        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String url = publicUrl + "/" + key;
            log.info("R2 upload succeeded: {}", url);
            return new UploadResult(url);
        } catch (SdkException | IOException ex) {
            throw new StorageException("Failed to upload image to R2 (key=" + key + ")", ex);
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) return;
        String key = extractKey(url);
        if (key == null) {
            log.warn("R2 delete skipped: could not parse key from url={}", url);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("R2 delete succeeded: bucket='{}' key='{}'", bucket, key);
        } catch (SdkException ex) {
            throw new StorageException("Failed to delete image from R2 (key=" + key + ")", ex);
        }
    }

    /**
     * Pull the S3 object key out of a public R2 URL. Accepts either the bare
     * public URL (host = publicUrl) or any URL whose path starts with the
     * bucket's key prefix.
     */
    private String extractKey(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.isBlank()) return null;
            String trimmed = path.startsWith("/") ? path.substring(1) : path;
            // Accept either "uploads/..." (raw key) or any leading host segments.
            if (trimmed.startsWith(KEY_PREFIX)) return trimmed;
            // Fallback: take the last N segments after the public URL host.
            return null;
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

    /**
     * Keep only a safe basename: strip any path, and replace characters that
     * aren't alphanumeric / dot / dash / underscore with '_'.
     */
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

    private static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
