package backend.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over image upload. Exactly one implementation is active at a time,
 * chosen by {@code app.storage.r2.enabled}:
 *
 * <ul>
 *   <li>{@code R2StorageService} when R2 is enabled, and</li>
 *   <li>{@code LocalStorageService} when it is disabled (or unset → dev fallback).</li>
 * </ul>
 *
 * Implementations validate the incoming file and throw {@link StorageException}
 * on rejection / failure; callers should treat that as "upload unavailable".
 */
public interface StorageService {

    /**
     * Store the given image and return a URL from which it can be served.
     *
     * @param file the multipart upload (must be a non-empty image)
     * @return an {@link UploadResult} whose {@code url} is publicly reachable
     * @throws StorageException if the file is invalid, empty, or the underlying
     *                          store (R2 or local disk) rejects the write
     */
    UploadResult upload(MultipartFile file);
}
