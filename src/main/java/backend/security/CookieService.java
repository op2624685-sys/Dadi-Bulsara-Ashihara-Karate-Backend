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
 *   - SameSite   : Lax (CSRF-safe — POSTs are still blocked cross-site by the
 *                  XSRF token check; GETs (like /me) need the cookie to be
 *                  sent on top-level navigation. Strict blocks cookies on all
 *                  cross-site requests including GETs, which breaks /me after
 *                  a same-tab login redirect when the frontend and backend are
 *                  on different eTLD+1 hosts (e.g. Vercel -> Railway).)
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
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
