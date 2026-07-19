package backend.storage;

/**
 * Value returned by {@link StorageService#upload}.
 *
 * @param url publicly-reachable URL of the stored object (R2 public URL, or the
 *           local {@code /uploads/...} path served by {@code WebStaticConfig})
 */
public record UploadResult(String url) {
}
