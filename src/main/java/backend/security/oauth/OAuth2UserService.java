package backend.security.oauth;

import backend.user.UserEntity;

/**
 * Strategy interface for processing OAuth2 user profiles.
 * <p>
 * When Google/GitHub/Facebook is added, create a {@code GoogleOAuth2UserService},
 * register it as a Spring bean, and call it from the {@code /api/v1/auth/oauth2/{provider}/callback}
 * endpoint (to be added alongside the {@code spring.security.oauth2.client.registration.google.*} properties).
 * <p>
 * For now, the {@link LocalOAuth2UserService} is the default no-op that simply
 * looks up an existing LOCAL user — no providers are configured.
 */
public interface OAuth2UserService {

    /**
     * Provider name this service handles (e.g. "GOOGLE"). Must match a value of
     * {@link backend.user.Provider}.
     */
    String providerName();

    /**
     * Look up or create a user based on the OAuth2 profile. Implementations
     * should be idempotent: if the user already exists (by provider+providerId
     * or by email), return the existing one.
     */
    UserEntity processOAuthUser(String providerId, String email,
                                String firstName, String lastName);
}
