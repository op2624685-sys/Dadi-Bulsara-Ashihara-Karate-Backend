package backend.teacher;

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
import java.util.ArrayList;
import java.util.List;

/**
 * A karate instructor in the federation directory.
 *
 * <p><b>No-N+1 by construction.</b> Everything a teacher needs — including
 * the lists (achievements, certifications, notable students, timeline) — lives
 * in a single table as {@code jsonb} columns. There are no {@code @OneToMany}
 * relations, so listing or loading a teacher is always exactly one SQL
 * statement, regardless of how many achievements/timeline entries a teacher
 * has. The alternative (normalized child tables) would force a join or a
 * secondary query per teacher — the classic N+1 trap — which this design
 * avoids entirely.
 *
 * <p>The {@code photo} / {@code bannerUrl} columns are nullable. When null the
 * frontend falls back to its existing default avatar (initials inside the
 * rotating dan-coloured rings) and the dan-coloured banner — i.e. "the
 * avatar banner which was currently there". Uploading real images is a future
 * storage concern; the contract is intentionally nullable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "teacher",
    indexes = {
        @Index(name = "idx_teacher_status",        columnList = "status"),
        @Index(name = "idx_teacher_state",         columnList = "state"),
        @Index(name = "idx_teacher_rank",          columnList = "rank"),
        @Index(name = "idx_teacher_belt",          columnList = "belt"),
        @Index(name = "idx_teacher_dan_grade",     columnList = "dan_grade"),
        @Index(name = "idx_teacher_featured_dan",  columnList = "featured, dan_grade"),
        @Index(name = "idx_teacher_user",          columnList = "user_id")
    }
)
public class TeacherEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    /**
     * Links this directory profile to the owning account (a UserEntity with
     * role TEACHER). Null for profiles that have no login (e.g. seeded
     * historical instructors). Set when a teacher account is created so the
     * teacher can log in and, among other things, approve the student
     * applications routed to them.
     */
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 30)
    private String phone;

    @Column
    private Integer age;

    /** Free-text belt label (e.g. "Black", "Red-Black"). Keep as varchar so
     *  the federation can introduce new belts without a migration. */
    @Column(length = 30)
    private String belt;

    /** Dan rank label derived from {@link #danGrade} (Shodan..Hachidan).
     *  Stored (not computed on read) so it can be filtered/sorted directly. */
    @Column(length = 20)
    private String rank;

    /** 1..8 dan grade. Drives the frontend's dan-colour system + banner. */
    @Column(name = "dan_grade")
    private Integer danGrade;

    @Column(length = 80)
    private String state;

    @Column(length = 80)
    private String city;

    @Column(name = "dojo_name", length = 160)
    private String dojoName;

    @Column(name = "dojo_location", length = 200)
    private String dojoLocation;

    @Column(name = "dojo_lat")
    private Double dojoLat;

    @Column(name = "dojo_lng")
    private Double dojoLng;

    @Column(name = "years_training")
    private Integer yearsTraining;

    /** Count of active students (separate from {@link #studentsList}). */
    @Column
    private Integer students;

    @Column(name = "camps_hosted")
    private Integer campsHosted;

    @Column(name = "seminars_given")
    private Integer seminarsGiven;

    @Column(name = "speciality", length = 60)
    private String speciality;

    /** Short card bio. */
    @Column(length = 400)
    private String bio;

    /** Long-form biography for the detail page. */
    @Column(name = "full_bio", length = 4000)
    private String fullBio;

    @Column(name = "certified_by", length = 160)
    private String certifiedBy;

    /** Optional custom avatar URL. Null → frontend default avatar. */
    @Column(name = "photo", length = 512)
    private String photo;

    /** Optional custom banner URL. Null → frontend default dan banner. */
    @Column(name = "banner_url", length = 512)
    private String bannerUrl;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "achievements", columnDefinition = "jsonb")
    private List<String> achievements = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certifications", columnDefinition = "jsonb")
    private List<String> certifications = new ArrayList<>();

    /** Notable students (names + note strings), shown on the detail page. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "students_list", columnDefinition = "jsonb")
    private List<String> studentsList = new ArrayList<>();

    /** Career timeline. JSON so it's fetched with the rest of the row. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timeline", columnDefinition = "jsonb")
    private List<TimelineEntry> timeline = new ArrayList<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeacherStatus status = TeacherStatus.APPROVED;

    /** Set when an admin rejects an application; surfaced to the applicant. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Pinned/senior teachers sort first within a page. */
    @Builder.Default
    @Column(nullable = false)
    private boolean featured = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
