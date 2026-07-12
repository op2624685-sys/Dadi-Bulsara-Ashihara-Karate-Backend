package backend.admin;

import backend.admin.dto.AdminStatsResponse;
import backend.security.SecurityService;
import backend.teacher.TeacherRepository;
import backend.teacher.TeacherStatus;
import backend.user.Role;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the admin dashboard aggregate. Teacher counts are scoped: a SUB_ADMIN
 * only sees totals for their managed state; an ADMIN sees everything. (User
 * counts are global — users aren't state-bound in this model.)
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        SecurityService.AdminScope scope = securityService.scope();
        String state = scope.state();           // null for ADMIN
        boolean global = !scope.isSubAdmin();

        long totalTeachers    = global ? teacherRepository.count()
                                        : teacherRepository.countByStateIgnoreCase(state);
        long pendingTeachers  = global ? teacherRepository.countByStatus(TeacherStatus.PENDING)
                                        : teacherRepository.countByStatusAndStateIgnoreCase(TeacherStatus.PENDING, state);
        long approvedTeachers = global ? teacherRepository.countByStatus(TeacherStatus.APPROVED)
                                        : teacherRepository.countByStatusAndStateIgnoreCase(TeacherStatus.APPROVED, state);
        long rejectedTeachers = global ? teacherRepository.countByStatus(TeacherStatus.REJECTED)
                                        : teacherRepository.countByStatusAndStateIgnoreCase(TeacherStatus.REJECTED, state);

        long totalUsers     = userRepository.count();
        long totalStudents  = userRepository.countByRole(Role.STUDENT)
                              + userRepository.countByRole(Role.USER);
        long totalSubAdmins = userRepository.countByRole(Role.SUB_ADMIN);
        long totalAdmins    = userRepository.countByRole(Role.ADMIN);

        // Only compute breakdown for the scope the caller can see.
        Map<String, Long> byState = computeTeachersByState(global ? null : state);

        return new AdminStatsResponse(
                global ? "GLOBAL" : "STATE",
                state,
                totalTeachers, pendingTeachers, approvedTeachers, rejectedTeachers,
                totalUsers, totalStudents, totalSubAdmins, totalAdmins,
                byState);
    }

    /**
     * Returns a state → teacher-count map. Pass {@code null} for every state, or
     * a specific state to limit to one.
     */
    private Map<String, Long> computeTeachersByState(String onlyState) {
        Map<String, Long> result = new LinkedHashMap<>();
        // Iterate the finite set of known states to keep this DB-agnostic
        // (we want ALL teachers regardless of status for the breakdown).
        for (String s : KNOWN_STATES) {
            if (onlyState != null && !onlyState.equalsIgnoreCase(s)) continue;
            long c = teacherRepository.countByStateIgnoreCase(s);
            if (c > 0) result.put(s, c);
        }
        return result;
    }

    // Mirror of the STATES list used by the registration form. Keeping it explicit
    // here avoids a second source of truth in the runtime config.
    private static final List<String> KNOWN_STATES = List.of(
            "Maharashtra", "Gujarat", "Delhi", "Kerala", "Karnataka",
            "Tamil Nadu", "Punjab", "West Bengal", "Andhra Pradesh", "Rajasthan");
}
