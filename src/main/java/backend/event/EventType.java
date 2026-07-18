package backend.event;

/**
 * The kind of competitive/instructional event. Stored on the row and indexed so
 * the public page can filter by type. Matches the frontend's type union
 * ("Tournament" | "Championship" | "Seminar").
 */
public enum EventType {
    TOURNAMENT,
    CHAMPIONSHIP,
    SEMINAR
}
