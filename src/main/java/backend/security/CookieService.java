package backend.security;

import backend.config.ApplicationProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Centralises cookie creation/clearing so the controller layer stays clean.
 * All auth cookies share the same attributes:
 *   - HttpOnly   : not accessible to JS (XSS-safe)
 *   - Secure     : off in dev (no HTTPS), on in prod (config flag)
 *   - SameSite   : configurable via app.cookies.same-site (Lax default,
 *                  None for cross-site prod). When set to None, browsers
 *                  also require Secure=true — the cookie is rejected
 *                  otherwise. The CSRF double-submit (XSRF-TOKEN cookie +
 *                  X-XSRF-TOKEN header) is the active CSRF defence when
 *                  SameSite=None.
 *   - Path       : /
 */
@Service
@RequiredArgsConstructor
public class CookieService {

    public static final String ACCESS_COOKIE  = "kf_access";
    public static final String REFRESH_COOKIE = "kf_refresh";

    private final ApplicationProperties props;

    public void writeAccessCookie(HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(ACCESS_COOKIE, token, maxAge).toString());
    }

    public void writeRefreshCookie(HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(REFRESH_COOKIE, token, maxAge).toString());
    }

    public void clearAll(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(ACCESS_COOKIE,  "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, build(REFRESH_COOKIE, "", Duration.ZERO).toString());
    }

    private ResponseCookie build(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.cookies().secure())
                .sameSite(props.cookies().sameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
