package backend.event;

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
 * A tournament / championship / seminar the federation runs.
 *
 * <p><b>No N+1 by construction.</b> The results roster ({@code participants})
 * lives in the row as a {@code jsonb} column — no child table, no join, so
 * listing or loading an event is always one SQL statement (same design as
 * {@link backend.teacher.TeacherEntity} and {@link backend.camp.CampEntity}).
 *
 * <p><b>Derived status.</b> There is deliberately NO status column: an event's
 * UPCOMING/PAST status is computed from {@link #eventDate} at read time
 * (see {@link EventServiceImpl}). This keeps the split correct as time passes
 * with no scheduled flip.
 *
 * <p><b>State scoping.</b> {@link #state} is the venue's state and is the axis a
 * SUB_ADMIN is scoped to: a sub-admin may only create/edit/delete events in
 * their own {@code managedState}, and the backend force-sets the state on create
 * (see {@link EventServiceImpl#effectiveState}).
 *
 * <p><b>Draft vs published.</b> Created hidden ({@code published = false}) and
 * announced later via publish; public reads filter on {@code published = true}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "event",
    indexes = {
        @Index(name = "idx_event_slug",       columnList = "slug", unique = true),
        @Index(name = "idx_event_state",      columnList = "state"),
        @Index(name = "idx_event_type",       columnList = "type"),
        @Index(name = "idx_event_date",       columnList = "event_date"),
        @Index(name = "idx_event_published",  columnList = "published")
    }
)
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL-safe unique identifier. Generated on create, stable across edits. */
    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(nullable = false, length = 250)
    private String title;

    /** The event date — drives the DERIVED upcoming/past status. */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(length = 250)
    private String location;

    @Column(length = 200)
    private String organizer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType type;

    /** Optional prize pool label, e.g. "₹1,50,000". */
    @Column(name = "prize_pool", length = 60)
    private String prizePool;

    /** Optional duration label. */
    @Column(length = 60)
    private String duration;

    /** Optional headline participant count. */
    @Column(name = "total_participants")
    private Integer totalParticipants;

    /** Optional one-line highlight/tagline. */
    @Column(length = 500)
    private String highlight;

    /** Venue state — the SUB_ADMIN scoping axis (indexed). */
    @Column(length = 80)
    private String state;

    /** Results roster. JSON so it's fetched with the row; defaults to empty. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participants", columnDefinition = "jsonb")
    private List<Participant> participants = new ArrayList<>();

    /**
     * DRAFT flag. Created {@code false} (hidden), flipped via publish. Public
     * endpoints only return published events.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    /** The admin/sub-admin account that created this event. Null for seeds. */
    @Column(name = "created_by_id")
    private Long createdById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
