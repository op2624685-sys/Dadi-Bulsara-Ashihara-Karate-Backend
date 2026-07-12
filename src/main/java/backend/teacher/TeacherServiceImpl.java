package backend.teacher;

import backend.security.SecurityService;
import backend.teacher.dto.*;
import backend.teacher.exception.TeacherNotFoundException;
import backend.user.CosmeticCatalogue;
import backend.user.EquippedCosmetics;
import backend.user.Role;
import backend.user.UserEntity;
import backend.user.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private static final Logger log = LoggerFactory.getLogger(TeacherServiceImpl.class);

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    /**
     * Ensures a SUB_ADMIN can only touch teachers in their managed state.
     * ADMIN passes through untouched. Throws 403 (AccessDenied → handled by
     * AccessDeniedHandlerImpl) when a sub-admin reaches for another state.
     */
    private void assertWithinScope(TeacherEntity teacher) {
        SecurityService.AdminScope scope = securityService.scope();
        if (scope.isSubAdmin()
                && (teacher.getState() == null
                    || !teacher.getState().equalsIgnoreCase(scope.state()))) {
            throw new AccessDeniedException(
                    "Sub-admins may only manage teachers in state: " + scope.state());
        }
    }

    /** Returns the effective state filter: a sub-admin's state wins; admin's
     *  explicit param (or null) is honoured. */
    private String effectiveState(String requestedState) {
        SecurityService.AdminScope scope = securityService.scope();
        return scope.isSubAdmin() ? scope.state() : requestedState;
    }

    // ---------------------------------------------------------------------
    // Directory listing — single Specification, Spring derives the count query
    // ---------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TeacherSummaryResponse> listTeachers(
            String search, String state, String rank, String belt,
            Integer minDan, Pageable pageable) {

        Long currentUserId = currentUserIdOrNull();
        Specification<TeacherEntity> spec = buildSpecification(
                search, state, rank, belt, minDan, currentUserId);

        Page<TeacherEntity> page = teacherRepository.findAll(spec, pageable);

        log.debug("Listed teachers page={} size={} total={}",
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());

        // Equipped cosmetics live on the UserEntity; join them in one batched
        // fetch (not per-row) so the directory cards can render them.
        Map<Long, UserEntity> usersById = userRepository
                .findAllById(page.getContent().stream()
                        .map(TeacherEntity::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));
        return PageResponse.of(page, t -> {
            UserEntity u = t.getUserId() != null ? usersById.get(t.getUserId()) : null;
            return TeacherSummaryResponse.of(
                    t,
                    u != null ? u.getEquippedAvatarId() : null,
                    u != null ? u.getEquippedBannerId() : null);
        });
    }

    // ---------------------------------------------------------------------
    // Single teacher
    // ---------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public TeacherResponse getTeacher(Long id) {
        TeacherEntity teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new TeacherNotFoundException(id));

        // Public endpoint: hide non-approved teachers entirely (don't leak
        // existence of pending/rejected applications).
        if (teacher.getStatus() != TeacherStatus.APPROVED) {
            throw new TeacherNotFoundException(id);
        }
        return toResponse(teacher);
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherResponse getMine(UserEntity currentUser) {
        // The caller's own record — returned at any status (pending/approved),
        // unlike the public getTeacher. 404 if they never registered as a sensei.
        TeacherEntity teacher = teacherRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new TeacherNotFoundException(currentUser.getId()));
        return toResponse(teacher);
    }

    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public TeacherResponse register(TeacherRegistrationRequest req) {
        // Role-based guard: a teacher is already a sensei and a student is
        // already a member — neither may apply for a teacher registration. The
        // endpoint is public, so anonymous (guest) applicants proceed.
        UserEntity currentUser = securityService.getCurrentUserOrNull();
        if (currentUser != null) {
            Role role = currentUser.getRole();
            if (role == Role.TEACHER) {
                throw new IllegalArgumentException("You are already registered as a sensei");
            }
            if (role == Role.STUDENT) {
                throw new IllegalArgumentException("Students cannot apply to become a teacher");
            }
        }

        TeacherEntity teacher = TeacherEntity.builder()
                .firstName(req.firstName().trim())
                .lastName(req.lastName().trim())
                .email(req.email().toLowerCase().trim())
                .phone(req.phone())
                .age(req.age())
                .state(req.state())
                .city(req.city() != null ? req.city().trim() : null)
                .dojoName(req.dojoName().trim())
                .dojoLocation(req.dojoLocation().trim())
                .dojoLat(req.dojoLat())
                .dojoLng(req.dojoLng())
                .yearsTraining(req.yearsTraining())
                .speciality(req.speciality())
                .belt(req.belt())
                .danGrade(req.danGrade())
                .rank(danToRank(req.danGrade()))
                .bio(req.bio() != null ? req.bio().trim() : null)
                .fullBio(req.fullBio() != null ? req.fullBio().trim() : null)
                .certifiedBy(defaultCertifiedBy(req.certifiedBy()))
                .achievements(req.achievements() != null ? req.achievements() : List.of())
                .certifications(req.certifications() != null ? req.certifications() : List.of())
                .studentsList(List.of())
                .timeline(List.of())
                .photo(req.photoUrl())
                .bannerUrl(null) // default → frontend's current dan banner
                .status(TeacherStatus.PENDING)
                .rejectionReason(null)
                .featured(false)
                .build();

        teacher = teacherRepository.save(teacher);
        log.info("Teacher registered (PENDING): id={} name={} {}", teacher.getId(),
                teacher.getFirstName(), teacher.getLastName());
        return toResponse(teacher);
    }

    // ---------------------------------------------------------------------
    // Self-service update — mirrors StudentServiceImpl#updateMine
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public TeacherResponse updateMine(UserEntity currentUser, TeacherUpdateRequest req) {
        TeacherEntity teacher = teacherRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new TeacherNotFoundException(currentUser.getId()));

        if (req.phone() != null)        teacher.setPhone(req.phone());
        if (req.dojoName() != null)     teacher.setDojoName(req.dojoName().trim());
        if (req.dojoLocation() != null) teacher.setDojoLocation(req.dojoLocation().trim());
        if (req.city() != null)         teacher.setCity(req.city().isBlank() ? null : req.city().trim());
        if (req.state() != null)        teacher.setState(req.state());
        if (req.belt() != null)         teacher.setBelt(req.belt());
        if (req.danGrade() != null) {
            teacher.setDanGrade(req.danGrade());
            // `rank` is derived from danGrade and stored (see TeacherEntity),
            // so keep it in sync on every change.
            teacher.setRank(danToRank(req.danGrade()));
        }
        if (req.speciality() != null)   teacher.setSpeciality(req.speciality());
        if (req.yearsTraining() != null) teacher.setYearsTraining(req.yearsTraining());
        if (req.age() != null)          teacher.setAge(req.age());
        if (req.bio() != null)          teacher.setBio(req.bio() != null ? req.bio().trim() : null);
        if (req.certifiedBy() != null)  teacher.setCertifiedBy(req.certifiedBy().isBlank()
                ? defaultCertifiedBy(null) : req.certifiedBy().trim());
        if (req.achievements() != null) teacher.setAchievements(req.achievements());
        if (req.certifications() != null) teacher.setCertifications(req.certifications());
        if (req.students() != null)      teacher.setStudents(req.students());
        if (req.campsHosted() != null)   teacher.setCampsHosted(req.campsHosted());
        if (req.seminarsGiven() != null) teacher.setSeminarsGiven(req.seminarsGiven());

        teacher = teacherRepository.save(teacher);
        log.info("Teacher record updated: id={} name={} {}", teacher.getId(),
                teacher.getFirstName(), teacher.getLastName());
        return toResponse(teacher);
    }

    // ---------------------------------------------------------------------
    // Admin: listing (all statuses), detail, approve, reject, delete
    // ---------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TeacherAdminSummaryResponse> listForAdmin(
            String search, TeacherStatus status, String requestedState, Pageable pageable) {

        final String state = effectiveState(requestedState);

        Specification<TeacherEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (isNotBlank(state)) {
                predicates.add(cb.equal(cb.lower(root.get("state")), state.toLowerCase()));
            }
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        List.of("firstName", "lastName", "dojoName", "speciality", "email", "state").stream()
                                .map(f -> cb.like(lowerCoalesce(cb, root, f), like))
                                .toArray(Predicate[]::new)));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TeacherEntity> page = teacherRepository.findAll(spec, pageable);
        return PageResponse.of(page, TeacherAdminSummaryResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherResponse getForAdmin(Long id) {
        TeacherEntity teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new TeacherNotFoundException(id));
        assertWithinScope(teacher);
        return toResponse(teacher);
    }

    @Override
    @Transactional
    public TeacherResponse approve(Long id) {
        TeacherEntity teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new TeacherNotFoundException(id));
        assertWithinScope(teacher);
        teacher.setStatus(TeacherStatus.APPROVED);
        teacher.setRejectionReason(null);
        teacher = teacherRepository.save(teacher);
        seedUnlocksFromBelt(teacher);
        log.info("Teacher approved: id={} name={} {}", teacher.getId(),
                teacher.getFirstName(), teacher.getLastName());
        return toResponse(teacher);
    }

    @Override
    @Transactional
    public TeacherResponse reject(Long id, String reason) {
        TeacherEntity teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new TeacherNotFoundException(id));
        assertWithinScope(teacher);
        teacher.setStatus(TeacherStatus.REJECTED);
        teacher.setRejectionReason(isNotBlank(reason) ? reason.trim() : null);
        teacher = teacherRepository.save(teacher);
        log.info("Teacher rejected: id={} reason={}", teacher.getId(), teacher.getRejectionReason());
        return toResponse(teacher);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TeacherEntity teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new TeacherNotFoundException(id));
        assertWithinScope(teacher);
        teacherRepository.deleteById(id);
        log.info("Teacher deleted: id={}", id);
    }

    // ---------------------------------------------------------------------
    // Specification builder
    // ---------------------------------------------------------------------
    private Specification<TeacherEntity> buildSpecification(
            String search, String state, String rank, String belt,
            Integer minDan, Long excludeUserId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always scope to publicly visible teachers.
            predicates.add(cb.equal(root.get("status"), TeacherStatus.APPROVED));

            // A signed-in sensei must not see their own card in the public
            // directory. Seed rows have no userId, so we keep them (OR userId IS
            // NULL) and only drop the row that belongs to the caller.
            if (excludeUserId != null) {
                predicates.add(cb.or(
                        cb.isNull(root.get("userId")),
                        cb.notEqual(root.get("userId"), excludeUserId)));
            }

            if (isNotBlank(state)) {
                predicates.add(cb.equal(root.get("state"), state));
            }
            if (isNotBlank(rank)) {
                predicates.add(cb.equal(root.get("rank"), rank));
            }
            if (isNotBlank(belt)) {
                predicates.add(cb.equal(root.get("belt"), belt));
            }
            if (minDan != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("danGrade"), minDan));
            }
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(searchFields(root, cb).stream()
                        .map(f -> cb.like(lowerCoalesce(cb, root, f), like))
                        .toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static List<String> searchFields(Root<TeacherEntity> root, CriteriaBuilder cb) {
        return List.of("firstName", "lastName", "dojoName", "speciality", "email", "bio");
    }

    private static Expression<String> lowerCoalesce(
            CriteriaBuilder cb, Root<TeacherEntity> root, String field) {
        return cb.lower(cb.coalesce(root.get(field), ""));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    static String danToRank(Integer danGrade) {
        if (danGrade == null) return null;
        return switch (danGrade) {
            case 1 -> "Shodan";
            case 2 -> "Nidan";
            case 3 -> "Sandan";
            case 4 -> "Yondan";
            case 5 -> "Godan";
            case 6 -> "Rokudan";
            case 7 -> "Nanadan";
            case 8 -> "Hachidan";
            default -> null;
        };
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** The signed-in user's id, or {@code null} for anonymous visitors. */
    private Long currentUserIdOrNull() {
        UserEntity u = securityService.getCurrentUserOrNull();
        return u != null ? u.getId() : null;
    }

    private static String defaultCertifiedBy(String provided) {
        return (provided != null && !provided.isBlank())
                ? provided.trim()
                : "Dadi Bulsara Ashihara Karate Federation";
    }

    // ---------------------------------------------------------------------
    // Cosmetics helpers
    // ---------------------------------------------------------------------
    /** Builds the full teacher response, joining in equipped cosmetics from the
     *  owning user account (null → role-default / dan look). */
    private TeacherResponse toResponse(TeacherEntity t) {
        EquippedCosmetics eq = equippedOf(t);
        return TeacherResponse.of(t, eq.avatarId(), eq.bannerId());
    }

    /** Reads the equipped-avatar/banner pair from the teacher's linked account. */
    private EquippedCosmetics equippedOf(TeacherEntity t) {
        if (t.getUserId() == null) {
            return EquippedCosmetics.NONE;
        }
        UserEntity u = userRepository.findById(t.getUserId()).orElse(null);
        return u != null
                ? new EquippedCosmetics(u.getEquippedAvatarId(), u.getEquippedBannerId())
                : EquippedCosmetics.NONE;
    }

    /** Seeds the linked user's belt-based cosmetics unlocks (idempotent union
     *  with any existing unlocks) when a teacher is approved. */
    private void seedUnlocksFromBelt(TeacherEntity teacher) {
        if (teacher.getUserId() == null) {
            return;
        }
        userRepository.findById(teacher.getUserId()).ifPresent(user -> {
            user.setUnlockedCosmetics(
                    CosmeticCatalogue.mergeUnlocks(user.getUnlockedCosmetics(), teacher.getBelt()));
            userRepository.save(user);
        });
    }
}
