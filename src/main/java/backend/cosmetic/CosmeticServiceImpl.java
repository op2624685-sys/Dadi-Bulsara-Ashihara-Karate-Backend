package backend.cosmetic;

import backend.storage.StorageException;
import backend.storage.StorageService;
import backend.user.Belt;
import backend.user.UserEntity;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link CosmeticService}. Catalogue reads come straight from the
 * {@code cosmetic} table; {@link #grantCosmetic} is the single idempotent
 * append point used by the event/camp reward pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CosmeticServiceImpl implements CosmeticService {

    private final CosmeticRepository cosmeticRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "cosmetics", key = "'all'")
    public List<CosmeticResponse> catalog() {
        return cosmeticRepository.findAllByOrderByIdAsc().stream()
                .map(CosmeticResponse::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CosmeticResponse> listAdmin() {
        return catalog();
    }

    @Override
    @Transactional
    public void grantCosmetic(Long userId, String cosmeticId) {
        if (userId == null || cosmeticId == null) return;
        if (!cosmeticRepository.existsById(cosmeticId)) {
            log.warn("Skipping reward grant: cosmetic '{}' does not exist", cosmeticId);
            return;
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Skipping reward grant: user {} not found", userId);
            return;
        }
        List<String> unlocked = user.getUnlockedCosmetics();
        if (unlocked == null) unlocked = new ArrayList<>();
        if (!unlocked.contains(cosmeticId)) {
            unlocked.add(cosmeticId);
            user.setUnlockedCosmetics(unlocked);
            userRepository.save(user);
            log.info("Granted cosmetic '{}' to user {}", cosmeticId, userId);
        }
    }

    @Override
    @Transactional
    public CosmeticResponse create(CosmeticCreateRequest req) {
        // Legacy JSON path: the caller already provided a (possibly null) imageUrl.
        return buildAndSave(req, req.imageUrl());
    }

    @Override
    public CosmeticResponse createWithOptionalImage(CosmeticCreateRequest req, MultipartFile file) {
        // Deliberately NOT @Transactional — the Cloudinary upload must not be
        // inside a DB transaction. The sequence is: upload FIRST, then a
        // dedicated @Transactional save. If the save fails, we best-effort
        // delete the just-uploaded image so it doesn't orphan in storage.
        String uploadedUrl = null;
        if (file != null && !file.isEmpty()) {
            log.info("Cosmetic createWithOptionalImage: uploading image '{}' ({} bytes)",
                    file.getOriginalFilename(), file.getSize());
            uploadedUrl = storageService.upload(file).url();
        }
        try {
            return buildAndSave(req, uploadedUrl);
        } catch (RuntimeException ex) {
            if (uploadedUrl != null) {
                bestEffortDelete(uploadedUrl);
            }
            throw ex;
        }
    }

    /**
     * The actual save: validate, build the entity, persist, and return the
     * DTO. {@code imageUrl} is the resolved storage URL (null for a CSS/SVG
     * cosmetic). Marked @Transactional so the DB save participates in its own
     * transaction, independent of any non-transactional wrapper.
     */
    @Transactional
    @CacheEvict(value = "cosmetics", allEntries = true)
    protected CosmeticResponse buildAndSave(CosmeticCreateRequest req, String imageUrl) {
        CosmeticType type = parseType(req.type());
        UnlockType unlockType = parseUnlockType(req.unlockType());

        // A belt cosmetic has no required rank; a reward cosmetic must have one.
        Integer requiredRank = null;
        if (unlockType != UnlockType.BELT) {
            if (req.requiredRank() == null || req.requiredRank() < 1 || req.requiredRank() > 3) {
                throw new IllegalArgumentException(
                        "Reward cosmetics require requiredRank in 1..3");
            }
            requiredRank = req.requiredRank();
        }

        String belt = isBlank(req.unlockBelt()) ? "White" : req.unlockBelt();

        Cosmetic cosmetic = Cosmetic.builder()
                .id(type.name().toLowerCase() + "_img_" + UUID.randomUUID().toString().substring(0, 8))
                .type(type)
                .name(req.name().trim())
                .description(req.description())
                .seasonId(isBlank(req.seasonId()) ? "S1" : req.seasonId())
                .imageUrl(imageUrl)
                .unlockType(unlockType)
                .unlockBelt(belt)
                .beltRank(Belt.rank(belt))
                .requiredRank(requiredRank)
                .eventId(req.eventId())
                .campId(req.campId())
                .primaryColor(req.primaryColor())
                .accentColor(req.accentColor())
                .glowColor(req.glowColor())
                .kanji(req.kanji())
                .patternId(req.patternId())
                .build();

        cosmetic = cosmeticRepository.save(cosmetic);
        log.info("Cosmetic created: id={} type={} unlockType={} image={}",
                cosmetic.getId(), type, unlockType, cosmetic.getImageUrl());
        return CosmeticResponse.of(cosmetic);
    }

    private void bestEffortDelete(String url) {
        try {
            storageService.delete(url);
        } catch (StorageException ex) {
            // Cleanup failure is non-fatal — the original error is the one
            // that matters to the caller. Log so the orphan can be cleaned
            // out of Cloudinary manually if it ever becomes a problem.
            log.warn("Best-effort delete of '{}' after a failed save failed: {}",
                    url, ex.getMessage());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "cosmetics", allEntries = true)
    public CosmeticResponse update(String id, CosmeticUpdateRequest req) {
        Cosmetic cosmetic = cosmeticRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No cosmetic found with id: " + id));

        if (req.type() != null)             cosmetic.setType(parseType(req.type()));
        if (req.name() != null)             cosmetic.setName(req.name().trim());
        if (req.description() != null)       cosmetic.setDescription(req.description());
        if (req.seasonId() != null)        cosmetic.setSeasonId(req.seasonId());
        if (req.imageUrl() != null)        cosmetic.setImageUrl(req.imageUrl());
        if (req.unlockType() != null)       cosmetic.setUnlockType(parseUnlockType(req.unlockType()));
        if (req.unlockBelt() != null) {
            cosmetic.setUnlockBelt(req.unlockBelt());
            cosmetic.setBeltRank(Belt.rank(req.unlockBelt()));
        }
        if (req.requiredRank() != null)      cosmetic.setRequiredRank(req.requiredRank());
        if (req.eventId() != null)          cosmetic.setEventId(req.eventId());
        if (req.campId() != null)           cosmetic.setCampId(req.campId());
        if (req.primaryColor() != null)     cosmetic.setPrimaryColor(req.primaryColor());
        if (req.accentColor() != null)      cosmetic.setAccentColor(req.accentColor());
        if (req.glowColor() != null)        cosmetic.setGlowColor(req.glowColor());
        if (req.kanji() != null)           cosmetic.setKanji(req.kanji());
        if (req.patternId() != null)        cosmetic.setPatternId(req.patternId());

        cosmetic = cosmeticRepository.save(cosmetic);
        log.info("Cosmetic updated: id={}", cosmetic.getId());
        return CosmeticResponse.of(cosmetic);
    }

    @Override
    @Transactional
    @CacheEvict(value = "cosmetics", allEntries = true)
    public void delete(String id) {
        Cosmetic cosmetic = cosmeticRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No cosmetic found with id: " + id));
        // Protect the built-in CSS belt cosmetics — deleting them would break the
        // belt-unlock system. Only admin-uploaded image cosmetics are deletable.
        if (cosmetic.getImageUrl() == null) {
            throw new IllegalArgumentException(
                    "Built-in belt cosmetics cannot be deleted");
        }
        cosmeticRepository.delete(cosmetic);
        log.info("Cosmetic deleted: id={}", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private static CosmeticType parseType(String raw) {
        try {
            return CosmeticType.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Invalid cosmetic type (expected avatar|banner): " + raw);
        }
    }

    private static UnlockType parseUnlockType(String raw) {
        try {
            return UnlockType.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Invalid unlock type (expected belt|event_reward|camp_reward): " + raw);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
