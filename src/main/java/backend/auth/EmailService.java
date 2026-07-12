package backend.auth;

import backend.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email. In dev (or when SMTP is unreachable), the
 * message body is logged to the console so the reset link is still
 * obtainable during development.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final ApplicationProperties props;

    /**
     * Sends a password-reset email asynchronously. Failures are logged but
     * do not propagate — the forgot-password endpoint should always return
     * 200 to the caller to avoid leaking whether an email exists.
     */
    @Async
    public void sendPasswordResetEmail(String to, String rawToken) {
        String link = props.mail().resetBaseUrl() + "?token=" + rawToken;
        String subject = "Reset your Dadi Bulsara password";
        String body = """
                Hi,

                We received a request to reset your password.
                Click the link below within the next 15 minutes to set a new password:

                %s

                If you did not request this, you can safely ignore this email.

                — Dadi Bulsara
                """.formatted(link);

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mail().from());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Password-reset email sent to {}", to);
        } catch (Exception ex) {
            // SMTP not configured / unreachable in dev — log the link instead
            log.warn("[DEV] Could not send password-reset email ({}). Reset link: {}",
                    ex.getMessage(), link);
        }
    }

    /**
     * Sends the email-verification link for a freshly signed-up account.
     * 15min validity — matches the verification-token TTL in AuthServiceImpl.
     * Mirrors the password-reset pattern: @Async, SMTP-with-dev-fallback,
     * link logged on failure.
     */
    @Async
    public void sendVerificationEmail(String to, String rawToken) {
        String link = props.mail().verifyBaseUrl() + "?token=" + rawToken;
        String subject = "Verify your Dadi Bulsara account";
        String body = """
                Hi,

                Welcome to the Dadi Bulsara Ashihara Karate Federation.
                Click the link below within the next 15 minutes to verify your email
                and activate your account:

                %s

                If you did not sign up, you can safely ignore this email.

                — Dadi Bulsara
                """.formatted(link);

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mail().from());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Verification email sent to {}", to);
        } catch (Exception ex) {
            log.warn("[DEV] Could not send verification email ({}). Verification link: {}",
                    ex.getMessage(), link);
        }
    }
}
