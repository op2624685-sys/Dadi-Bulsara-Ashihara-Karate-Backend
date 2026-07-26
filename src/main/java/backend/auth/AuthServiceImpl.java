package backend.auth;

import backend.auth.dto.*;
import backend.common.SecureTokenGenerator;
import backend.common.TokenHasher;
import backend.common.exception.EmailAlreadyExistsException;
import backend.common.exception.EmailNotVerifiedException;
import backend.common.exception.InvalidTokenException;
import backend.config.ApplicationProperties;
import backend.security.JwtService;
import backend.user.CosmeticCatalogue;
import backend.user.Provider;
import backend.user.Role;
import backend.user.UserEntity;
import backend.user.UserRepository;
import backend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final ApplicationProperties props;
    private final CosmeticCatalogue cosmeticCatalogue;

    // ---------------------------------------------------------------------
    // Signup
    //
    // Creates the user, marks emailVerified=false, and emails a one-time
    // verification link. Does NOT issue auth tokens — the user is not
    // signed in until they click the link. Login is gated by the
    // emailVerified check below.
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    @CacheEvict(value = "adminStats", allEntries = true)
    public MessageResponse signup(SignupRequest req, String userAgent, String ip) {
        String email = req.email().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        UserEntity user = UserEntity.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .firstName(req.firstName())
                .lastName(req.lastName())
                // Signup is intentionally restricted to USER; promotion to
                // STUDENT / TEACHER / ADMIN is an admin-panel action.
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .enabled(true)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        log.info("New user signed up: id={} email={} role={}", user.getId(), user.getEmail(), user.getRole());

        // Issue a verification token + email it. Failures here are logged
        // (EmailService falls back to [DEV] console) but do not propagate —
        // the signup is already committed at that point.
        String rawToken = SecureTokenGenerator.generate();
        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(rawToken))
                .expiresAt(Instant.now().plus(props.verificationTtl()))
                .used(false)
                .build());
        emailService.sendVerificationEmail(user.getEmail(), rawToken);

        return MessageResponse.of("Check your inbox to verify your email and activate your account");
    }

    // ---------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public AuthResponse login(LoginRequest req, String userAgent, String ip) {
        String email = req.email().toLowerCase();

        // AuthenticationManager throws BadCredentialsException on failure;
        // GlobalExceptionHandler maps that to 401 with a generic message.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.password()));

        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidTokenException("Authenticated user not found"));

        if (!user.isEnabled()) {
            throw new org.springframework.security.authentication.DisabledException(
                    "Account is disabled");
        }

        // Reject unverified accounts with a distinct error code so the
        // frontend can show "verify your email" + a resend link, instead of
        // the generic "invalid credentials" message.
        if (!user.isEmailVerified()) {
            log.info("Login rejected — unverified email: id={} email={}", user.getId(), user.getEmail());
            throw new EmailNotVerifiedException(email);
        }

        log.info("User logged in: id={} email={}", user.getId(), user.getEmail());

        return issueNewPair(user, userAgent, ip);
    }

    // ---------------------------------------------------------------------
    // Refresh (with rotation + theft detection)
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public AuthResponse refresh(String presentedRefreshToken, String userAgent, String ip) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token is required");
        }

        String hash = TokenHasher.sha256(presentedRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        // REUSE DETECTION: revoked or expired token presented → revoke EVERYTHING
        if (existing.isRevoked() || existing.isExpired()) {
            String reason = existing.isRevoked() ? "REUSE_DETECTED" : "EXPIRED_REUSE";
            int revoked = refreshTokenRepository.revokeAllByUserId(
                    existing.getUser().getId(), Instant.now(), reason);
            log.warn("Refresh token reuse detected for user_id={}. Revoked {} token(s). Reason={}",
                    existing.getUser().getId(), revoked, reason);
            throw new InvalidTokenException("Refresh token reuse detected. Please log in again.");
        }

        // Normal rotation
        UserEntity user = existing.getUser();
        // A blocked (disabled) account must not be issued new tokens — otherwise
        // an already-authenticated blocked user would keep rotating their refresh
        // token (valid up to the 30-day refresh TTL) and stay logged in forever,
        // defeating the block. Rejecting here forces a re-authenticate, which
        // login() also blocks (see the isEnabled() guard there).
        if (!user.isEnabled()) {
            throw new org.springframework.security.authentication.DisabledException("Account is disabled");
        }
        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
        existing.setRevokedReason("ROTATED");
        existing.setLastUsedAt(Instant.now());
        refreshTokenRepository.save(existing);

        AuthResponse response = issueNewPair(user, userAgent, ip);

        // Link rotation chain (best-effort; not critical for correctness)
        // We re-query for the latest refresh token to grab its id.
        // For simplicity we skip the replaced_by_id wiring — it remains null.
        return response;
    }

    // ---------------------------------------------------------------------
    // Logout
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public void logout(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            return; // nothing to revoke
        }
        String hash = TokenHasher.sha256(presentedRefreshToken);
        Optional<RefreshToken> opt = refreshTokenRepository.findByTokenHash(hash);
        opt.ifPresent(rt -> {
            rt.setRevoked(true);
            rt.setRevokedAt(Instant.now());
            rt.setRevokedReason("LOGOUT");
            refreshTokenRepository.save(rt);
        });
    }

    // ---------------------------------------------------------------------
    // Forgot password
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest req) {
        String email = req.email().toLowerCase();
        Optional<UserEntity> opt = userRepository.findByEmailIgnoreCase(email);
        if (opt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", email);
            return; // silent — never reveal whether the email is registered
        }
        UserEntity user = opt.get();

        // Invalidate any outstanding reset tokens for this user
        passwordResetTokenRepository.invalidateAllForUser(user.getId());

        String rawToken = SecureTokenGenerator.generate();
        PasswordResetToken entity = PasswordResetToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(rawToken))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .used(false)
                .build();
        passwordResetTokenRepository.save(entity);

        log.info("Password reset token issued for user_id={}", user.getId());
        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
    }

    // ---------------------------------------------------------------------
    // Reset password
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    @CacheEvict(value = {"users", "usersList"}, allEntries = true)
    public void resetPassword(ResetPasswordRequest req) {
        String hash = TokenHasher.sha256(req.token());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));

        if (!token.isUsable()) {
            throw new InvalidTokenException("Invalid or expired reset token");
        }

        UserEntity user = token.getUser();
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        // Invalidate all refresh tokens (force re-login everywhere)
        int revoked = refreshTokenRepository.revokeAllByUserId(
                user.getId(), Instant.now(), "PASSWORD_RESET");
        log.info("Password reset for user_id={}; revoked {} refresh token(s)",
                user.getId(), revoked);
    }

    // ---------------------------------------------------------------------
    // Current user
    // ---------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#email")
    public UserResponse getCurrentUser(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidTokenException("User not found"));
        return withEffectiveUnlocks(user);
    }

    // ---------------------------------------------------------------------
    // Equip cosmetics (validated against the user's unlocked list)
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public UserResponse updateCosmetics(UpdateCosmeticsRequest req, String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        if (req.equippedAvatarId() != null) {
            cosmeticCatalogue.requireUnlocked(user, req.equippedAvatarId());
            user.setEquippedAvatarId(req.equippedAvatarId());
        }
        if (req.equippedBannerId() != null) {
            cosmeticCatalogue.requireUnlocked(user, req.equippedBannerId());
            user.setEquippedBannerId(req.equippedBannerId());
        }
        user = userRepository.save(user);
        log.info("Updated cosmetics for user_id={} avatar={} banner={}",
                user.getId(), user.getEquippedAvatarId(), user.getEquippedBannerId());
        return withEffectiveUnlocks(user);
    }

    /**
     * Build the current-user response with belt-based unlocks computed live (so a
     * cosmetic an admin created for the user's belt shows up immediately, without
     * rewriting the stored {@code unlockedCosmetics} snapshot).
     */
    private UserResponse withEffectiveUnlocks(UserEntity user) {
        return UserResponse.of(user, cosmeticCatalogue);
    }

    // ---------------------------------------------------------------------
    // Email verification (request link)
    //
    // Always silent on missing email — same anti-enumeration shape as
    // requestPasswordReset. If the user is already verified we also stay
    // silent (no harm in resending, but no point either; the only useful
    // state to expose is "does this email exist").
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public void requestEmailVerification(ForgotPasswordRequest req) {
        String email = req.email().toLowerCase();
        Optional<UserEntity> opt = userRepository.findByEmailIgnoreCase(email);
        if (opt.isEmpty()) {
            log.info("Verification requested for non-existent email: {}", email);
            return;
        }
        UserEntity user = opt.get();

        if (user.isEmailVerified()) {
            log.info("Verification requested for already-verified user_id={}", user.getId());
            return;
        }

        // Invalidate any outstanding tokens for this user
        emailVerificationTokenRepository.invalidateAllForUser(user.getId());

        String rawToken = SecureTokenGenerator.generate();
        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(rawToken))
                .expiresAt(Instant.now().plus(props.verificationTtl()))
                .used(false)
                .build());

        log.info("Email verification token issued for user_id={}", user.getId());
        emailService.sendVerificationEmail(user.getEmail(), rawToken);
    }

    // ---------------------------------------------------------------------
    // Email verification (consume link)
    //
    // Validates the token, flips emailVerified + enabled, marks the token
    // used. Does NOT issue auth tokens — the user follows the success
    // message to /login and authenticates normally.
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    @CacheEvict(value = {"users", "usersList", "adminStats"}, allEntries = true)
    public void verifyEmail(String rawToken) {
        String hash = TokenHasher.sha256(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired verification link"));

        if (!token.isUsable()) {
            throw new InvalidTokenException("Invalid or expired verification link");
        }

        UserEntity user = token.getUser();
        user.setEmailVerified(true);
        // enabled was already true by default, but flip it for safety in case
        // an admin disabled the account between signup and verification.
        user.setEnabled(true);
        userRepository.save(user);

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        log.info("Email verified for user_id={} email={}", user.getId(), user.getEmail());
    }

    // ---------------------------------------------------------------------
    // Token-pair issuance
    // ---------------------------------------------------------------------
    private AuthResponse issueNewPair(UserEntity user, String userAgent, String ip) {
        // 1. Access token (stateless JWT)
        JwtService.IssuedAccessToken access = jwtService.issueAccessToken(user);

        // 2. Refresh token (opaque random, hashed in DB)
        String rawRefresh = SecureTokenGenerator.generate();
        RefreshToken refresh = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(rawRefresh))
                .expiresAt(Instant.now().plus(props.jwt().refreshTtl()))
                .userAgent(truncate(userAgent, 500))
                .ipAddress(truncate(ip, 64))
                .revoked(false)
                .build();
        refresh = refreshTokenRepository.save(refresh);
        // We need to return the RAW refresh token (not the hash) so the client
        // can present it on /refresh. We store only the hash; the controller
        // receives the raw token from a separate return path — see AuthController.
        // To keep the AuthResponse free of tokens, we return user + access
        // expiry here, and the controller manages the refresh cookie itself.
        return new AuthResponse(
                UserResponse.of(user),
                access.expiresAt().toString(),
                access.token(),
                rawRefresh);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
