package backend.camp;

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
 * A training camp / gasshuku the federation has run (or will run).
 *
 * <p><b>No N+1 by construction.</b> Everything a camp needs — including its
 * lists (about images, training pillars, instructors, day-by-day schedule and
 * gallery) — lives in a single table as {@code jsonb} columns. There are no
 * {@code @OneToMany} relations, so listing or loading a camp is always exactly
 * one SQL statement, mirroring the {@link backend.teacher.TeacherEntity}
 * design.
 *
 * <p><b>Draft vs published.</b> A camp is created as a hidden DRAFT
 * ({@code published = false}). It only appears on the public {@code /camps}
 * pages once an admin publishes it. Public reads always filter on
 * {@code published = true}; admin reads see everything.
 *
 * <p><b>Slug.</b> Generated once from the name + year on create (slugified,
 * uniqueness enforced) and kept <b>stable</b> across edits so public URLs never
 * break. Seed rows pass the exact legacy frontend slug explicitly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "camp",
    indexes = {
        @Index(name = "idx_camp_slug",           columnList = "slug", unique = true),
        @Index(name = "idx_camp_state",          columnList = "state"),
        @Index(name = "idx_camp_year",           columnList = "year"),
        @Index(name = "idx_camp_published",      columnList = "published"),
        @Index(name = "idx_camp_published_year", columnList = "published, year")
    }
)
public class CampEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL-safe unique identifier used by the public detail page. Generated on
     *  create (slugify name + year) and stable across edits. */
    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String subtitle;

    @Column(length = 200)
    private String location;

    @Column(length = 80)
    private String state;

    /** Calendar year the camp ran. Indexed — public list sorts by year desc. */
    @Column(name = "year")
    private Integer year;

    /** Free-text duration, e.g. "5 Days". */
    @Column(length = 60)
    private String duration;

    /** Number of participants (headline stat). */
    @Column(name = "participants")
    private Integer participants;

    /** Number of training sessions (headline stat). */
    @Column(name = "sessions")
    private Integer sessions;

    /** Number of instructors (headline stat; may differ from the list size). */
    @Column(name = "instructor_count")
    private Integer instructorCount;

    /** Hero/banner image URL or path. */
    @Column(name = "hero_image", length = 512)
    private String heroImage;

    /** The two "about" section images. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "about_images", columnDefinition = "jsonb")
    private List<String> aboutImages = new ArrayList<>();

    /** Pull-quote shown on the detail page. */
    @Column(length = 500)
    private String quote;

    @Column(name = "quote_author", length = 200)
    private String quoteAuthor;

    /** Long-form description / write-up. */
    @Column(length = 4000)
    private String description;

    /** Japanese kana subtitle, e.g. "武道合宿 · Budo Gasshuku". */
    @Column(length = 120)
    private String kana;

    /** Training pillars / methods. JSON so they're fetched with the row. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pillars", columnDefinition = "jsonb")
    private List<TrainingPillar> pillars = new ArrayList<>();

    /** Instructors who taught at the camp. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "instructors", columnDefinition = "jsonb")
    private List<Instructor> instructors = new ArrayList<>();

    /** Day-by-day schedule. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule", columnDefinition = "jsonb")
    private List<ScheduleDay> schedule = new ArrayList<>();

    /** Gallery image URLs/paths. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_images", columnDefinition = "jsonb")
    private List<String> galleryImages = new ArrayList<>();

    /** Stored status (UPCOMING/PAST) — edited by an admin, not derived. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampStatus status = CampStatus.UPCOMING;

    /**
     * DRAFT flag. Created as {@code false} (hidden); flipped to {@code true} via
     * the publish action. Public endpoints only ever return published camps.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    /** The admin account that created this camp. Null for seeded rows. */
    @Column(name = "created_by_id")
    private Long createdById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
