package backend.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the single active {@link StorageService} implementation.
 *
 * <p>Exactly one provider is active at a time, chosen by its {@code enabled} flag:
 * <ul>
 *   <li>{@code app.storage.cloudinary.enabled=true} → {@link CloudinaryStorageService}</li>
 *   <li>{@code app.storage.r2.enabled=true} → {@link R2StorageService}</li>
 *   <li>neither enabled → {@link LocalStorageService} (local ./uploads dev fallback)</li>
 * </ul>
 *
 * <p>Moving the activation logic here (instead of leaving it on the service
 * classes) keeps the three implementations free of {@code @Service} / condition
 * annotations and guarantees they never collide: previously {@link
 * LocalStorageService} used {@code matchIfMissing=true}, which would have
 * activated it alongside Cloudinary. Now each bean is gated so at most one is
 * created.</p>
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.cloudinary", name = "enabled", havingValue = "true")
    public StorageService cloudinaryStorageService(backend.config.StorageProperties props) {
        return new CloudinaryStorageService(props.cloudinary());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "true")
    public StorageService r2StorageService(backend.config.StorageProperties props) {
        return new R2StorageService(props.r2());
    }

    @Bean
    @ConditionalOnProperties({
        @ConditionalOnProperty(prefix = "app.storage.cloudinary", name = "enabled",
                havingValue = "false", matchIfMissing = true),
        @ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled",
                havingValue = "false", matchIfMissing = true)
    })
    public StorageService localStorageService() {
        return new LocalStorageService();
    }
}
