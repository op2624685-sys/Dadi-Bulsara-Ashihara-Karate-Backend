package backend.admin.dto;

import java.util.Map;

/**
 * Aggregate counts powering the admin dashboard. For a SUB_ADMIN the teacher
 * counts are already scoped to their managed state (the {@code scope} / {@code state}
 * fields tell the UI which).
 */
public record AdminStatsResponse(
        /** GLOBAL for ADMIN, STATE for a SUB_ADMIN. */
        String scope,
        /** The managed state (only set when scope == STATE). */
        String state,
        long totalTeachers,
        long pendingTeachers,
        long approvedTeachers,
        long rejectedTeachers,
        long totalUsers,
        long totalStudents,
        long totalSubAdmins,
        long totalAdmins,
        /** Teacher id → count, for the "by state" doughnut (already scope-limited). */
        Map<String, Long> teachersByState
) {
}
