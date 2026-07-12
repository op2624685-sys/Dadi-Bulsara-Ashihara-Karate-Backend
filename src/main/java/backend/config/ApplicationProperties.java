package backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Typed view of the {@code app.*} namespace in application.yaml.
 * Bound by @EnableConfigurationProperties on the security config (see SecurityConfig).
 */
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        Jwt jwt,
        Cors cors,
        Mail mail,
        Cookies cookies,
        Duration verificationTtl
) {

    public record Jwt(
            String secret,
            Duration accessTtl,
            Duration refreshTtl,
            String issuer
    ) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Mail(
            String from,
            String resetBaseUrl,
            String verifyBaseUrl
    ) {}

    public record Cookies(boolean secure, String sameSite) {}
}