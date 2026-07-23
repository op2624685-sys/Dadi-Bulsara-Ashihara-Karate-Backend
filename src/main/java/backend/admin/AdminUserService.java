package backend.admin;

import backend.admin.dto.AdminUserSummaryResponse;
import backend.admin.dto.RoleChangeRequest;
import backend.admin.dto.SubAdminCreateRequest;
import backend.common.exception.EmailAlreadyExistsException;
import backend.common.exception.UserNotFoundException;
import backend.security.SecurityService;
import backend.teacher.dto.PageResponse;
import backend.user.CosmeticCatalogue;
import backend.user.Provider;
import backend.user.Role;
import backend.user.UserEntity;
import backend.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;


import java.util.ArrayList;
import java.util.List;

/**
 * ADMIN-only user management: listing accounts, creating state-scoped
 * sub-admins, and promoting/demoting roles.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityService securityService;
    private final CosmeticCatalogue cosmeticCatalogue;

    // ── List ──────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    @Cacheable(value = "usersList", key = "(#search != null ? #search : '') + '-' + (#role != null ? #role.name() : '') + '-' + (#state != null ? #state : '') + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public PageResponse<AdminUserSummaryResponse> listUsers(
            String search, Role role, String state, Pageable pageable) {

        // A SUB_ADMIN is scoped to their own state — ignore any state they pass
        // and force the filter to their managedState instead.
        UserEntity caller = securityService.getCurrentUserOrNull();
        final String effectiveState = (caller != null && caller.getRole() == Role.SUB_ADMIN)
                ? caller.getManagedState()
                : state;

        Specification<UserEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (isNotBlank(effectiveState)) {
                predicates.add(cb.equal(cb.lower(root.get("state")), effectiveState.toLowerCase()));
            }
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like)));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UserEntity> page = userRepository.findAll(spec, pageable);
        return PageResponse.of(page, AdminUserSummaryResponse::of);
    }

    // ── Create sub-admin ────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = {"usersList", "adminStats"}, allEntries = true)
    public AdminUserSummaryResponse createSubAdmin(SubAdminCreateRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        UserEntity sub = UserEntity.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .firstName(req.firstName().trim())
                .lastName(req.lastName().trim())
                .role(Role.SUB_ADMIN)
                .managedState(req.state().trim())
                .provider(Provider.LOCAL)
                .enabled(true)
                .emailVerified(true) // admin-created, no email verification step
                .unlockedCosmetics(cosmeticCatalogue.allIds()) // staff unlock everything
                .build();
        sub = userRepository.save(sub);
        log.info("Created SUB_ADMIN id={} email={} state={}", sub.getId(), email, sub.getManagedState());
        return AdminUserSummaryResponse.of(sub);
    }

    // ── Change role ─────────────────────────────────────────────────────────────
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = {"usersList", "adminStats", "users"}, allEntries = true)
    })
    public AdminUserSummaryResponse changeRole(Long id, RoleChangeRequest req) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        // Guard: an admin cannot demote/lock themselves out accidentally via this path.
        UserEntity caller = securityService.getCurrentUserOrNull();
        if (caller != null && caller.getId().equals(id)
                && req.role() != Role.ADMIN) {
            throw new IllegalArgumentException("You cannot change your own role");
        }

        if (req.role() == Role.SUB_ADMIN) {
            if (req.state() == null || req.state().isBlank()) {
                throw new IllegalArgumentException("A SUB_ADMIN requires a managed state");
            }
            user.setManagedState(req.state().trim());
        } else {
            user.setManagedState(null);
        }
        user.setRole(req.role());
        // Staff roles unlock every cosmetic (no belt → no partial unlocks).
        if (req.role() == Role.ADMIN || req.role() == Role.SUB_ADMIN) {
            user.setUnlockedCosmetics(cosmeticCatalogue.allIds());
        }
        user = userRepository.save(user);
        log.info("Changed role for user_id={} to {} (state={})", id, req.role(), user.getManagedState());
        return AdminUserSummaryResponse.of(user);
    }

    // ── Block / Unblock ────────────────────────────────────────────────────────
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = {"usersList", "adminStats", "users"}, allEntries = true)
    })
    public AdminUserSummaryResponse setBlocked(Long id, boolean blocked) {
        UserEntity caller = securityService.getCurrentUserOrNull();
        if (caller == null
                || (caller.getRole() != Role.ADMIN && caller.getRole() != Role.SUB_ADMIN)) {
            throw new AccessDeniedException("Only an ADMIN or SUB_ADMIN may manage users");
        }

        UserEntity target = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        // Guard: an admin/sub-admin cannot lift the ban on (or ban) themselves.
        if (caller.getId().equals(target.getId())) {
            throw new IllegalArgumentException("You cannot block or unblock yourself");
        }

        // Guard: never block an ADMIN account — that could lock out the system.
        if (target.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("ADMIN accounts cannot be blocked");
        }

        // SUB_ADMIN is scoped to their own state.
        if (caller.getRole() == Role.SUB_ADMIN) {
            if (target.getState() == null || caller.getManagedState() == null
                    || !target.getState().equalsIgnoreCase(caller.getManagedState())) {
                throw new AccessDeniedException(
                        "Sub-admins may only manage users in state: " + caller.getManagedState());
            }
        }

        target.setBlocked(blocked);
        target = userRepository.save(target);
        log.info("{} user_id={} (state={})", blocked ? "Blocked" : "Unblocked", id, target.getState());
        return AdminUserSummaryResponse.of(target);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
