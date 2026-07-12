package backend.common;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically random opaque tokens (used for refresh tokens and
 * password-reset tokens). 32 bytes → 256 bits of entropy → 43 base64url chars.
 */
public final class SecureTokenGenerator {

    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokenGenerator() {}

    public static String generate() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return URL_ENCODER.encodeToString(buf);
    }
}
