package backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Serves locally-uploaded images so the dev fallback works without Cloudflare R2.
 *
 * <p>Files written by {@code LocalStorageService} under the working directory's
 * {@code ./uploads/} folder are exposed at {@code GET /uploads/**}. This is only
 * needed in development (when {@code app.storage.r2.enabled=false}); in production
 * the R2 service returns a fully-qualified public URL and these requests are served
 * by Cloudflare instead.</p>
 */
@Configuration
public class WebStaticConfig implements WebMvcConfigurer {

    /** Filesystem location (relative to the running process) that holds local uploads. */
    private static final String UPLOADS_LOCATION = "file:./uploads/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(UPLOADS_LOCATION)
                // don't map a 404 here as a controller — let missing files 404 normally
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
    }
}
