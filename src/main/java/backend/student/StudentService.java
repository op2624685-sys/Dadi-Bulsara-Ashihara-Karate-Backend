package backend.student;

import backend.teacher.dto.PageResponse;
import backend.student.dto.StudentAdminSummaryResponse;
import backend.student.dto.StudentPublicResponse;
import backend.student.dto.StudentRegistrationRequest;
import backend.student.dto.StudentResponse;
import backend.student.dto.StudentSummaryResponse;
import backend.student.dto.StudentUpdateRequest;
import backend.user.UserEntity;
import org.springframework.data.domain.Pageable;

public interface StudentService {

    /**
     * Public directory listing. One query (Specification → WHERE predicates,
     * Spring derives the count). No N+1. Only APPROVED students are returned.
     */
    PageResponse<StudentSummaryResponse> listStudents(
            String search, String state, String belt, Pageable pageable);

    /** Single student by id. 404 if missing OR not publicly visible. */
    StudentResponse getStudent(Long id);

    /**
     * Public, sanitized single-student projection for the public profile page.
     * Returns only non-personal fields plus the sensei's dojo, and 404s unless
     * the student is APPROVED. Use this from anonymous/public contexts instead
     * of {@link #getStudent(Long)}, which leaks personal data.
     */
    StudentPublicResponse getPublicStudent(Long id);

    /** The current user's own application (any status), or 404 if none. */
    StudentResponse getMine(UserEntity currentUser);

    /**
     * Public "Register as Student". Requires an authenticated applicant. The
     * record is created PENDING and routed to {@code req.senseiId()}, who must
     * approve it before the applicant is promoted to STUDENT and the record
     * shows in the public directory.
     */
    StudentResponse register(StudentRegistrationRequest req, UserEntity currentUser);

    /**
     * Update the caller's own student record (PATCH-style: null fields are
     * left unchanged). 404 if they have no application yet.
     */
    StudentResponse updateMine(UserEntity currentUser, StudentUpdateRequest req);

    // ── Teacher (sensei) approval — scoped to one sensei ───────────────────────

    /** The queue of applications routed to {@code senseiId}. */
    PageResponse<StudentAdminSummaryResponse> listForTeacher(
            Long senseiId, String search, StudentStatus status, Pageable pageable);

    /** Approve as the owning sensei. Promotes the applicant to STUDENT. */
    StudentResponse approveAsTeacher(Long id, Long senseiId);

    StudentResponse rejectAsTeacher(Long id, Long senseiId, String reason);

    // ── Admin (mirror of the teacher-approval console) ─────────────────────────

    PageResponse<StudentAdminSummaryResponse> listForAdmin(
            String search, StudentStatus status, Pageable pageable);

    StudentResponse getForAdmin(Long id);

    StudentResponse approve(Long id);

    StudentResponse reject(Long id, String reason);

    void delete(Long id);
}
