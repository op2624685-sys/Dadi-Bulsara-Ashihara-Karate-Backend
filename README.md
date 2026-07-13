# Karate — Backend

Production-grade Spring Boot 4.1 / Java 21 REST API with JWT + refresh-token
authentication. Built for the Dadi Bulsara Next.js frontend.

## Quick start

### 1. Prerequisites

- Java 21
- Maven (or use the included `mvnw`)
- PostgreSQL 14+ running locally on port 5432
- (Optional) An SMTP server or [MailHog](https://github.com/mailhog/MailHog) for dev email testing

### 2. Create the database

```sql
CREATE DATABASE karate;
```

The user/password in the example default to `postgres / postgres`.

### 3. Set environment variables

Copy `.env.example` to `.env` (or set the variables in your IDE run config):

```bash
cp .env.example .env
```

At minimum, set `JWT_SECRET` to a 32-byte random string. Generate one with:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

### 4. Run the application

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. On first launch, Hibernate will
auto-create the `app_user`, `refresh_token`, and `password_reset_token`
tables in PostgreSQL (`spring.jpa.hibernate.ddl-auto=update`).

### 5. Try it out

- **Swagger UI**: <http://localhost:8080/swagger-ui.html>
- **OpenAPI JSON**: <http://localhost:8080/v3/api-docs>
- **Health**: <http://localhost:8080/actuator/health>

## Authentication endpoints

| Method | Path                          | Auth required | Description |
|--------|-------------------------------|---------------|-------------|
| POST   | `/api/v1/auth/signup`         | no            | Create account, returns cookies |
| POST   | `/api/v1/auth/login`          | no            | Email + password → cookies |
| POST   | `/api/v1/auth/refresh`        | refresh cookie | Rotate tokens (CSRF required) |
| POST   | `/api/v1/auth/logout`         | refresh cookie | Revoke + clear cookies (CSRF required) |
| POST   | `/api/v1/auth/forgot-password`| no            | Send reset link (rate-limited 3/min) |
| POST   | `/api/v1/auth/reset-password` | no            | Submit new password with token |
| GET    | `/api/v1/auth/me`             | access cookie | Current user profile |

All state-changing endpoints (POST) require a `X-XSRF-TOKEN` header matching
the `XSRF-TOKEN` cookie value, **except** for the auth endpoints themselves
(`/api/v1/auth/login`, `/signup`, `/refresh`, `/forgot-password`,
`/reset-password`) which are CSRF-exempt.

## Cookies set by the API

| Cookie       | HttpOnly | Secure (prod) | SameSite | Path | Max-Age |
|--------------|----------|---------------|----------|------|---------|
| `kf_access`  | yes      | yes           | Strict   | `/`  | 15 min  |
| `kf_refresh` | yes      | yes           | Strict   | `/`  | 30 days |
| `XSRF-TOKEN` | no       | yes           | (Lax default) | `/` | session |

`Secure` is controlled by `COOKIES_SECURE` (set to `true` behind HTTPS).

## Curl smoke test

```bash
# 1. Sign up
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"firstName":"Test","lastName":"User","email":"test@example.com","password":"Pass1234"}'

# 2. Log in
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"test@example.com","password":"Pass1234"}'

# 3. Hit an authenticated endpoint
curl -b cookies.txt http://localhost:8080/api/v1/auth/me

# 4. Refresh (rotates both tokens)
curl -X POST -b cookies.txt -c cookies.txt \
  -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN cookies.txt | awk '{print $7}')" \
  http://localhost:8080/api/v1/auth/refresh

# 5. Log out
curl -X POST -b cookies.txt -c /dev/null \
  -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN cookies.txt | awk '{print $7}')" \
  http://localhost:8080/api/v1/auth/logout
```

## Architecture

```
backend/
├── pom.xml
├── src/main/java/backend/
│   ├── KarateApplication.java
│   ├── auth/
│   │   ├── AuthController.java          # /api/v1/auth/* endpoints
│   │   ├── AuthService.java + Impl
│   │   ├── EmailService.java            # password-reset emails
│   │   ├── RefreshToken.java + Repository
│   │   ├── PasswordResetToken.java + Repository
│   │   └── dto/                         # SignupRequest, LoginRequest, ...
│   ├── user/
│   │   ├── UserEntity.java              # @Table("app_user") — JPA entity
│   │   ├── UserRepository.java
│   │   ├── Role.java + Provider.java
│   │   └── dto/UserResponse.java
│   ├── security/
│   │   ├── SecurityConfig.java          # main filter chain
│   │   ├── JwtService.java              # Nimbus JOSE+JWT wrapper
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── CustomUserDetailsService.java
│   │   ├── CookieService.java
│   │   ├── AuthEntryPoint.java          # JSON 401
│   │   ├── AccessDeniedHandlerImpl.java # JSON 403
│   │   └── oauth/                       # extension points for Google/GitHub
│   ├── common/
│   │   ├── ApiError.java + GlobalExceptionHandler
│   │   ├── RateLimiter.java             # in-memory sliding window
│   │   ├── SecureTokenGenerator.java    # 32-byte URL-safe tokens
│   │   ├── TokenHasher.java             # SHA-256 helper
│   │   ├── exception/                   # custom exceptions
│   │   └── validation/                  # @PasswordComplexity
│   └── config/
│       ├── ApplicationProperties.java   # @ConfigurationProperties("app")
│       └── CorsConfig.java
└── src/main/resources/application.yaml
```

## Features

- **Authentication & authorization** — stateless JWT access tokens plus opaque
  refresh tokens with rotation and reuse detection, email verification, and
  rate-limited password reset (`/api/v1/auth/*`).
- **Student management** — registration, public/authenticated profiles, status
  lifecycle, and teacher/admin-scoped views.
- **Teacher management** — teacher CRUD, profiles, reviews, timeline entries, and
  admin oversight.
- **Admin & roles** — admin/sub-admin user management, role promotion/demotion,
  and aggregate statistics.
- **Security hardening** — CSRF double-submit cookies, BCrypt password hashing,
  in-memory rate limiting, and centralised JSON error handling.

## Resource API (non-auth)

The auth endpoints are documented above. The remaining resources are grouped by
base path:

| Module   | Base path                  | Access                                     |
|----------|----------------------------|--------------------------------------------|
| Students | `/api/v1/students`         | public reads; `register` & `me` authenticated |
|          | `/api/v1/teacher/students` | `TEACHER`                                  |
|          | `/api/v1/admin/students`   | `ADMIN`, `SUB_ADMIN`                       |
| Teachers | `/api/v1/teachers`         | public reads; `register` open              |
|          | `/api/v1/admin/teachers`   | `ADMIN`                                    |
| Admin    | `/api/v1/admin`            | `ADMIN`, `SUB_ADMIN` (user mgmt + stats)   |

All create/update endpoints require the `X-XSRF-TOKEN` header (see above). For
the full request/response schema of every endpoint, open **Swagger UI** at
<http://localhost:8080/swagger-ui.html>.

## See also

- [SECURITY.md](./SECURITY.md) — token model, security trade-offs, prod checklist
