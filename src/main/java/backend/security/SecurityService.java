package backend.security;

import backend.user.Role;
import backend.user.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resolves the currently authenticated {@link UserEntity} from the Spring
 * {@code SecurityContext}.
 *
 * <p>The {@link JwtAuthenticationFilter} sets the {@code UserEntity} itself as the
 * {@code Authentication} principal, so we can read {@code role} and
 * {@code managedState} directly — no extra DB hit, and no JWT changes needed.
 *
 * <p>{@link #scope()} collapses the caller's role + (for a sub-admin) managed state
 * into a single object the admin services use to decide what data is visible.
 */
@Service
public class SecurityService {

    /**
     * The administrative scope of the caller.
     *
     * @param role  the caller's role
     * @param state the state a sub-admin is bound to; {@code null} for a global ADMIN
     */
    public record AdminScope(Role role, String state) {
        public boolean isSubAdmin() {
            return role == Role.SUB_ADMIN;
        }
    }

    /** The authenticated user, or {@code null} if there is no session. */
    public UserEntity getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof UserEntity u ? u : null;
    }

    /** The authenticated user, throwing if the request is not authed. */
    public UserEntity requireCurrentUser() {
        UserEntity u = getCurrentUserOrNull();
        if (u == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authentication required");
        }
        return u;
    }

    /**
     * The caller's admin scope. For a SUB_ADMIN the state is their managed state;
     * for ADMIN it is {@code null} (global).
     */
    public AdminScope scope() {
        UserEntity u = requireCurrentUser();
        String state = u.getRole() == Role.SUB_ADMIN ? u.getManagedState() : null;
        return new AdminScope(u.getRole(), state);
    }
}
