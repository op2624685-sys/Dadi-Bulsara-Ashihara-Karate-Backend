package backend.camp.dto;

import backend.camp.CampParticipant;
import backend.camp.CampReward;
import backend.camp.CampStatus;
import backend.camp.Instructor;
import backend.camp.ScheduleDay;
import backend.camp.TrainingPillar;

import java.util.List;

/**
 * Partial-update payload for a camp. Every field is nullable: a {@code null}
 * field is left unchanged (PATCH semantics), so the edit form can send only what
 * changed. The slug is intentionally NOT updatable — it stays stable so public
 * URLs never break (see {@link backend.camp.CampServiceImpl#update}).
 */
public record CampUpdateRequest(
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
        List<String> aboutImages,
        String quote,
        String quoteAuthor,
        String description,
        String kana,
        List<TrainingPillar> pillars,
        List<Instructor> instructors,
        List<ScheduleDay> schedule,
        List<String> galleryImages,
        List<CampParticipant> results,
        List<CampReward> rewards,
        CampStatus status
) {
}
