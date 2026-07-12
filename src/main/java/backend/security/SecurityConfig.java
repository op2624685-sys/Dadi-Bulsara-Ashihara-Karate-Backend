package backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Main security configuration: stateless API, CORS-aware, CSRF-protected
 * for browser clients, public auth endpoints, JWT filter in front of the
 * username/password filter.
 *
 * <p>CSRF strategy: Spring's CookieCsrfTokenRepository sets a non-HttpOnly
 * {@code XSRF-TOKEN} cookie on first response. The frontend reads the cookie
 * value and echoes it in {@code X-XSRF-TOKEN} on every state-changing request.
 * The auth endpoints below are exempt from CSRF (they have no existing
 * session to protect; they're the public entry points).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthEntryPoint authEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;

    /**
     * CSRF is enabled only in deployment. Dev mode disables it so the SPA can
     * call mutating endpoints without the double-submit token dance. Flip via
     * {@code app.security.csrf-enabled=true} (or env {@code APP_SECURITY_CSRF_ENABLED=true}).
     */
    @Value("${app.security.csrf-enabled:false}")
    private boolean csrfEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieName("XSRF-TOKEN");
        csrfRepo.setHeaderName("X-XSRF-TOKEN");
        CsrfCookieFilter csrfCookieFilter = new CsrfCookieFilter(csrfRepo);

        http
            .cors(cors -> {}) // uses CorsConfig.corsConfigurationSource bean
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // /me needs a valid session — keep it out of the broad
                    // /api/v1/auth/** permitAll so unauthenticated calls get a
                    // 401 from the entry point instead of reaching the controller.
                    .requestMatchers("/api/v1/auth/me").authenticated()
                    .requestMatchers(
                            "/actuator/health",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/api/v1/auth/**",
                            // Public teacher directory (list, detail) + open
                            // registration. GETs need no CSRF; the POST is
                            // already CSRF-exempt above.
                            "/api/v1/teachers/**"
                    ).permitAll()
                    // Student registration is authenticated (tied to the caller's
                    // account) — declare it BEFORE the broad /students/** permitAll
                    // so the more specific rule wins.
                    .requestMatchers(HttpMethod.POST, "/api/v1/students/register").authenticated()
                    // A student's own application — only visible to the applicant.
                    .requestMatchers(HttpMethod.GET, "/api/v1/students/me").hasRole("STUDENT")
                    // Public student directory (list + detail) — GETs only.
                    .requestMatchers("/api/v1/students/**").permitAll()
                    // Sensei-facing student approval queue.
                    .requestMatchers("/api/v1/teacher/**").hasRole("TEACHER")
                    .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUB_ADMIN")
                    .anyRequest().authenticated())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (csrfEnabled) {
            // CSRF protection for browser clients — enabled in production /
            // deployment only. Public JSON entry points are exempt.
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(csrfRepo)
                    // Auth endpoints are JSON-only; they're the public entry points
                    // and don't have an existing session to protect.
                    .ignoringRequestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/signup",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout",
                            "/api/v1/auth/forgot-password",
                            "/api/v1/auth/reset-password",
                            "/api/v1/auth/resend-verification",
                            // Public teacher directory + "Join as Sensei"
                            // registration. The registration endpoint is a
                            // public JSON entry point with no session, so it
                            // is CSRF-exempt just like the auth endpoints.
                            "/api/v1/teachers/register"))
                // Force the XSRF-TOKEN cookie onto every response so the SPA can
                // read it (the deferred CsrfTokenRequestHandler only writes the
                // cookie when the token value is accessed, which never happens
                // for a pure JSON client — without this, every admin POST would
                // 403 on CSRF).
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class);
        } else {
            // Development: CSRF disabled so the SPA can call mutating endpoints
            // without the double-submit token. Re-enable for deployment via
            // app.security.csrf-enabled=true.
            http.csrf(csrf -> csrf.disable());
        }

        return http.build();
    }

    /**
     * Ensures the double-submit CSRF cookie is always present for the browser
     * client. It unconditionally generates + persists a fresh token on every
     * response, which the frontend reads from {@code document.cookie} and echoes
     * back as the {@code X-XSRF-TOKEN} header on state-changing calls.
     */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        private final CookieCsrfTokenRepository csrfRepo;

        CsrfCookieFilter(CookieCsrfTokenRepository csrfRepo) {
            this.csrfRepo = csrfRepo;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, java.io.IOException {
            CsrfToken token = csrfRepo.generateToken(request);
            csrfRepo.saveToken(token, request, response);
            // Echo the token in a response header so a cross-origin SPA (which
            // cannot read the XSRF-TOKEN cookie via document.cookie) can capture
            // it and echo it back as the X-XSRF-TOKEN request header. Must be
            // CORS-exposed (see CorsConfig).
            response.setHeader("X-XSRF-TOKEN", token.getToken());
            filterChain.doFilter(request, response);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
