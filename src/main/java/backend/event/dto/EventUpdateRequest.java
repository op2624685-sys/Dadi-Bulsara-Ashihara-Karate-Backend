package backend.event.dto;

import backend.event.EventType;
import backend.event.Participant;

import java.time.LocalDate;
import java.util.List;

/**
 * Partial-update payload for an event. Every field is nullable: a {@code null}
 * field is left unchanged (PATCH semantics). The slug is not updatable (kept
 * stable). For a SUB_ADMIN, a supplied {@code state} is still coerced to their
 * managed state by the service, so they can never move an event out of scope.
 */
public record EventUpdateRequest(
        String title,
        LocalDate eventDate,
        String location,
        String organizer,
        EventType type,
        String prizePool,
        String duration,
        Integer totalParticipants,
        String highlight,
        String state,
        List<Participant> participants
) {
}
