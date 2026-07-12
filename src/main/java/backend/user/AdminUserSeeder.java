package backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import backend.user.CosmeticCatalogue;

/**
 * Provisions a federation administrator on a fresh database so the admin
 * teacher-approval console is actually reachable. The seeded account is
 * email-verified and enabled (it can log in immediately).
 *
 * <p>Credentials come from {@code ADMIN_SEED_EMAIL} / {@code ADMIN_SEED_PASSWORD}
 * (or {@code app.admin.*} in application.yaml); sensible dev defaults apply
 * when those are absent. <b>Rotate / remove this in production</b> — it exists
 * only so the gated admin API isn't unreachable during development.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed-email:admin@dadibulsara.local}")
    private String seedEmail;

    @Value("${app.admin.seed-password:ChangeMe123!}")
    private String seedPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(seedEmail)) {
            log.info("Admin user already present ({}); skipping seed.", seedEmail);
            return;
        }

        UserEntity admin = UserEntity.builder()
                .email(seedEmail.toLowerCase().trim())
                .password(passwordEncoder.encode(seedPassword))
                .firstName("Admin")
                .lastName("Federation")
                .role(Role.ADMIN)
                .provider(Provider.LOCAL)
                .enabled(true)
                .emailVerified(true)
                .unlockedCosmetics(CosmeticCatalogue.allIds()) // staff unlock everything
                .build();
        userRepository.save(admin);

        log.warn(
                "Seeded admin user  email='{}'  password='{}'  — CHANGE THIS IN PRODUCTION",
                seedEmail, seedPassword);

        seedSubAdmin();
    }

    /**
     * Provisions a demonstration state-scoped sub-admin (Kerala) so the
     * state-scoped teacher-approval flow is reachable during development.
     * Override/remove in production.
     */
    private void seedSubAdmin() {
        final String subEmail = "subadmin@dadibulsara.local";
        if (userRepository.existsByEmailIgnoreCase(subEmail)) {
            log.info("Sub-admin already present ({}); skipping seed.", subEmail);
            return;
        }
        UserEntity sub = UserEntity.builder()
                .email(subEmail.toLowerCase().trim())
                .password(passwordEncoder.encode("SubAdmin123!"))
                .firstName("Kerala")
                .lastName("Coordinator")
                .role(Role.SUB_ADMIN)
                .managedState("Kerala")
                .provider(Provider.LOCAL)
                .enabled(true)
                .emailVerified(true)
                .unlockedCosmetics(CosmeticCatalogue.allIds()) // staff unlock everything
                .build();
        userRepository.save(sub);
        log.warn(
                "Seeded sub-admin user  email='{}'  password='{}'  state='{}'  — CHANGE THIS IN PRODUCTION",
                subEmail, "SubAdmin123!", sub.getManagedState());
    }
}
