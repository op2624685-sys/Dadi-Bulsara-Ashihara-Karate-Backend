package backend.camp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One day of a camp's day-by-day schedule. Stored as a JSON element inside the
 * camp row (see {@link CampEntity#schedule}) — single-row fetch, no N+1.
 *
 * <p>Plain Lombok class (NOT a record) so it serialises into {@code jsonb}.
 * The nested {@code tags} list is defaulted to an empty list so a builder that
 * omits it never produces {@code null}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDay {
    /** Day number label, e.g. "01". */
    private String dayNum;
    /** Human day label, e.g. "Monday · Arrival". */
    private String dayLabel;
    private String title;
    private String description;
    /** Highlight tags for the day, e.g. ["Kata", "Bunkai"]. */
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
