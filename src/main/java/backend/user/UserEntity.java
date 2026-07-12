package backend.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "app_user",
    indexes = {
        @Index(name = "idx_app_user_email", columnList = "email"),
        @Index(name = "idx_app_user_provider", columnList = "provider, provider_id")
    }
)
public class UserEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    /**
     * Null for OAuth-only users. BCrypt-hashed for LOCAL users.
     */
    @Column
    private String password;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Provider provider = Provider.LOCAL;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    /**
     * The single Indian state a {@link Role#SUB_ADMIN} is responsible for.
     * Null for every other role. A sub-admin's teacher-admin actions are
     * scoped to this state (see SecurityService / TeacherServiceImpl).
     */
    @Column(name = "managed_state", length = 80)
    private String managedState;

    /**
     * The avatar the user has equipped. Null → the role-default look
     * (belt for students, dan for teachers, plain for staff). Drives the
     * navbar, the public profile, and the directory cards for every role.
     */
    @Column(name = "equipped_avatar_id", length = 64)
    private String equippedAvatarId;

    /** The banner the user has equipped. Null → role-default banner. */
    @Column(name = "equipped_banner_id", length = 64)
    private String equippedBannerId;

    /**
     * Cosmetic ids this user is allowed to equip. Seeded from belt rank
     * (student/teacher record) or all-unlocked for staff, and extended later
     * by events/achievements. The equip endpoint validates against this list.
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unlocked_cosmetics", columnDefinition = "jsonb")
    private List<String> unlockedCosmetics = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // ROLE_ prefix is required by Spring Security's hasRole(...) expression
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
