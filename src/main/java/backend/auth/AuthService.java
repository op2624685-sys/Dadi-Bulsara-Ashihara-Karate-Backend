package backend.auth;

import backend.auth.dto.*;
import backend.user.dto.UserResponse;

public interface AuthService {

    /**
     * Creates a new LOCAL user (emailVerified=false, enabled=true — a
     * separate check at login time blocks sign-in until verification is
     * complete) and emails them a one-time verification link. Does NOT
     * issue auth tokens — the user is not logged in until they click the
     * link and {@link #verifyEmail(String)} flips their flags.
     *
     * @throws backend.common.exception.EmailAlreadyExistsException if the email is taken
     */
    MessageResponse signup(SignupRequest request, String userAgent, String ip);

    /**
     * Authenticates the user with email + password, then issues a fresh
     * token pair. Rejects unverified accounts with
     * {@link backend.common.exception.EmailNotVerifiedException}.
     */
    AuthResponse login(LoginRequest request, String userAgent, String ip);

    /**
     * Rotates the refresh token. Old token is revoked; new pair is issued.
     * If the presented refresh token is already revoked or expired, ALL
     * refresh tokens for that user are revoked (token-theft mitigation).
     */
    AuthResponse refresh(String presentedRefreshToken, String userAgent, String ip);

    /**
     * Revokes the presented refresh token (best-effort) and returns a
     * message. The controller will clear cookies in the response.
     */
    void logout(String presentedRefreshToken);

    /**
     * Always returns the same MessageResponse regardless of whether the
     * email exists, to prevent email enumeration. Sends a reset link via
     * email if the user is found.
     */
    void requestPasswordReset(ForgotPasswordRequest request);

    /**
     * Validates the token, updates the user's password, marks the token
     * used, and revokes all refresh tokens for that user.
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Sends (or re-sends) a verification link to a freshly signed-up user.
     * Silent on missing email — same anti-enumeration shape as
     * {@link #requestPasswordReset(ForgotPasswordRequest)}.
     */
    void requestEmailVerification(ForgotPasswordRequest request);

    /**
     * Validates the one-time token, flips the user's emailVerified +
     * enabled flags, and marks the token used. Does NOT issue auth tokens
     * — the user follows the success message to /login.
     *
     * @throws backend.common.exception.InvalidTokenException on bad/expired/already-used token
     */
    void verifyEmail(String rawToken);

    /**
     * Returns the UserResponse for the currently authenticated user.
     */
    UserResponse getCurrentUser(String email);

    /**
     * Equips cosmetics on the current user's account. Each non-null id is
     * validated against the user's {@code unlockedCosmetics} list; an id the
     * user has not unlocked is rejected. A null field is left unchanged.
     */
    UserResponse updateCosmetics(UpdateCosmeticsRequest request, String email);
}
