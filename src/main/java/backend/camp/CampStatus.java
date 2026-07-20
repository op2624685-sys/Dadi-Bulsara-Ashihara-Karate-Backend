package backend.camp;

/**
 * Whether a camp is still to come or already concluded.
 *
 * <p>Unlike an event's status (which is derived from its date), a camp's status
 * is <b>stored</b> on the row and edited by an admin. This lets the federation
 * keep a historical camp flagged {@code PAST} while curating its gallery and
 * write-up, and flag a {@code UPCOMING} camp before its exact schedule is set.
 */
public enum CampStatus {
    UPCOMING,
    PAST
}
