package backend.storage;

/**
 * JSON body returned by {@link UploadController}: { "url": "..." }.
 *
 * @param url the stored object's publicly-reachable URL
 */
public record UploadResponse(String url) {
}
