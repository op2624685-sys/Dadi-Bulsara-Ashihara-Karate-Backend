package backend.event.dto;

import backend.event.EventEntity;
import backend.event.EventStatus;
import backend.event.EventType;
import backend.event.Participant;

import java.time.LocalDate;
import java.util.List;

/**
 * The single event projection used by BOTH the public list and the admin list.
 * Carries the DERIVED {@code status} (computed from {@code eventDate}, not
 * stored) alongside {@code published} so the admin manage list can render the
 * draft/published badge while the public list only ever receives published rows.
 */
public record EventResponse(
        Long id,
        String slug,
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
        List<Participant> participants,
        /** Derived UPCOMING/PAST — computed from eventDate by the service. */
        EventStatus status,
        boolean published,
        Long createdById
) {
    /**
     * Builds a response from an entity plus a pre-computed derived status (the
     * service derives it once so "now" is consistent across a whole list).
     */
    public static EventResponse of(EventEntity e, EventStatus status) {
        return new EventResponse(
                e.getId(),
                e.getSlug(),
                e.getTitle(),
                e.getEventDate(),
                e.getLocation(),
                e.getOrganizer(),
                e.getType(),
                e.getPrizePool(),
                e.getDuration(),
                e.getTotalParticipants(),
                e.getHighlight(),
                e.getState(),
                e.getParticipants(),
                status,
                e.isPublished(),
                e.getCreatedById());
    }
}
