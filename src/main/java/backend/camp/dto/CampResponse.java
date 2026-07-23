package backend.camp.dto;

import backend.camp.CampEntity;
import backend.camp.CampParticipant;
import backend.camp.CampReward;
import backend.camp.CampStatus;
import backend.camp.Instructor;
import backend.camp.ScheduleDay;
import backend.camp.TrainingPillar;

import java.util.List;

/**
 * Full camp detail projection (public detail page + admin edit form). Carries
 * everything in {@link CampSummaryResponse} plus the long-form fields and the
 * nested jsonb lists. The nested Lombok classes are Jackson-serializable, so
 * they are reused directly in the response.
 */
public record CampResponse(
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
        CampStatus status,
        boolean published,
        Long createdById
) {
    public static CampResponse of(CampEntity c) {
        return new CampResponse(
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
                c.getAboutImages(),
                c.getQuote(),
                c.getQuoteAuthor(),
                c.getDescription(),
                c.getKana(),
                c.getPillars(),
                c.getInstructors(),
                c.getSchedule(),
                c.getGalleryImages(),
                c.getResults(),
                c.getRewards(),
                c.getStatus(),
                c.isPublished(),
                c.getCreatedById());
    }
}
