package backend.camp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One "training method / pillar" of a camp's curriculum (e.g. Kata Refinement,
 * Kumite Pressure). Stored as a JSON element inside the camp row (see
 * {@link CampEntity#pillars}), so the whole camp — including its pillars — is
 * read in a single row fetch. No separate table, no join, therefore no N+1.
 *
 * <p>Must be a plain Lombok class (NOT a record) so Hibernate's
 * {@code @JdbcTypeCode(SqlTypes.JSON)} + Jackson can serialise/deserialise it
 * into a {@code jsonb} column.
 *
 * <p>{@code icon} is stored as a free-text {@code String} matching the
 * frontend's lowercase union ("kata" | "kumite" | "conditioning" | ...), so new
 * icons can be introduced without a schema/enum migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPillar {
    /** Display order id, e.g. "01". */
    private String id;
    /** Method label, e.g. "Method 01". */
    private String method;
    private String title;
    private String description;
    /** Free-text intensity, e.g. "4 hrs / day" or "Nightly". */
    private String hoursPerDay;
    /** Lowercase icon key matching the frontend union (kata, kumite, ...). */
    private String icon;
}
