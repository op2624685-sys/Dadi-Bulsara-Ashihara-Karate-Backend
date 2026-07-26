package backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code app.storage.*} namespace in application.yaml.
 *
 * <p>Registered automatically by {@code @ConfigurationPropertiesScan("backend.config")}
 * on {@link backend.KarateApplication}, so no {@code @Component} / {@code @EnableConfigurationProperties}
 * is needed. Values are sourced from {@code application.yaml}, whose placeholders
 * ({@code ${R2_*_KARATE}}) are resolved from {@code .env} by {@code KarateApplication#main}.</p>
 *
 * <p>Usage: inject {@code StorageProperties} (or just {@code StorageProperties.R2}) into a
 * {@code @Configuration} / {@code @Service} bean to build the R2 client.</p>
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        /** Master switch. When {@code true} the R2-backed service is used; otherwise the local fallback. */
        boolean r2Enabled,
        /** Cloudflare R2 (S3-compatible) connection details. */
        R2 r2,
        /** Cloudinary connection details (alternative to R2). */
        Cloudinary cloudinary
) {

    /**
     * Cloudflare R2 configuration. Mirrors the {@code app.storage.r2.*} yaml keys.
     *
     * @param enabled   whether R2 uploads are active ({@code app.storage.r2.enabled})
     * @param accountId R2 account id, used to build the endpoint host
     * @param accessKey R2 API access key id (S3-compatible)
     * @param secretKey R2 API secret access key (S3-compatible)
     * @param bucket    target R2 bucket name
     * @param publicUrl public base URL where objects are served (with no trailing slash)
     */
    public record R2(
            boolean enabled,
            String accountId,
            String accessKey,
            String secretKey,
            String bucket,
            String publicUrl
    ) {}

    /**
     * Cloudinary configuration. Mirrors the {@code app.storage.cloudinary.*} yaml keys.
     * Active only when {@code app.storage.cloudinary.enabled=true}; the SDK is built from
     * a {@code cloudinary://api-key:api-secret@cloud-name} URL.
     *
     * @param enabled   whether Cloudinary uploads are active ({@code app.storage.cloudinary.enabled})
     * @param cloudName Cloudinary cloud name
     * @param apiKey    Cloudinary API key
     * @param apiSecret Cloudinary API secret
     */
    public record Cloudinary(
            boolean enabled,
            String cloudName,
            String apiKey,
            String apiSecret
    ) {}
}
