package backend.camp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A visiting/hosting instructor at a camp. Stored as a JSON element inside the
 * camp row (see {@link CampEntity#instructors}) — single-row fetch, no N+1.
 *
 * <p>Plain Lombok class (NOT a record) so it serialises into {@code jsonb}.
 * {@code role} is optional (matches the frontend's optional field).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instructor {
    private String id;
    private String name;
    /** Rank label, e.g. "6th Dan". */
    private String grade;
    /** Optional role, e.g. "Head Instructor", "Camp Director". May be null. */
    private String role;
    /** Where the instructor is from, e.g. "Osaka, Japan". */
    private String origin;
    /** Portrait image URL/path. */
    private String image;
}
