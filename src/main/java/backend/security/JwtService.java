package backend.security;

import backend.common.exception.InvalidTokenException;
import backend.common.exception.TokenExpiredException;
import backend.config.ApplicationProperties;
import backend.user.UserEntity;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates short-lived access tokens (HS256 / MAC).
 * Uses Nimbus JOSE+JWT (transitive via spring-boot-starter-oauth2-resource-server).
 *
 * <p>Refresh tokens are NOT JWTs — they are opaque random strings stored hashed
 * in the DB; see {@code RefreshTokenService}.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final ApplicationProperties props;

    private JWSSigner signer;
    private JWSVerifier verifier;

    @PostConstruct
    void init() {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes (256 bits) for HS256. "
                    + "Set JWT_SECRET env var to a strong random string.");
        }
        try {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            this.signer = new MACSigner(keyBytes);
            this.verifier = new MACVerifier(keyBytes);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to initialise JWT signer/verifier", e);
        }
        log.info("JwtService initialised (issuer={}, access-ttl={}, refresh-ttl={})",
                props.jwt().issuer(), props.jwt().accessTtl(), props.jwt().refreshTtl());
    }

    public IssuedAccessToken issueAccessToken(UserEntity user) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plus(props.jwt().accessTtl());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getEmail())
                    .issuer(props.jwt().issuer())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim("uid", user.getId())
                    .claim("role", user.getRole().name())
                    .claim("name", user.getFirstName() + " " + user.getLastName())
                    .build();

            SignedJWT signedJwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claims);
            signedJwt.sign(signer);
            return new IssuedAccessToken(signedJwt.serialize(), exp);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
    }

    /**
     * Parse + verify signature + check exp/nbf. Throws
     * {@link TokenExpiredException} if expired, {@link InvalidTokenException} otherwise.
     */
    public JWTClaimsSet parseAndValidate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                throw new InvalidTokenException("Invalid token signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                throw new TokenExpiredException("Access token has expired");
            }
            return claims;
        } catch (ParseException e) {
            throw new InvalidTokenException("Malformed token", e);
        } catch (JOSEException e) {
            throw new InvalidTokenException("Token validation failed", e);
        }
    }

    public Instant getAccessTokenExpiry() {
        return Instant.now().plus(props.jwt().accessTtl());
    }

    public record IssuedAccessToken(String token, Instant expiresAt) {}
}
