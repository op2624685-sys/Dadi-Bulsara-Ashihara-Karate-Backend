package backend.auth;

import backend.auth.dto.*;
import backend.common.RateLimiter;
import backend.config.ApplicationProperties;
import backend.security.CookieService;
import backend.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int LOGIN_MAX_PER_MIN    = 5;
    private static final int FORGOT_MAX_PER_MIN   = 3;
    private static final int VERIFY_MAX_PER_MIN   = 5;

    private final AuthService authService;
    private final CookieService cookieService;
    private final ApplicationProperties props;
    private final RateLimiter rateLimiter;

    // --- public endpoints ------------------------------------------------

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest req,
                                                  HttpServletRequest http) {
        // No cookie write — the user is not logged in until they verify
        // their email. AuthService.signup emails a one-time link.
        MessageResponse msg = authService.signup(req, ua(http), ip(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(msg);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam("token") String token,
                                                      HttpServletRequest http) {
        // Rate-limit per IP to prevent token-bruteforce (random 256-bit
        // tokens are infeasible to guess, but belt-and-braces).
        rateLimiter.checkOrThrow("verify:" + ip(http), VERIFY_MAX_PER_MIN, Duration.ofMinutes(1));
        authService.verifyEmail(token);
        return ResponseEntity.ok(MessageResponse.of("Email verified. You can now sign in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ForgotPasswordRequest req,
                                                             HttpServletRequest http) {
        rateLimiter.checkOrThrow("verify-resend:" + ip(http), FORGOT_MAX_PER_MIN, Duration.ofMinutes(1));
        authService.requestEmailVerification(req);
        // Same response shape regardless of whether the email exists.
        return ResponseEntity.ok(MessageResponse.of(
                "If that email is registered, a verification link has been sent"));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletRequest http,
                                              HttpServletResponse res) {
        rateLimiter.checkOrThrow("login:" + ip(http), LOGIN_MAX_PER_MIN, Duration.ofMinutes(1));
        AuthResponse result = authService.login(req, ua(http), ip(http));
        writeAuthCookies(res, result);
        return ResponseEntity.ok(result.user());
    }

    @PostMapping("/refresh")
    public ResponseEntity<UserResponse> refresh(HttpServletRequest http,
                                                HttpServletResponse res) {
        String presented = readRefreshCookie(http);
        AuthResponse result = authService.refresh(presented, ua(http), ip(http));
        writeAuthCookies(res, result);
        return ResponseEntity.ok(result.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest http,
                                                  HttpServletResponse res) {
        String presented = readRefreshCookie(http);
        authService.logout(presented);
        cookieService.clearAll(res);
        return ResponseEntity.ok(MessageResponse.of("Logged out"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req,
                                                         HttpServletRequest http) {
        rateLimiter.checkOrThrow("forgot:" + ip(http), FORGOT_MAX_PER_MIN, Duration.ofMinutes(1));
        authService.requestPasswordReset(req);
        // Always return the same message to prevent email enumeration
        return ResponseEntity.ok(MessageResponse.of(
                "If that email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(MessageResponse.of(
                "Password has been reset. Please log in with your new password."));
    }

    // --- authenticated endpoints ----------------------------------------

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getCurrentUser(principal.getUsername()));
    }

    @PutMapping("/me/cosmetics")
    public ResponseEntity<UserResponse> updateCosmetics(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateCosmeticsRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.updateCosmetics(req, principal.getUsername()));
    }

    // --- helpers --------------------------------------------------------

    private void writeAuthCookies(HttpServletResponse res, AuthResponse result) {
        Duration accessTtl  = props.jwt().accessTtl();
        Duration refreshTtl = props.jwt().refreshTtl();
        cookieService.writeAccessCookie(res,  result.accessToken(),  accessTtl);
        cookieService.writeRefreshCookie(res, result.refreshToken(), refreshTtl);
    }

    private String readRefreshCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (var c : req.getCookies()) {
            if (CookieService.REFRESH_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private static String ua(HttpServletRequest req) {
        String h = req.getHeader("User-Agent");
        return h != null ? h : "unknown";
    }

    private static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "unknown";
    }
}
