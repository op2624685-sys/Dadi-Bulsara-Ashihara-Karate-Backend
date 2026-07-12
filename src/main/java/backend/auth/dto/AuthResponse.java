package backend.auth.dto;

import backend.user.dto.UserResponse;

/**
 * Internal carrier from AuthService → AuthController. Contains the raw
 * access + refresh tokens so the controller can attach them as HttpOnly
 * cookies. The body of the HTTP response (after the controller strips the
 * token fields) contains only the user profile and access-token expiry.
 */
public record AuthResponse(
        UserResponse user,
        String accessTokenExpiresAt,
        String accessToken,
        String refreshToken
) {}
