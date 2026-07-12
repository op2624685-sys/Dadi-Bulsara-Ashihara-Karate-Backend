package backend.teacher;

/**
 * Lifecycle of a teacher's public visibility.
 *
 * <p>Registration creates an APPROVED record by default so it shows up in the
 * public directory immediately (this is a demo federation site, not a
 * moderated platform). In production you'd default new applications to
 * PENDING and flip them to APPROVED via an admin action — the enum is here
 * so that switch is a one-line change with no schema migration.
 */
public enum TeacherStatus {
    PENDING,
    APPROVED,
    REJECTED
}
