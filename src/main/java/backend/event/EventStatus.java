package backend.event;

/**
 * Whether an event is still to come or already concluded.
 *
 * <p><b>Derived, not stored.</b> Unlike a camp's status, an event's status is
 * computed from its {@code eventDate} at read time
 * (see {@link EventServiceImpl#deriveStatus}): {@code UPCOMING} when the date is
 * today or in the future, otherwise {@code PAST}. There is no status column on
 * {@link EventEntity} — this keeps upcoming/past correct automatically as time
 * passes, with no scheduled job to flip a flag.
 */
public enum EventStatus {
    UPCOMING,
    PAST
}
