package backend.camp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A competitor/attendee in a camp's results table. Stored as a JSON element
 * inside the camp row ({@code results} jsonb) — single-row fetch, no N+1.
 *
 * <p>Mirrors the event {@link backend.event.Participant} but adds {@code userId}
 * so a placing participant can be linked to a registered account and auto-granted
 * their rank's reward cosmetic. {@code placement} is "1st"/"2nd"/"3rd" (null if
 * unplaced); {@code role} mirrors the frontend union ("Student"/"Teacher").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampParticipant {
    private String id;
    private String name;
    /** "Student" or "Teacher". */
    private String role;
    /** The participant's home state. */
    private String state;
    private String belt;
    /** "1st" / "2nd" / "3rd", or null if unplaced. */
    private String placement;
    /** Linked registered account (nullable). Drives reward auto-grant. */
    private Long userId;
}
