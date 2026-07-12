package backend.security;

import backend.common.exception.InvalidTokenException;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the access token from the {@code kf_access} cookie (fallback:
 * {@code Authorization: Bearer ...}). On a valid token, populates the
 * SecurityContext with the loaded user. On any failure, simply clears the
 * context and lets the chain continue — public endpoints stay reachable,
 * protected ones get a 401 from the entry point.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JWTClaimsSet claims = jwtService.parseAndValidate(token);
                UserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (InvalidTokenException ex) {
                // bad/expired token — clear context, do not 401 here (entry point decides)
                SecurityContextHolder.clearContext();
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
                // user deleted but token still valid — treat as unauthenticated
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Cookie (preferred for SPAs)
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (CookieService.ACCESS_COOKIE.equals(c.getName())) {
                    String v = c.getValue();
                    if (v != null && !v.isBlank()) return v;
                }
            }
        }
        // 2. Authorization: Bearer header (fallback for non-browser clients)
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length()).trim();
        }
        return null;
    }
}
