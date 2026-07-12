package backend.security.oauth;

import backend.user.Provider;
import backend.user.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Default OAuth2 service for the LOCAL provider. Returns null — there is no
 * real OAuth flow for local users (they sign up via /api/v1/auth/signup).
 * <p>
 * This class exists to document the extension point and to be the single
 * registration target when real providers are added.
 * <p>
 * To add Google: <br>
 * 1. Add to application.yaml:
 *    <pre>
 *    spring.security.oauth2.client.registration.google:
 *      client-id: ${GOOGLE_CLIENT_ID}
 *      client-secret: ${GOOGLE_CLIENT_SECRET}
 *      scope: email, profile
 *    </pre>
 * 2. Create {@code GoogleOAuth2UserService implements OAuth2UserService} with
 *    providerName() returning "GOOGLE".
 * 3. Add a {@code ClientRegistrationRepository} bean.
 * 4. Add a {@code /api/v1/auth/oauth2/google/callback} endpoint that calls
 *    the service, then mints our own access + refresh tokens via AuthService.
 */
@Service
@RequiredArgsConstructor
public class LocalOAuth2UserService implements OAuth2UserService {

    @Override
    public String providerName() {
        return Provider.LOCAL.name();
    }

    @Override
    public UserEntity processOAuthUser(String providerId, String email,
                                       String firstName, String lastName) {
        // Not used — LOCAL users go through /signup, not OAuth.
        throw new UnsupportedOperationException(
                "LOCAL provider does not support OAuth processing — use /api/v1/auth/signup");
    }
}
