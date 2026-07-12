# Security Model

This document describes the authentication design and the production
checklist for the Dadi Bulsara backend.

## Token model

We use a **two-token** system:

1. **Access token** — short-lived (15 min) **stateless JWT** signed with HS256.
   - Stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie called `kf_access`.
   - Contains: `sub` (email), `uid`, `role`, `name`, `iss`, `iat`, `exp`, `jti`.
   - Validated on every request via `JwtAuthenticationFilter`.
   - **Not stored** in the DB — there's nothing to revoke for an access token.

2. **Refresh token** — long-lived (30 days) **opaque random string** (43
   base64url chars, 256 bits of entropy).
   - Stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie called `kf_refresh`.
   - **Hashed with SHA-256** before being stored in the `refresh_token` table
     — the raw token never lives in the DB.
   - Can be revoked at any time.

### Why two tokens?

- **Access tokens are short-lived** to limit the window of damage if one is
  stolen (e.g. via XSS that bypasses HttpOnly).
- **Refresh tokens can be revoked** — a stolen refresh token stops working
  the moment we mark it revoked, OR as soon as the legitimate user uses it
  (see *rotation + theft detection* below).
- **Refresh tokens are not JWTs** because we need to be able to revoke
  individual ones; stateless JWTs cannot be revoked without a denylist.

### Rotation + theft detection

Every call to `/api/v1/auth/refresh`:

1. Looks up the presented refresh token's hash.
2. If the token is **revoked or expired**, the user is treated as compromised:
   **every** refresh token for that user is revoked (`REUSE_DETECTED` /
   `EXPIRED_REUSE`), the security event is logged, and the request is
   rejected with 401. The user must log in again.
3. Otherwise, the old token is marked `ROTATED` and a new access + refresh
   pair is issued.

This way, a stolen refresh token stops working the instant the legitimate
user uses it for the first time.

## CSRF

We use Spring's `CookieCsrfTokenRepository` (double-submit cookie pattern):

- On the first response to a browser, the server sets a non-HttpOnly
  `XSRF-TOKEN` cookie.
- The frontend reads it with JS and echoes the value in the
  `X-XSRF-TOKEN` header on every state-changing request.
- Spring compares the header to the cookie. Mismatch → 403.

The `/api/v1/auth/{login,signup,refresh,logout,forgot-password,reset-password,resend-verification}`
endpoints are CSRF-exempt because they have no pre-existing session to
protect; the `refresh` and `logout` endpoints read the `kf_refresh` cookie
itself, which is HttpOnly, so the attacker can't forge it cross-site.

## Cookies

| Cookie       | HttpOnly | Secure (prod) | SameSite | Path |
|--------------|----------|---------------|----------|------|
| `kf_access`  | yes      | yes           | Strict   | `/`  |
| `kf_refresh` | yes      | yes           | Strict   | `/`  |
| `XSRF-TOKEN` | no       | yes           | (Lax)    | `/`  |

`Secure` is controlled by the `COOKIES_SECURE` env var. **Set to `true` in
production.** Local development over plain HTTP requires `false`.

## Password storage

Passwords are hashed with **BCrypt** at cost 12. The cost factor is a
property of the `PasswordEncoder` bean in `SecurityConfig`.

The database **never** contains plain passwords. OAuth-only users have a
`NULL` password column.

## Password reset

- A 32-byte random token is generated, **hashed with SHA-256**, and stored
  in the `password_reset_token` table with a 15-minute expiry.
- The raw token is sent in the password-reset email link
  (`${RESET_BASE_URL}?token=...`).
- The raw token is never stored. A DB compromise doesn't expose valid
  reset tokens.
- The endpoint is rate-limited to **3 requests per minute per IP**.
- Whether the email exists or not, the API always returns the same
  `200 OK` message — no email enumeration.

When a user successfully resets their password, **all** of their refresh
tokens are revoked, forcing re-login on every device.

## Rate limiting

In-memory sliding-window rate limiter (see `common/RateLimiter.java`):

| Endpoint                      | Limit     |
|-------------------------------|-----------|
| `POST /api/v1/auth/login`     | 5 / min / IP |
| `POST /api/v1/auth/forgot-password` | 3 / min / IP |

**For production scale**, swap `RateLimiter` to Bucket4j + Redis. The
controller's `rateLimiter.checkOrThrow(...)` call site stays the same.

## Validation

Request bodies are validated with Jakarta Bean Validation
(`spring-boot-starter-validation`). The `GlobalExceptionHandler` returns
400 with a structured `fieldErrors` array.

Custom `PasswordComplexity` validator enforces: ≥ 8 characters, at least
one letter, at least one digit.

## Error responses

All errors return JSON in a consistent shape:

```json
{
  "timestamp": "2026-07-08T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login",
  "fieldErrors": null
}
```

The `code` field is machine-readable and stable; use it to drive UI
behaviour. Possible codes: `AUTH_REQUIRED`, `AUTH_FAILED`,
`AUTH_INVALID_CREDENTIALS`, `AUTH_INVALID_TOKEN`, `AUTH_TOKEN_EXPIRED`,
`AUTH_ACCESS_DENIED`, `EMAIL_ALREADY_EXISTS`, `RESOURCE_CONFLICT`,
`RATE_LIMIT_EXCEEDED`, `VALIDATION_FAILED`, `INTERNAL_ERROR`.

## Adding an OAuth provider (e.g. Google)

1. Add the `spring-boot-starter-oauth2-client` dependency (alongside the
   existing `-resource-server`).
2. In `application.yaml`:
   ```yaml
   spring.security.oauth2.client.registration.google:
     client-id: ${GOOGLE_CLIENT_ID}
     client-secret: ${GOOGLE_CLIENT_SECRET}
     scope: email, profile
     redirect-uri: "{baseUrl}/api/v1/auth/oauth2/google/callback"
   ```
3. Create `security/oauth/GoogleOAuth2UserService.java` implementing
   `OAuth2UserService` with `providerName() == "GOOGLE"`.
4. Add `/api/v1/auth/oauth2/google/callback` to `AuthController` that
   delegates to the service, then calls `authService.issueNewPair(...)`
   to mint our own tokens.
5. The `UserEntity.provider` and `providerId` columns already exist and
   are used for lookup.

## Production checklist

- [ ] `JWT_SECRET` set to a 32-byte random value (NOT the default in
      `application.yaml`).
- [ ] `COOKIES_SECURE=true` (requires HTTPS).
- [ ] `CORS_ORIGINS` restricted to the real frontend domain.
- [ ] SMTP credentials configured (`MAIL_*`).
- [ ] PostgreSQL connection uses a low-privilege user, not `postgres`.
- [ ] Rate limiter swapped to Bucket4j + Redis.
- [ ] Flyway migrations replace `ddl-auto=update`.
- [ ] TLS terminated at the proxy (nginx, Cloudflare, etc.).
- [ ] Logs scrubbed of PII (the IP/UA capture in `refresh_token` is fine
      for abuse investigations, but should be auto-purged per the privacy
      policy).
