package backend.teacher;

import backend.teacher.dto.PageResponse;
import backend.teacher.dto.TeacherAdminSummaryResponse;
import backend.teacher.dto.TeacherRegistrationRequest;
import backend.teacher.dto.TeacherResponse;
import backend.teacher.dto.TeacherSummaryResponse;
import backend.teacher.dto.TeacherUpdateRequest;
import backend.user.UserEntity;
import org.springframework.data.domain.Pageable;

public interface TeacherService {

    /**
     * Public directory listing. Returns a single paginated page built from ONE
     * query (Specification → WHERE predicates, with Spring deriving the matching
     * count query). No N+1: every teacher is one row, no child joins.
     *
     * @param search  free-text match across name/dojo/speciality/email/bio (null = no filter)
     * @param state   exact state filter (null = no filter)
     * @param rank    exact dan-rank label filter (null = no filter)
     * @param belt    exact belt label filter (null = no filter)
     * @param minDan  minimum dan grade inclusive (null = no filter)
     */
    PageResponse<TeacherSummaryResponse> listTeachers(
            String search, String state, String rank, String belt,
            Integer minDan, Pageable pageable);

    /**
     * Single teacher by id. 404 if missing OR not publicly visible
     * (status != APPROVED). One row fetch, jsonb included.
     */
    TeacherResponse getTeacher(Long id);

    /**
     * The caller's own teacher record (any status). 404 if they have none.
     * Mirrors StudentService#getMine so the profile page can show the teacher's
     * full data and a pending/approved state.
     */
    TeacherResponse getMine(UserEntity currentUser);

    /**
     * Public registration ("Join as Sensei"). Persists a PENDING teacher so it
     * does NOT appear in the public directory until an admin approves it, and
     * returns the created record.
     */
    TeacherResponse register(TeacherRegistrationRequest request);

    /**
     * Update the caller's own teacher record (PATCH-style: null fields are left
     * unchanged). Mirrors StudentService#updateMine so the profile page can edit
     * contact / dojo / training fields. Throws 404 if the caller has no teacher
     * record, and recomputes {@code rank} whenever {@code danGrade} changes.
     */
    TeacherResponse updateMine(UserEntity currentUser, TeacherUpdateRequest req);

    // ── Admin ──────────────────────────────────────────────────────────────

    /**
     * Admin directory: every teacher regardless of status, filterable by status
     * and state, searchable, paginated.
     *
     * <p>State-scoped: a SUB_ADMIN is silently forced to their managed state
     * (the {@code state} argument is ignored for them); an ADMIN may pass any
     * state or {@code null} for "all states".
     */
    PageResponse<TeacherAdminSummaryResponse> listForAdmin(
            String search, TeacherStatus status, String state, Pageable pageable);

    /** Admin detail: returns the teacher even if not APPROVED. */
    TeacherResponse getForAdmin(Long id);

    /** Flip a teacher to APPROVED (clears any rejection reason). */
    TeacherResponse approve(Long id);

    /** Flip a teacher to REJECTED, recording an optional reason. */
    TeacherResponse reject(Long id, String reason);

    /** Hard-delete a teacher (admin moderation). */
    void delete(Long id);
}
