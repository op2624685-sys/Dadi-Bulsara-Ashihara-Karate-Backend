package backend.camp;

import backend.camp.dto.CampCreateRequest;
import backend.camp.dto.CampResponse;
import backend.camp.dto.CampSummaryResponse;
import backend.camp.dto.CampUpdateRequest;
import backend.camp.exception.CampNotFoundException;
import backend.security.SecurityService;
import backend.teacher.dto.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Default {@link CampService}. Camps are ADMIN-only to mutate (enforced by
 * SecurityConfig), so — unlike events — there is no per-state scoping here.
 *
 * <p>Key invariants:
 * <ul>
 *   <li>Create always produces a hidden DRAFT ({@code published = false}) with a
 *       generated, unique slug and {@code createdById} stamped from the caller.</li>
 *   <li>The slug is generated once and kept stable across edits so the public
 *       {@code /camps/{slug}} URL never breaks.</li>
 *   <li>Public reads only ever return published camps; a public request for a
 *       draft slug yields a 404 (indistinguishable from "missing").</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CampServiceImpl implements CampService {

    private static final Logger log = LoggerFactory.getLogger(CampServiceImpl.class);

    /** Matches any run of non-alphanumeric chars — collapsed to a single dash. */
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    /** Trims leading/trailing dashes left over from slugification. */
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");

    private final CampRepository campRepository;
    private final SecurityService securityService;

    // ─────────────────────────────────────────────────────────────────────
    // Public reads
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<CampSummaryResponse> listPublished() {
        List<CampSummaryResponse> body = campRepository.findByPublishedTrueOrderByYearDesc()
                .stream()
                .map(CampSummaryResponse::of)
                .toList();
        log.debug("Public camp list served: {} published camps", body.size());
        return body;
    }

    @Override
    @Transactional(readOnly = true)
    public CampResponse getPublishedBySlug(String slug) {
        // Only published camps resolve publicly. A draft (or unknown) slug is a
        // 404 so we never leak the existence of an unpublished camp.
        CampEntity camp = campRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new CampNotFoundException(
                        "No published camp found for slug: " + slug));
        return CampResponse.of(camp);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Admin reads
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CampSummaryResponse> listForAdmin(String search, Pageable pageable) {
        Specification<CampEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("name"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("location"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("state"), "")), like)));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<CampEntity> page = campRepository.findAll(spec, pageable);
        log.debug("Admin camp list page={} size={} total={}",
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
        return PageResponse.of(page, CampSummaryResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public CampResponse getForAdmin(Long id) {
        return CampResponse.of(findOrThrow(id));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Admin mutations
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public CampResponse create(CampCreateRequest req) {
        Long creatorId = securityService.requireCurrentUser().getId();

        CampEntity camp = CampEntity.builder()
                .slug(generateUniqueSlug(req.name(), req.year()))
                .name(req.name().trim())
                .subtitle(req.subtitle())
                .location(req.location())
                .state(req.state())
                .year(req.year())
                .duration(req.duration())
                .participants(req.participants())
                .sessions(req.sessions())
                .instructorCount(req.instructorCount())
                .heroImage(req.heroImage())
                .aboutImages(nullToEmpty(req.aboutImages()))
                .quote(req.quote())
                .quoteAuthor(req.quoteAuthor())
                .description(req.description())
                .kana(req.kana())
                .pillars(nullToEmpty(req.pillars()))
                .instructors(nullToEmpty(req.instructors()))
                .schedule(nullToEmpty(req.schedule()))
                .galleryImages(nullToEmpty(req.galleryImages()))
                .status(req.status() != null ? req.status() : CampStatus.UPCOMING)
                // A new camp always starts hidden — it is announced later via publish.
                .published(false)
                .createdById(creatorId)
                .build();

        camp = campRepository.save(camp);
        log.info("Camp created (DRAFT): id={} slug={} name='{}' by userId={}",
                camp.getId(), camp.getSlug(), camp.getName(), creatorId);
        return CampResponse.of(camp);
    }

    @Override
    public CampResponse update(Long id, CampUpdateRequest req) {
        CampEntity camp = findOrThrow(id);

        // PATCH semantics: only non-null fields are applied. The slug is NEVER
        // touched here so the public URL stays valid across edits.
        if (req.name() != null)            camp.setName(req.name().trim());
        if (req.subtitle() != null)        camp.setSubtitle(req.subtitle());
        if (req.location() != null)        camp.setLocation(req.location());
        if (req.state() != null)           camp.setState(req.state());
        if (req.year() != null)            camp.setYear(req.year());
        if (req.duration() != null)        camp.setDuration(req.duration());
        if (req.participants() != null)    camp.setParticipants(req.participants());
        if (req.sessions() != null)        camp.setSessions(req.sessions());
        if (req.instructorCount() != null) camp.setInstructorCount(req.instructorCount());
        if (req.heroImage() != null)       camp.setHeroImage(req.heroImage());
        if (req.aboutImages() != null)     camp.setAboutImages(req.aboutImages());
        if (req.quote() != null)           camp.setQuote(req.quote());
        if (req.quoteAuthor() != null)     camp.setQuoteAuthor(req.quoteAuthor());
        if (req.description() != null)     camp.setDescription(req.description());
        if (req.kana() != null)            camp.setKana(req.kana());
        if (req.pillars() != null)         camp.setPillars(req.pillars());
        if (req.instructors() != null)     camp.setInstructors(req.instructors());
        if (req.schedule() != null)        camp.setSchedule(req.schedule());
        if (req.galleryImages() != null)   camp.setGalleryImages(req.galleryImages());
        if (req.status() != null)          camp.setStatus(req.status());

        camp = campRepository.save(camp);
        log.info("Camp updated: id={} slug={}", camp.getId(), camp.getSlug());
        return CampResponse.of(camp);
    }

    @Override
    public void delete(Long id) {
        CampEntity camp = findOrThrow(id);
        campRepository.delete(camp);
        log.info("Camp deleted: id={} slug={}", id, camp.getSlug());
    }

    @Override
    public CampResponse publish(Long id) {
        CampEntity camp = findOrThrow(id);
        camp.setPublished(true);
        camp = campRepository.save(camp);
        log.info("Camp published: id={} slug={}", camp.getId(), camp.getSlug());
        return CampResponse.of(camp);
    }

    @Override
    public CampResponse unpublish(Long id) {
        CampEntity camp = findOrThrow(id);
        camp.setPublished(false);
        camp = campRepository.save(camp);
        log.info("Camp unpublished: id={} slug={}", camp.getId(), camp.getSlug());
        return CampResponse.of(camp);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private CampEntity findOrThrow(Long id) {
        return campRepository.findById(id)
                .orElseThrow(() -> new CampNotFoundException("No camp found with id: " + id));
    }

    /**
     * Generates a URL-safe, unique slug from the camp name + year. If the base
     * slug already exists, a numeric suffix is appended ({@code -2}, {@code -3},
     * …) until an unused slug is found. Runs only on create; the slug is stable
     * thereafter.
     */
    private String generateUniqueSlug(String name, Integer year) {
        String base = slugify(year != null ? name + " " + year : name);
        if (base.isBlank()) {
            base = "camp";
        }
        String candidate = base;
        int suffix = 2;
        while (campRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /** Lowercases, strips accents, and replaces non-alphanumeric runs with dashes. */
    static String slugify(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");          // drop diacritics
        String lower = normalized.toLowerCase(Locale.ENGLISH);
        String dashed = NON_ALNUM.matcher(lower).replaceAll("-");
        return EDGE_DASHES.matcher(dashed).replaceAll("");
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
