package backend.student;

/**
 * Lifecycle of a student membership application.
 *
 * <p>A student application is created as {@code PENDING} when a user registers
 * and picks a sensei. It is routed to that sensei (and visible to federation
 * admins) for approval. On approval the applicant's account is promoted to the
 * {@code STUDENT} role and the record becomes publicly visible in the
 * directory; on rejection it is hidden from the public directory.
 */
public enum StudentStatus {
    PENDING,
    APPROVED,
    REJECTED
}
