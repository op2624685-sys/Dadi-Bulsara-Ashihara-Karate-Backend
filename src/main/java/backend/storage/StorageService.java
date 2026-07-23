package backend.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over image upload. Exactly one implementation is active at a time,
 * chosen by {@code app.storage.cloudinary.enabled} / {@code app.storage.r2.enabled}:
 *
 * <ul>
 *   <li>{@code CloudinaryStorageService} when Cloudinary is enabled,</li>
 *   <li>{@code R2StorageService} when R2 is enabled, and</li>
 *   <li>{@code LocalStorageService} when neither is enabled (or unset → dev fallback).</li>
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
     *                          store (Cloudinary/R2 or local disk) rejects the write
     */
    UploadResult upload(MultipartFile file);

    /**
     * Best-effort delete of a previously-uploaded object by its public URL.
     * Used as cleanup by multipart-create flows: if the upload succeeded but
     * the subsequent DB save failed, the caller rolls back the orphaned cloud
     * asset so it doesn't linger in storage.
     *
     * <p>Callers should always wrap this in a try/catch and log+swallow on
     * failure — a delete error must not mask the original exception that
     * triggered the cleanup. A no-op for unknown URLs is acceptable.</p>
     *
     * @param url the public URL returned by a previous {@link #upload}
     * @throws StorageException if the underlying store rejects the delete
     */
    void delete(String url);
}
