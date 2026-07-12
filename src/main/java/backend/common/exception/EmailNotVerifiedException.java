package backend.common.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown by {@code AuthService.login} when the user exists, has the right
 * password, but hasn't yet verified their email address. Subclasses
 * {@link AuthenticationException} so Spring Security treats it as a
 * normal auth failure (401) — but the global handler maps it to a
 * distinct error code so the frontend can show a "please verify" message
 * instead of "wrong password".
 */
public class EmailNotVerifiedException extends AuthenticationException {
    public EmailNotVerifiedException(String email) {
        super("Email is not verified: " + email);
    }
}
