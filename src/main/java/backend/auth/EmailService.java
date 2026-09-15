package backend.auth;

import backend.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends transactional email via the Resend HTTP API
 * ({@code https://api.resend.com/emails}, port 443). HTTP-based delivery is
 * used instead of SMTP because outbound port 465 is blocked on Render's AWS
 * egress, which made JavaMailSender unreliable in production. Failures are
 * logged with the link inline so the reset/verify URL is still obtainable
 * during development or a Resend outage.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";

    private final ApplicationProperties props;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${RESEND_MAIL_API_KEY:}")
    private String resendApiKey;

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
        String htmlBody = """
                <div style="font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #333;">Reset Your Password</h2>
                    <p>Hi,</p>
                    <p>We received a request to reset your password. Click the link below within the next 15 minutes to set a new password:</p>
                    <p><a href="%s" style="background-color: #d32f2f; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block;">Reset Password</a></p>
                    <p>If you did not request this, you can safely ignore this email.</p>
                    <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                    <p style="font-size: 0.9em; color: #666;">— Dadi Bulsara Ashihara Karate Federation</p>
                </div>
                """.formatted(link);

        try {
            sendViaResend(to, subject, body, htmlBody);
            log.info("Password-reset email sent to {}", to);
        } catch (Exception ex) {
            // SMTP-style dev fallback: log the link so devs can still complete the flow
            log.warn("[DEV] Could not send password-reset email ({}). Reset link: {}",
                    ex.getMessage(), link);
        }
    }

    /**
     * Sends the email-verification link for a freshly signed-up account.
     * 15min validity — matches the verification-token TTL in AuthServiceImpl.
     * Mirrors the password-reset pattern: @Async, Resend-with-dev-fallback,
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
        String htmlBody = """
                <div style="font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #d32f2f;">Verify Your Account</h2>
                    <p>Hi,</p>
                    <p>Welcome to the Dadi Bulsara Ashihara Karate Federation!</p>
                    <p>Click the link below within the next 15 minutes to verify your email and activate your account:</p>
                    <p><a href="%s" style="background-color: #d32f2f; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block;">Verify Email</a></p>
                    <p>If you did not sign up, you can safely ignore this email.</p>
                    <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                    <p style="font-size: 0.9em; color: #666;">— Dadi Bulsara Ashihara Karate Federation</p>
                </div>
                """.formatted(link);

        try {
            sendViaResend(to, subject, body, htmlBody);
            log.info("Verification email sent to {}", to);
        } catch (Exception ex) {
            log.warn("[DEV] Could not send verification email ({}). Verification link: {}",
                    ex.getMessage(), link);
        }
    }

    /**
     * Sends a registration application submission email.
     */
    @Async
    public void sendApplicationSubmittedEmail(String to, String role) {
        String roleLabel = role.equalsIgnoreCase("TEACHER") ? "Teacher/Sensei" : "Student";
        String subject = "Application Received - Dadi Bulsara";
        String body = "Hi,\n\nWe have received your application for " + roleLabel + " membership. Our team will review it and get back to you soon.\n\n— Dadi Bulsara";
        String htmlBody = """
                <div style="font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #d32f2f;">Application Received!</h2>
                    <p>Hi,</p>
                    <p>We have received your application for <strong>%s</strong> membership at the Dadi Bulsara Ashihara Karate Federation.</p>
                    <p>Our team is currently reviewing your details. We will notify you via email once the process is complete.</p>
                    <p>Thank you for your patience!</p>
                    <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                    <p style="font-size: 0.9em; color: #666;">— Dadi Bulsara Ashihara Karate Federation</p>
                </div>
                """.formatted(roleLabel);

        try {
            sendViaResend(to, subject, body, htmlBody);
            log.info("Application submitted email sent to {}", to);
        } catch (Exception ex) {
            log.warn("[DEV] Could not send application submitted email ({}). To: {}", ex.getMessage(), to);
        }
    }

    /**
     * Sends a registration approval email.
     */
    @Async
    public void sendRegistrationApprovedEmail(String to, String role) {
        String roleLabel = role.equalsIgnoreCase("TEACHER") ? "Teacher/Sensei" : "Student";
        String subject = "Registration Approved - Dadi Bulsara";
        String body = "Congratulations!\n\nYour registration as a " + roleLabel + " has been approved. Your account is now fully active.\n\nWelcome to the federation!\n\n— Dadi Bulsara";
        String htmlBody = """
                <div style="font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #2e7d32;">Congratulations! 🎉</h2>
                    <p>Hi,</p>
                    <p>We are happy to inform you that your registration as a <strong>%s</strong> has been officially approved!</p>
                    <p>Your account is now fully active, and you can now access all the membership benefits of the Dadi Bulsara Ashihara Karate Federation.</p>
                    <p>Welcome to the family!</p>
                    <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                    <p style="font-size: 0.9em; color: #666;">— Dadi Bulsara Ashihara Karate Federation</p>
                </div>
                """.formatted(roleLabel);

        try {
            sendViaResend(to, subject, body, htmlBody);
            log.info("Registration approved email sent to {}", to);
        } catch (Exception ex) {
            log.warn("[DEV] Could not send registration approved email ({}). To: {}", ex.getMessage(), to);
        }
    }

    /**
     * POSTs the email to Resend's /emails endpoint. Resend returns 200 on
     * accept with a JSON {@code {"id": "..."}} body; we treat anything outside
     * [200, 300) as a failure so the caller's catch-block logs the cause.
     */
    private void sendViaResend(String to, String subject, String textBody, String htmlBody) throws Exception {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException(
                    "RESEND_MAIL_API_KEY is not configured (set it in .env / Render dashboard)");
        }

        String payload = """
                {
                  "from": %s,
                  "to": [%s],
                  "subject": %s,
                  "text": %s,
                  "html": %s
                }
                """.formatted(jsonString(props.mail().from()),
                              jsonString(to),
                              jsonString(subject),
                              jsonString(textBody),
                              jsonString(htmlBody));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_EMAILS_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Resend returned HTTP " + resp.statusCode()
                    + ": " + truncate(resp.body(), 500));
        }
    }

    /**
     * Minimal JSON string escape: backslash, double-quote, control chars, and
     * the common unicode ranges we use in subject + body text. Avoids pulling
     * in a JSON library for this single use-site.
     */
    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}