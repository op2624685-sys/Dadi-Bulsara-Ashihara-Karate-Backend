package backend.student.dto;

import backend.student.StudentEntity;
import backend.student.StudentStatus;

import java.time.Instant;

/**
 * Approver-facing student row (admin console + teacher approval queue). Unlike
 * {@link StudentSummaryResponse} it is NOT restricted to APPROVED students and
 * exposes {@code status} (and {@code rejectionReason}) so an approver can triage
 * the queue. Still a single-row projection — no N+1.
 */
public record StudentAdminSummaryResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String state,
        String city,
        String belt,
        Integer age,
        String senseiName,
        Long senseiId,
        Long userId,
        StudentStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        // ── Identity / family (from the registration form) ──
        String fatherName,
        String motherName,
        String dob,
        String bloodGroup,
        String mobileNumber,
        String address,
        String pinCode,
        // ── Uploaded registration documents ──
        String photo,
        String aadharUrl,
        String passportPhotoUrl,
        String beltCertificateUrl,
        String signatureUrl
) {
    public static StudentAdminSummaryResponse of(StudentEntity s) {
        return new StudentAdminSummaryResponse(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                s.getEmail(),
                s.getPhone(),
                s.getState(),
                s.getCity(),
                s.getBelt(),
                s.getAge(),
                s.getSenseiName(),
                s.getSenseiId(),
                s.getUserId(),
                s.getStatus(),
                s.getRejectionReason(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getFatherName(),
                s.getMotherName(),
                s.getDob() != null ? s.getDob().toString() : null,
                s.getBloodGroup(),
                s.getMobileNumber(),
                s.getAddress(),
                s.getPinCode(),
                s.getPhoto(),
                s.getAadharUrl(),
                s.getPassportPhotoUrl(),
                s.getBeltCertificateUrl(),
                s.getSignatureUrl());
    }
}
