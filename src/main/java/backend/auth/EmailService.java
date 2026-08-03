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
 * Sends transactional email via Gmail SMTP (sender credentials configured under
 * {@code spring.mail.*} in application.yaml). STARTTLS on port 587 — Render's
 * AWS egress blocks SMTPS port 465, so 465 is not a fallback. Failures are
 * logged with the link inline so the reset/verify URL is still obtainable
 * during development or a Gmail outage (new sign-in location can also trigger
 * Google's security alert — see Gmail docs).
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

        sendOrLog(to, subject, body, link, "password-reset");
    }

    /**
     * Sends the email-verification link for a freshly signed-up account.
     * 15min validity — matches the verification-token TTL in AuthServiceImpl.
     * Mirrors the password-reset pattern: @Async, log-on-failure with link
     * inline so the dev flow is still end-to-end testable.
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

        sendOrLog(to, subject, body, link, "verification");
    }

    private void sendOrLog(String to, String subject, String body, String link, String kind) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mail().from());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("{} email sent to {}", kind, to);
        } catch (Exception ex) {
            log.warn("[DEV] Could not send {} email ({}). {} link: {}",
                    kind, ex.getMessage(), capitalize(kind), link);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
