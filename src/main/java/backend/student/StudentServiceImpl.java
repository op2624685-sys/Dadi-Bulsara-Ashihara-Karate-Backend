package backend.student;

import backend.security.SecurityService;
import backend.student.dto.*;
import backend.student.exception.StudentNotFoundException;
import backend.teacher.TeacherEntity;
import backend.teacher.TeacherRepository;
import backend.teacher.dto.PageResponse;
import backend.user.CosmeticCatalogue;
import backend.user.EquippedCosmetics;
import backend.user.Role;
import backend.user.UserEntity;
import backend.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    // ---------------------------------------------------------------------
    // Public directory
    // ---------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentSummaryResponse> listStudents(
            String search, String state, String belt, Pageable pageable) {
        Long currentUserId = currentUserIdOrNull();
        Specification<StudentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), StudentStatus.APPROVED));
            // A signed-in student must not see their own card in the public
            // directory. Seed rows have no userId, so we keep them (OR userId IS NULL)
            // and only drop the row that belongs to the caller.
            if (currentUserId != null) {
                predicates.add(cb.or(
                        cb.isNull(root.get("userId")),
                        cb.notEqual(root.get("userId"), currentUserId)));
            }
            if (isNotBlank(state)) {
                predicates.add(cb.equal(root.get("state"), state));
            }
            if (isNotBlank(belt)) {
                predicates.add(cb.equal(cb.lower(root.get("belt")), belt.toLowerCase()));
            }
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(searchFields(root, cb).stream()
                        .map(f -> cb.like(lowerCoalesce(cb, root, f), like))
                        .toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<StudentEntity> page = studentRepository.findAll(spec, pageable);

        // Equipped cosmetics live on the UserEntity; join them in one batched
        // fetch (not per-row) so the directory cards can render them.
        Map<Long, UserEntity> usersById = userRepository
                .findAllById(page.getContent().stream()
                        .map(StudentEntity::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));
        return PageResponse.of(page, s -> {
            UserEntity u = s.getUserId() != null ? usersById.get(s.getUserId()) : null;
            return StudentSummaryResponse.of(
                    s,
                    u != null ? u.getEquippedAvatarId() : null,
                    u != null ? u.getEquippedBannerId() : null);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudent(Long id) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        if (student.getStatus() != StudentStatus.APPROVED) {
            throw new StudentNotFoundException(id);
        }
        return toResponse(student);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentPublicResponse getPublicStudent(Long id) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        if (student.getStatus() != StudentStatus.APPROVED) {
            throw new StudentNotFoundException(id);
        }
        return toPublicResponse(student);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getMine(UserEntity currentUser) {
        StudentEntity student = studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new StudentNotFoundException(currentUser.getId()));
        return toResponse(student);
    }

    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------
    @Override
    @Transactional
    public StudentResponse register(StudentRegistrationRequest req, UserEntity currentUser) {
        // Role-based guard: a student is already a student, and a teacher may
        // not apply for student membership (product rule). Other roles
        // (USER / ADMIN / SUB_ADMIN) may still apply.
        Role role = currentUser.getRole();
        if (role == Role.STUDENT) {
            throw new IllegalArgumentException("You are already registered as a student");
        }
        if (role == Role.TEACHER) {
            throw new IllegalArgumentException("Teachers cannot apply for student membership");
        }

        // One live application per account.
        if (studentRepository.existsByUserIdAndStatusIn(
                currentUser.getId(),
                List.of(StudentStatus.PENDING, StudentStatus.APPROVED))) {
            throw new IllegalArgumentException(
                    "You already have a student membership application");
        }
        if (req.senseiId() == null) {
            throw new IllegalArgumentException("Please choose a sensei (teacher)");
        }
        TeacherEntity sensei = teacherRepository.findById(req.senseiId())
                .orElseThrow(() -> new IllegalArgumentException("Selected sensei is not available"));
        if (sensei.getStatus() != backend.teacher.TeacherStatus.APPROVED) {
            throw new IllegalArgumentException("Selected sensei is not available for approvals");
        }

        // Identity is taken from the authenticated account; the form values are
        // accepted only when present and fall back to the account otherwise.
        String firstName = isNotBlank(req.firstName()) ? req.firstName().trim() : currentUser.getFirstName();
        String lastName  = isNotBlank(req.lastName())  ? req.lastName().trim()  : currentUser.getLastName();
        String email     = isNotBlank(req.email())     ? req.email().toLowerCase().trim() : currentUser.getEmail();

        StudentEntity student = StudentEntity.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(req.mobileNumber())
                .age(req.age())
                .belt(req.belt())
                .state(req.state())
                .city(isNotBlank(req.city()) ? req.city().trim() : null)
                .senseiId(sensei.getId())
                .senseiName((sensei.getFirstName() + " " + sensei.getLastName()).trim())
                .userId(currentUser.getId())
                .fatherName(req.fatherName())
                .motherName(req.motherName())
                .dob(parseDob(req.dob()))
                .bloodGroup(req.bloodGroup())
                .mobileNumber(req.mobileNumber())
                .address(req.address())
                .pinCode(req.pinCode())
                .photo(req.photoUrl())
                .aadharUrl(req.aadharUrl())
                .passportPhotoUrl(req.passportPhotoUrl())
                .beltCertificateUrl(req.beltCertificateUrl())
                .signatureUrl(req.signatureUrl())
                .campsCount(0)
                .eventsCount(0)
                .isChampion(false)
                .achievements(req.achievements() != null ? req.achievements() : List.of())
                .status(StudentStatus.PENDING)
                .rejectionReason(null)
                .build();

        student = studentRepository.save(student);

        // Seed the applicant's unlocked cosmetics from their belt (idempotent
        // union — preserves any event/achievement unlocks already present).
        UserEntity user = userRepository.findById(currentUser.getId()).orElse(currentUser);
        user.setUnlockedCosmetics(
                CosmeticCatalogue.mergeUnlocks(user.getUnlockedCosmetics(), req.belt()));
        userRepository.save(user);

        log.info("Student application submitted (PENDING): id={} name={} {} senseiId={}",
                student.getId(), student.getFirstName(), student.getLastName(), sensei.getId());
        return toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse updateMine(UserEntity currentUser, StudentUpdateRequest req) {
        StudentEntity student = studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new StudentNotFoundException(currentUser.getId()));
        if (req.fatherName() != null)   student.setFatherName(req.fatherName());
        if (req.motherName() != null)   student.setMotherName(req.motherName());
        if (req.dob() != null)          student.setDob(parseDob(req.dob()));
        if (req.bloodGroup() != null)   student.setBloodGroup(req.bloodGroup());
        if (req.mobileNumber() != null) student.setMobileNumber(req.mobileNumber());
        if (req.email() != null)        student.setEmail(req.email().toLowerCase().trim());
        if (req.address() != null)      student.setAddress(req.address());
        if (req.state() != null)        student.setState(req.state());
        if (req.city() != null)         student.setCity(req.city().isBlank() ? null : req.city().trim());
        if (req.pinCode() != null)      student.setPinCode(req.pinCode());
        student = studentRepository.save(student);
        // Keep the account's state in sync so sub-admin scoping stays correct.
        if (req.state() != null) {
            currentUser.setState(req.state());
            userRepository.save(currentUser);
        }
        log.info("Student record updated: id={}", student.getId());
        return toResponse(student);
    }

    // ---------------------------------------------------------------------
    // Teacher (sensei) approval
    // ---------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentAdminSummaryResponse> listForTeacher(
            Long senseiId, String search, StudentStatus status, Pageable pageable) {
        Specification<StudentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("senseiId"), senseiId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        List.of("firstName", "lastName", "state", "belt", "senseiName", "email").stream()
                                .map(f -> cb.like(lowerCoalesce(cb, root, f), like))
                                .toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<StudentEntity> page = studentRepository.findAll(spec, pageable);
        return PageResponse.of(page, StudentAdminSummaryResponse::of);
    }

    @Override
    @Transactional
    public StudentResponse approveAsTeacher(Long id, Long senseiId) {
        StudentEntity student = requireOwnedBySensei(id, senseiId);
        student.setStatus(StudentStatus.APPROVED);
        student.setRejectionReason(null);
        student = studentRepository.save(student);
        promoteToStudent(student);
        log.info("Student approved by sensei {}: id={} name={} {}",
                senseiId, student.getId(), student.getFirstName(), student.getLastName());
        return toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse rejectAsTeacher(Long id, Long senseiId, String reason) {
        StudentEntity student = requireOwnedBySensei(id, senseiId);
        student.setStatus(StudentStatus.REJECTED);
        student.setRejectionReason(isNotBlank(reason) ? reason.trim() : null);
        student = studentRepository.save(student);
        log.info("Student rejected by sensei {}: id={} reason={}", senseiId, id, student.getRejectionReason());
        return toResponse(student);
    }

    // ---------------------------------------------------------------------
    // Admin approval (mirror of the teacher-approval console)
    // ---------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentAdminSummaryResponse> listForAdmin(
            String search, StudentStatus status, Pageable pageable) {
        Specification<StudentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (isNotBlank(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        List.of("firstName", "lastName", "state", "belt", "senseiName", "email").stream()
                                .map(f -> cb.like(lowerCoalesce(cb, root, f), like))
                                .toArray(Predicate[]::new)));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<StudentEntity> page = studentRepository.findAll(spec, pageable);
        return PageResponse.of(page, StudentAdminSummaryResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getForAdmin(Long id) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        return toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse approve(Long id) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        student.setStatus(StudentStatus.APPROVED);
        student.setRejectionReason(null);
        student = studentRepository.save(student);
        promoteToStudent(student);
        log.info("Student approved by admin: id={} name={} {}", student.getId(), student.getFirstName(), student.getLastName());
        return toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse reject(Long id, String reason) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        student.setStatus(StudentStatus.REJECTED);
        student.setRejectionReason(isNotBlank(reason) ? reason.trim() : null);
        student = studentRepository.save(student);
        log.info("Student rejected by admin: id={} reason={}", id, student.getRejectionReason());
        return toResponse(student);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        studentRepository.deleteById(id);
        log.info("Student deleted by admin: id={}", id);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    /** Promotes the applicant to STUDENT once their application is approved. */
    private void promoteToStudent(StudentEntity student) {
        if (student.getUserId() == null) {
            log.info("Student id={} has no linked account (seed row) — skipping role promotion", student.getId());
            return;
        }
        Optional<UserEntity> applicant = userRepository.findById(student.getUserId());
        if (applicant.isEmpty()) {
            log.warn("Student id={} links to missing user_id={} — skipping role promotion", student.getId(), student.getUserId());
            return;
        }
        UserEntity user = applicant.get();
        if (user.getRole() != Role.STUDENT) {
            user.setRole(Role.STUDENT);
            log.info("Promoted user_id={} to STUDENT", user.getId());
        }
        // Mirror the student's state onto the account so sub-admins can scope
        // user-management to their state.
        user.setState(student.getState());
        // Seed belt-based cosmetics unlocks (idempotent union with existing).
        user.setUnlockedCosmetics(
                CosmeticCatalogue.mergeUnlocks(user.getUnlockedCosmetics(), student.getBelt()));
        userRepository.save(user);
    }

    /** Loads a student and ensures it belongs to the given sensei. */
    private StudentEntity requireOwnedBySensei(Long id, Long senseiId) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        if (!senseiId.equals(student.getSenseiId())) {
            throw new AccessDeniedException("This application is not assigned to you");
        }
        return student;
    }

    private static List<String> searchFields(
            jakarta.persistence.criteria.Root<StudentEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        return List.of("firstName", "lastName", "state", "senseiName", "belt", "city");
    }

    private static jakarta.persistence.criteria.Expression<String> lowerCoalesce(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<StudentEntity> root, String field) {
        return cb.lower(cb.coalesce(root.get(field), ""));
    }

    private static LocalDate parseDob(String dob) {
        if (isNotBlank(dob)) {
            try {
                return LocalDate.parse(dob);
            } catch (Exception e) {
                log.warn("Could not parse dob '{}'", dob);
            }
        }
        return null;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** The signed-in user's id, or {@code null} for anonymous visitors. */
    private Long currentUserIdOrNull() {
        UserEntity u = securityService.getCurrentUserOrNull();
        return u != null ? u.getId() : null;
    }

    /** Resolves the owning sensei (if any) so the dojo can be inlined into the response. */
    private TeacherEntity senseiOf(StudentEntity s) {
        if (s.getSenseiId() == null) {
            return null;
        }
        return teacherRepository.findById(s.getSenseiId()).orElse(null);
    }

    /** Builds the full student response, joining in equipped cosmetics from the
     *  owning user account (null → role-default look). */
    private StudentResponse toResponse(StudentEntity s) {
        EquippedCosmetics eq = equippedOf(s);
        return StudentResponse.of(s, senseiOf(s), eq.avatarId(), eq.bannerId());
    }

    /** Builds the public student response, joining in equipped cosmetics. */
    private StudentPublicResponse toPublicResponse(StudentEntity s) {
        EquippedCosmetics eq = equippedOf(s);
        return StudentPublicResponse.of(s, senseiOf(s), eq.avatarId(), eq.bannerId());
    }

    /** Reads the equipped-avatar/banner pair from the student's linked account. */
    private EquippedCosmetics equippedOf(StudentEntity s) {
        if (s.getUserId() == null) {
            return EquippedCosmetics.NONE;
        }
        UserEntity u = userRepository.findById(s.getUserId()).orElse(null);
        return u != null
                ? new EquippedCosmetics(u.getEquippedAvatarId(), u.getEquippedBannerId())
                : EquippedCosmetics.NONE;
    }
}
