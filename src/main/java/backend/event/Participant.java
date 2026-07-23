package backend.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A competitor/attendee in an event's results table. Stored as a JSON element
 * inside the event row (see {@link EventEntity#participants}) — single-row
 * fetch, no N+1.
 *
 * <p>Plain Lombok class (NOT a record) so it serialises into {@code jsonb}.
 * {@code placement} is the podium result ("1st" / "2nd" / "3rd") and is null for
 * a participant who did not place. {@code role} mirrors the frontend union
 * ("Student" / "Teacher").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Participant {
    private String id;
    private String name;
    /** "Student" or "Teacher". */
    private String role;
    /** The participant's home state. */
    private String state;
    private String belt;
    /** "1st" / "2nd" / "3rd", or null if unplaced. */
    private String placement;

    /**
     * Linked registered account (nullable). When an admin links a placing
     * participant to a user and saves results, the matching rank's reward
     * cosmetic is auto-granted to this user. Free-text {@code name} is kept
     * for display; {@code userId} drives the grant.
     */
    private Long userId;
}
