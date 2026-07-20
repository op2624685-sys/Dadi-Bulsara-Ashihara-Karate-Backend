package backend.camp.dto;

import backend.camp.CampEntity;
import backend.camp.CampStatus;

/**
 * Lightweight camp projection for list/card views. A single-row projection — no
 * N+1. Includes {@code published} so the admin manage list can render the
 * draft/published badge; public list callers only ever receive published camps.
 */
public record CampSummaryResponse(
        Long id,
        String slug,
        String name,
        String subtitle,
        String location,
        String state,
        Integer year,
        String duration,
        Integer participants,
        Integer sessions,
        Integer instructorCount,
        String heroImage,
        CampStatus status,
        boolean published
) {
    public static CampSummaryResponse of(CampEntity c) {
        return new CampSummaryResponse(
                c.getId(),
                c.getSlug(),
                c.getName(),
                c.getSubtitle(),
                c.getLocation(),
                c.getState(),
                c.getYear(),
                c.getDuration(),
                c.getParticipants(),
                c.getSessions(),
                c.getInstructorCount(),
                c.getHeroImage(),
                c.getStatus(),
                c.isPublished());
    }
}
