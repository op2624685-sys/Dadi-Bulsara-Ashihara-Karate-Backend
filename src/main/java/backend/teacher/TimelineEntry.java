package backend.teacher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One milestone in a teacher's journey timeline. Stored as a JSON element
 * inside the teacher row (see {@link TeacherEntity#timeline}), so the whole
 * teacher — including their timeline — is read in a single row fetch. No
 * separate table, no join, therefore no N+1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEntry {
    private int year;
    private String event;
    private String icon;
}
