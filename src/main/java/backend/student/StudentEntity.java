package backend.student;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A federation student membership application / directory record.
 *
 * <p><b>No N+1 by construction</b> — same design as {@code TeacherEntity}: every
 * field a student needs (including the {@code achievements} jsonb list) lives in
 * a single row, so listing or loading a student is always exactly one SQL
 * statement. There are no {@code @OneToMany} relations here.
 *
 * <p><b>Application vs directory.</b> A record starts as {@code PENDING} (a user
 * applied and chose a sensei). It only appears in the public {@code /students}
 * directory once a sensei approves it ({@code APPROVED}); a {@code REJECTED}
 * record is hidden from the public. The {@code userId} column links the
 * application to the applicant's {@link backend.user.UserEntity} account so we
 * can promote them to the {@code STUDENT} role on approval. The {@code senseiId}
 * column links to the {@link backend.teacher.TeacherEntity} who must approve it.
 *
 * <p>The {@code photo} column is nullable and, when present, is surfaced as the
 * avatar fallback on the public profile / directory (the equipped cosmetic wins
 * when one is set). Equipped cosmetics live on the owning {@code UserEntity}, not
 * here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "student",
    indexes = {
        @Index(name = "idx_student_status",  columnList = "status"),
        @Index(name = "idx_student_state",   columnList = "state"),
        @Index(name = "idx_student_belt",    columnList = "belt"),
        @Index(name = "idx_student_sensei",  columnList = "sensei_id"),
        @Index(name = "idx_student_user",    columnList = "user_id", unique = true)
    }
)
public class StudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column
    private Integer age;

    /** Free-text belt label (e.g. "Yellow", "Black"). Keep as varchar so the
     *  federation can introduce new belts without a migration. Drives the
     *  frontend's belt-colour banner + avatar. */
    @Column(length = 30)
    private String belt;

    @Column(length = 80)
    private String state;

    @Column(length = 80)
    private String city;

    /** The directory teacher (sensei) this student applied under. The
     *  application is routed to — and must be approved by — this teacher. */
    @Column(name = "sensei_id")
    private Long senseiId;

    /** Denormalized sensei display name (captured at application time) so the
     *  directory card can render it without a join. */
    @Column(name = "sensei_name", length = 200)
    private String senseiName;

    /** The applicant's account. Null only for legacy/seed rows created without
     *  an authenticated session. Used to promote the user to STUDENT on
     *  approval. */
    @Column(name = "user_id")
    private Long userId;

    // ── Identity / family (from the registration form) ─────────────────────────
    @Column(name = "father_name", length = 120)
    private String fatherName;

    @Column(name = "mother_name", length = 120)
    private String motherName;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "blood_group", length = 4)
    private String bloodGroup;

    @Column(name = "mobile_number", length = 15)
    private String mobileNumber;

    @Column(length = 400)
    private String address;

    @Column(name = "pin_code", length = 10)
    private String pinCode;

    // ── Directory stats / display ──────────────────────────────────────────────
    @Column(name = "photo", length = 512)
    private String photo;

    @Builder.Default
    @Column
    private Integer campsCount = 0;

    @Builder.Default
    @Column
    private Integer eventsCount = 0;

    @Builder.Default
    @Column(name = "is_champion", nullable = false)
    private boolean isChampion = false;

    @Column(name = "champion_year")
    private Integer championYear;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "achievements", columnDefinition = "jsonb")
    private List<String> achievements = new ArrayList<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status = StudentStatus.PENDING;

    /** Set when a sensei/admin rejects an application; surfaced to the applicant. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
