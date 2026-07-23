package backend.camp;

import backend.camp.dto.CampCreateRequest;
import backend.camp.dto.CampResponse;
import backend.camp.dto.CampSummaryResponse;
import backend.camp.dto.CampUpdateRequest;
import backend.camp.exception.CampNotFoundException;
import backend.security.SecurityService;
import backend.storage.StorageException;
import backend.storage.StorageService;
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
import org.springframework.web.multipart.MultipartFile;

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
    private final backend.cosmetic.CosmeticService cosmeticService;
    private final StorageService storageService;

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
        // Legacy JSON path: the caller has already uploaded any images and
        // baked the URLs into the request. No storage activity here.
        return doCreate(req);
    }

    @Override
    public CampResponse createWithImages(CampCreateRequest payload,
                                          MultipartFile heroImage,
                                          MultipartFile aboutImage0,
                                          MultipartFile aboutImage1,
                                          List<MultipartFile> galleryImages,
                                          List<MultipartFile> instructorImages) {
        // Deliberately NOT @Transactional — the storage uploads must not be
        // inside a DB transaction. Upload all images first; if any upload
        // fails, best-effort delete the ones that already succeeded. If the
        // DB save then fails, best-effort delete again.
        UploadedImages uploaded = new UploadedImages();
        try {
            uploaded.hero = uploadIfPresent(heroImage);
            uploaded.about0 = uploadIfPresent(aboutImage0);
            uploaded.about1 = uploadIfPresent(aboutImage1);
            uploaded.gallery = uploadAllIfPresent(galleryImages);
            uploaded.instructor = uploadAllIfPresent(instructorImages);
        } catch (RuntimeException ex) {
            uploaded.bestEffortDeleteAll(storageService, log);
            throw ex;
        }

        CampCreateRequest enriched = mergeCreateWithUploads(payload, uploaded);
        try {
            return doCreate(enriched);
        } catch (RuntimeException ex) {
            uploaded.bestEffortDeleteAll(storageService, log);
            throw ex;
        }
    }

    @Transactional
    protected CampResponse doCreate(CampCreateRequest req) {
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
                .results(nullToEmpty(req.results()))
                .rewards(nullToEmpty(req.rewards()))
                .status(req.status() != null ? req.status() : CampStatus.UPCOMING)
                // A new camp always starts hidden — it is announced later via publish.
                .published(false)
                .createdById(creatorId)
                .build();

        camp = campRepository.save(camp);
        grantRewards(camp);
        log.info("Camp created (DRAFT): id={} slug={} name='{}' by userId={}",
                camp.getId(), camp.getSlug(), camp.getName(), creatorId);
        return CampResponse.of(camp);
    }

    @Override
    public CampResponse update(Long id, CampUpdateRequest req) {
        // Legacy JSON path.
        return doUpdate(id, req);
    }

    @Override
    public CampResponse updateWithImages(Long id, CampUpdateRequest payload,
                                          MultipartFile heroImage,
                                          MultipartFile aboutImage0,
                                          MultipartFile aboutImage1,
                                          List<MultipartFile> galleryImages,
                                          List<MultipartFile> instructorImages) {
        UploadedImages uploaded = new UploadedImages();
        try {
            uploaded.hero = uploadIfPresent(heroImage);
            uploaded.about0 = uploadIfPresent(aboutImage0);
            uploaded.about1 = uploadIfPresent(aboutImage1);
            uploaded.gallery = uploadAllIfPresent(galleryImages);
            uploaded.instructor = uploadAllIfPresent(instructorImages);
        } catch (RuntimeException ex) {
            uploaded.bestEffortDeleteAll(storageService, log);
            throw ex;
        }

        // For update, the payload already carries the existing URLs. Merge:
        // if a slot has a new upload, prefer it; else keep what the payload has.
        CampUpdateRequest enriched = mergeUpdateWithUploads(payload, uploaded);
        try {
            return doUpdate(id, enriched);
        } catch (RuntimeException ex) {
            uploaded.bestEffortDeleteAll(storageService, log);
            throw ex;
        }
    }

    @Transactional
    protected CampResponse doUpdate(Long id, CampUpdateRequest req) {
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
        if (req.results() != null)         camp.setResults(req.results());
        if (req.rewards() != null)          camp.setRewards(req.rewards());
        if (req.status() != null)          camp.setStatus(req.status());

        boolean rewardChange = req.results() != null || req.rewards() != null;
        camp = campRepository.save(camp);
        if (rewardChange) grantRewards(camp);
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
    // Multipart helpers (upload + merge + cleanup)
    // ─────────────────────────────────────────────────────────────────────

    /** Upload a single image if present (non-null and non-empty). */
    private String uploadIfPresent(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        log.info("Uploading camp image: name='{}' size={}", file.getOriginalFilename(), file.getSize());
        return storageService.upload(file).url();
    }

    /** Upload each non-empty file; returns the URLs in the same order. */
    private List<String> uploadAllIfPresent(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return new ArrayList<>();
        List<String> urls = new ArrayList<>(files.size());
        for (MultipartFile f : files) {
            urls.add(uploadIfPresent(f));
        }
        return urls;
    }

    /**
     * For create: build a {@link CampCreateRequest} that has the freshly
     * uploaded URLs merged in. The payload's existing image fields are
     * overridden by the uploads (the form sends them as null/empty to
     * signal "use the new file").
     */
    private CampCreateRequest mergeCreateWithUploads(CampCreateRequest payload, UploadedImages u) {
        List<String> aboutImages = new ArrayList<>(2);
        if (u.about0 != null) aboutImages.add(u.about0);
        else if (payload.aboutImages() != null && payload.aboutImages().size() > 0) aboutImages.add(payload.aboutImages().get(0));
        if (u.about1 != null) aboutImages.add(u.about1);
        else if (payload.aboutImages() != null && payload.aboutImages().size() > 1) aboutImages.add(payload.aboutImages().get(1));

        // Galleries: prefer new uploads; append any extra payload URLs that
        // don't have a corresponding new file (e.g. a user reorders but
        // doesn't replace them all).
        List<String> gallery = new ArrayList<>();
        int payloadGalleryIdx = 0;
        for (String url : u.gallery) {
            if (url != null) {
                gallery.add(url);
            } else if (payload.galleryImages() != null && payloadGalleryIdx < payload.galleryImages().size()) {
                gallery.add(payload.galleryImages().get(payloadGalleryIdx++));
            }
        }
        // Append any remaining payload gallery URLs that didn't have a slot.
        if (payload.galleryImages() != null) {
            while (payloadGalleryIdx < payload.galleryImages().size()) {
                gallery.add(payload.galleryImages().get(payloadGalleryIdx++));
            }
        }

        // Instructors: pair uploaded images to existing instructor rows by
        // index. An upload replaces the row's `image` field; the rest of the
        // instructor fields are left untouched in the payload.
        List<Instructor> payloadInstructors = payload.instructors() != null
                ? payload.instructors() : List.<Instructor>of();
        List<Instructor> mergedInstructors = new ArrayList<>();
        for (int i = 0; i < payloadInstructors.size(); i++) {
            Instructor orig = payloadInstructors.get(i);
            String newImage = (u.instructor != null && i < u.instructor.size()) ? u.instructor.get(i) : null;
            String image = newImage != null ? newImage : orig.getImage();
            mergedInstructors.add(Instructor.builder()
                    .id(orig.getId())
                    .name(orig.getName())
                    .grade(orig.getGrade())
                    .role(orig.getRole())
                    .origin(orig.getOrigin())
                    .image(image)
                    .build());
        }

        return new CampCreateRequest(
                payload.name(),
                payload.subtitle(),
                payload.location(),
                payload.state(),
                payload.year(),
                payload.duration(),
                payload.participants(),
                payload.sessions(),
                payload.instructorCount(),
                u.hero != null ? u.hero : payload.heroImage(),
                aboutImages,
                payload.quote(),
                payload.quoteAuthor(),
                payload.description(),
                payload.kana(),
                payload.pillars(),
                mergedInstructors,
                payload.schedule(),
                gallery,
                payload.results(),
                payload.rewards(),
                payload.status());
    }

    /**
     * For update: the payload already carries the existing URLs (PATCH
     * semantics). If a new file is uploaded for a slot, the new URL wins;
     * otherwise the payload's URL is kept. If the payload sends a slot as
     * null AND there's no new file, the field is left unchanged
     * (doUpdate's PATCH rules will skip it).
     */
    private CampUpdateRequest mergeUpdateWithUploads(CampUpdateRequest payload, UploadedImages u) {
        // aboutImages: same merging as create.
        List<String> aboutImages = null;
        if (payload.aboutImages() != null || u.about0 != null || u.about1 != null) {
            aboutImages = new ArrayList<>(2);
            if (u.about0 != null) aboutImages.add(u.about0);
            else if (payload.aboutImages() != null && !payload.aboutImages().isEmpty())
                aboutImages.add(payload.aboutImages().get(0));
            if (u.about1 != null) aboutImages.add(u.about1);
            else if (payload.aboutImages() != null && payload.aboutImages().size() > 1)
                aboutImages.add(payload.aboutImages().get(1));
        }

        // gallery: same as create.
        List<String> gallery = null;
        if (payload.galleryImages() != null || (u.gallery != null && !u.gallery.isEmpty())) {
            gallery = new ArrayList<>();
            int payloadIdx = 0;
            List<String> payloadGallery = payload.galleryImages() != null ? payload.galleryImages() : List.<String>of();
            for (String url : u.gallery) {
                if (url != null) {
                    gallery.add(url);
                } else if (payloadIdx < payloadGallery.size()) {
                    gallery.add(payloadGallery.get(payloadIdx++));
                }
            }
            while (payloadIdx < payloadGallery.size()) {
                gallery.add(payloadGallery.get(payloadIdx++));
            }
        }

        // instructors: same pairing by index.
        List<Instructor> mergedInstructors = null;
        if (payload.instructors() != null) {
            mergedInstructors = new ArrayList<>();
            for (int i = 0; i < payload.instructors().size(); i++) {
                Instructor orig = payload.instructors().get(i);
                String newImage = (u.instructor != null && i < u.instructor.size()) ? u.instructor.get(i) : null;
                String image = newImage != null ? newImage : orig.getImage();
                mergedInstructors.add(Instructor.builder()
                        .id(orig.getId())
                        .name(orig.getName())
                        .grade(orig.getGrade())
                        .role(orig.getRole())
                        .origin(orig.getOrigin())
                        .image(image)
                        .build());
            }
        }

        return new CampUpdateRequest(
                payload.name(),
                payload.subtitle(),
                payload.location(),
                payload.state(),
                payload.year(),
                payload.duration(),
                payload.participants(),
                payload.sessions(),
                payload.instructorCount(),
                u.hero != null ? u.hero : payload.heroImage(),
                aboutImages,
                payload.quote(),
                payload.quoteAuthor(),
                payload.description(),
                payload.kana(),
                payload.pillars(),
                mergedInstructors,
                payload.schedule(),
                gallery,
                payload.results(),
                payload.rewards(),
                payload.status());
    }

    /**
     * Bookkeeping for a single multipart-create: every uploaded URL we need to
     * roll back if anything goes wrong. {@code gallery} and {@code instructor}
     * are sized to match the corresponding payload lists — {@code null}
     * entries are slots the user didn't replace.
     */
    private static final class UploadedImages {
        String hero;
        String about0;
        String about1;
        List<String> gallery = new ArrayList<>();
        List<String> instructor = new ArrayList<>();

        void bestEffortDeleteAll(StorageService storage, Logger log) {
            deleteOne(storage, log, hero);
            deleteOne(storage, log, about0);
            deleteOne(storage, log, about1);
            if (gallery != null) for (String u : gallery) deleteOne(storage, log, u);
            if (instructor != null) for (String u : instructor) deleteOne(storage, log, u);
        }

        private static void deleteOne(StorageService storage, Logger log, String url) {
            if (url == null) return;
            try {
                storage.delete(url);
            } catch (StorageException ex) {
                log.warn("Best-effort delete of '{}' after a failed save failed: {}", url, ex.getMessage());
            }
        }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Reward grant — when an admin saves results, every placing participant
    // that is linked to a registered user gets the cosmetic assigned to
    // their rank (1st/2nd/3rd) in the camp's `rewards` map.
    // ─────────────────────────────────────────────────────────────────────────
    private void grantRewards(CampEntity camp) {
        if (camp.getRewards() == null || camp.getRewards().isEmpty()) return;
        if (camp.getResults() == null) return;
        for (CampParticipant p : camp.getResults()) {
            if (p.getUserId() == null || isBlank(p.getPlacement())) continue;
            Integer rank = placementToRank(p.getPlacement());
            if (rank == null) continue;
            CampReward reward = camp.getRewards().stream()
                    .filter(r -> r.getRank() == rank)
                    .findFirst().orElse(null);
            if (reward != null && isNotBlank(reward.getCosmeticId())) {
                cosmeticService.grantCosmetic(p.getUserId(), reward.getCosmeticId());
            }
        }
    }

    /** Maps "1st"/"2nd"/"3rd" → 1/2/3; null for any other value. */
    private static Integer placementToRank(String placement) {
        if (placement == null) return null;
        String p = placement.trim().toLowerCase();
        if (p.startsWith("1")) return 1;
        if (p.startsWith("2")) return 2;
        if (p.startsWith("3")) return 3;
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
