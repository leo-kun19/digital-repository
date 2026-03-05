package com.example.thesisrepo.web;

import com.example.thesisrepo.profile.StudentProfile;
import com.example.thesisrepo.profile.StudentProfileRepository;
import com.example.thesisrepo.publication.*;
import com.example.thesisrepo.publication.repo.*;
import com.example.thesisrepo.service.CurrentUserService;
import com.example.thesisrepo.service.StorageService;
import com.example.thesisrepo.service.workflow.AuditEventService;
import com.example.thesisrepo.service.workflow.CaseTimelineService;
import com.example.thesisrepo.service.workflow.PublicationWorkflowGateService;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import com.example.thesisrepo.web.dto.LecturerApprovalQueueRowDto;
import com.example.thesisrepo.web.dto.LecturerCaseWorkItemDto;
import com.example.thesisrepo.web.dto.LecturerStudentGroupDto;
import com.example.thesisrepo.web.dto.TimelineItemDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/lecturer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LECTURER')")
public class LecturerWorkflowController {

  private final CaseSupervisorRepository caseSupervisors;
  private final PublicationCaseRepository cases;
  private final PublicationRegistrationRepository registrations;
  private final WorkflowCommentRepository comments;
  private final SubmissionVersionRepository submissionVersions;
  private final AuditEventRepository auditEventRepository;
  private final StudentProfileRepository studentProfiles;
  private final StorageService storageService;
  private final CurrentUserService currentUser;
  private final PublicationWorkflowGateService workflowGates;
  private final AuditEventService auditEvents;
  private final CaseTimelineService timelineService;

  @GetMapping("/approvals")
  public List<PublicationCase> approvalQueue() {
    User me = currentUser.requireCurrentUser();
    return caseSupervisors.findPendingApprovalsForLecturer(me.getId()).stream()
      .map(CaseSupervisor::getPublicationCase)
      .toList();
  }

  @GetMapping("/approval-queue")
  public List<LecturerApprovalQueueRowDto> approvalQueueDetail() {
    User me = currentUser.requireCurrentUser();
    List<PublicationCase> approvalCases = caseSupervisors.findPendingApprovalsForLecturer(me.getId()).stream()
      .map(CaseSupervisor::getPublicationCase)
      .toList();
    if (approvalCases.isEmpty()) {
      return List.of();
    }

    Map<Long, PublicationRegistration> registrationByCase = registrations.findByPublicationCaseIn(approvalCases).stream()
      .collect(Collectors.toMap(r -> r.getPublicationCase().getId(), r -> r));
    Map<Long, StudentProfile> profileByUser = loadStudentProfiles(approvalCases);

    return approvalCases.stream()
      .map(c -> {
        PublicationRegistration registration = registrationByCase.get(c.getId());
        StudentProfile profile = profileByUser.get(c.getStudent().getId());
        return new LecturerApprovalQueueRowDto(
          c.getId(),
          c.getType(),
          c.getStatus(),
          c.getUpdatedAt(),
          c.getStudent().getId(),
          c.getStudent().getEmail(),
          profile != null ? profile.getName() : c.getStudent().getEmail(),
          profile != null ? profile.getStudentId() : null,
          profile != null ? profile.getFaculty() : null,
          profile != null ? profile.getProgram() : null,
          registration != null ? registration.getTitle() : null,
          registration != null ? registration.getYear() : null,
          registration != null ? registration.getSubmittedAt() : null
        );
      })
      .sorted(Comparator.comparing(LecturerApprovalQueueRowDto::registrationSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
      .toList();
  }

  @PostMapping("/approvals/{caseId}/approve")
  public ResponseEntity<?> approveRegistration(@PathVariable Long caseId) {
    User me = currentUser.requireCurrentUser();
    PublicationCase c = workflowGates.requireCase(caseId);
    CaseSupervisor supervisor = workflowGates.ensureLecturerCanApproveRegistration(me, c);

    supervisor.approve();
    caseSupervisors.save(supervisor);

    List<CaseSupervisor> supervisors = caseSupervisors.findByCaseId(c.getId());
    boolean anyRejected = supervisors.stream().anyMatch(s -> s.getRejectedAt() != null);
    boolean allApproved = supervisors.stream().allMatch(s -> s.getApprovedAt() != null);
    CaseStatus nextStatus = anyRejected
      ? CaseStatus.REJECTED
      : (allApproved ? CaseStatus.REGISTRATION_APPROVED : CaseStatus.REGISTRATION_PENDING);

    if (nextStatus != c.getStatus()) {
      c.setStatus(nextStatus);
      cases.save(c);

      PublicationRegistration registration = registrations.findByPublicationCase(c).orElse(null);
      if (registration != null) {
        if (nextStatus == CaseStatus.REGISTRATION_APPROVED) {
          registration.setSupervisorDecisionAt(Instant.now());
          registration.setSupervisorDecisionNote("Approved by all supervisors");
          registrations.save(registration);
        } else if (nextStatus == CaseStatus.REJECTED) {
          registration.setSupervisorDecisionAt(Instant.now());
          registration.setSupervisorDecisionNote(supervisor.getDecisionNote());
          registrations.save(registration);
        }
      }
    }

    auditEvents.log(
      c.getId(),
      me,
      Role.LECTURER,
      AuditEventType.SUPERVISOR_APPROVED_REGISTRATION,
      "Supervisor approved registration"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/approvals/{caseId}/reject")
  public ResponseEntity<?> rejectRegistration(@PathVariable Long caseId, @RequestBody DecisionRequest req) {
    if (!hasText(req.getNote())) {
      throw new ResponseStatusException(BAD_REQUEST, "Rejection note is required");
    }

    User me = currentUser.requireCurrentUser();
    PublicationCase c = workflowGates.requireCase(caseId);
    CaseSupervisor supervisor = workflowGates.ensureLecturerCanApproveRegistration(me, c);

    supervisor.reject(req.getNote());
    caseSupervisors.save(supervisor);

    c.setStatus(CaseStatus.REJECTED);
    cases.save(c);

    PublicationRegistration registration = registrations.findByPublicationCase(c)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Registration not found"));
    registration.setSupervisorDecisionAt(Instant.now());
    registration.setSupervisorDecisionNote(req.getNote());
    registrations.save(registration);

    auditEvents.log(
      c.getId(),
      me,
      Role.LECTURER,
      AuditEventType.SUPERVISOR_REJECTED_REGISTRATION,
      req.getNote()
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @GetMapping("/review")
  public List<PublicationCase> reviewQueue() {
    User me = currentUser.requireCurrentUser();
    return caseSupervisors.findByLecturer(me).stream()
      .map(CaseSupervisor::getPublicationCase)
      .filter(c -> c.getStatus() == CaseStatus.UNDER_SUPERVISOR_REVIEW || c.getStatus() == CaseStatus.NEEDS_REVISION_SUPERVISOR)
      .toList();
  }

  @GetMapping("/pending-supervisor")
  public List<LecturerStudentGroupDto> pendingSupervisor(@RequestParam(required = false) Integer year) {
    User me = currentUser.requireCurrentUser();
    Set<CaseStatus> statuses = Set.of(
      CaseStatus.UNDER_SUPERVISOR_REVIEW,
      CaseStatus.NEEDS_REVISION_SUPERVISOR,
      CaseStatus.READY_TO_FORWARD
    );
    return groupCasesByStudent(filterCasesByStatusAndYear(me, statuses, year));
  }

  @GetMapping("/library-tracking")
  public List<LecturerStudentGroupDto> libraryTracking(@RequestParam(required = false) Integer year) {
    User me = currentUser.requireCurrentUser();
    Set<CaseStatus> statuses = Set.of(
      CaseStatus.FORWARDED_TO_LIBRARY,
      CaseStatus.UNDER_LIBRARY_REVIEW,
      CaseStatus.NEEDS_REVISION_LIBRARY,
      CaseStatus.APPROVED_FOR_CLEARANCE,
      CaseStatus.CLEARANCE_SUBMITTED,
      CaseStatus.CLEARANCE_APPROVED,
      CaseStatus.READY_TO_PUBLISH,
      CaseStatus.PUBLISHED
    );
    return groupCasesByStudent(filterCasesByStatusAndYear(me, statuses, year));
  }

  @GetMapping("/my-students")
  public List<LecturerStudentGroupDto> myStudents(@RequestParam(required = false) Integer year) {
    User me = currentUser.requireCurrentUser();
    return groupCasesByStudent(filterCasesByStatusAndYear(me, null, year));
  }

  @PostMapping("/cases/{caseId}/comment")
  public ResponseEntity<?> addComment(@PathVariable Long caseId, @RequestBody CommentRequest req) {
    PublicationCase c = supervisedCase(caseId);
    if (!hasText(req.getBody())) {
      throw new ResponseStatusException(BAD_REQUEST, "Comment body is required");
    }
    User me = currentUser.requireCurrentUser();
    WorkflowComment comment = comments.save(WorkflowComment.builder()
      .publicationCase(c)
      .author(me)
      .authorRole(Role.LECTURER)
      .authorEmail(me.getEmail())
      .body(req.getBody())
      .build());

    auditEvents.log(
      c.getId(),
      comment.getSubmissionVersion() != null ? comment.getSubmissionVersion().getId() : null,
      me,
      Role.LECTURER,
      AuditEventType.FEEDBACK_ADDED,
      summarizeComment(req.getBody(), "Lecturer feedback posted")
    );

    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/cases/{caseId}/request-revision")
  public ResponseEntity<?> requestRevision(@PathVariable Long caseId, @RequestBody RevisionRequest req) {
    if (!hasText(req.getReason())) {
      throw new ResponseStatusException(BAD_REQUEST, "Revision reason is required");
    }

    User me = currentUser.requireCurrentUser();
    PublicationCase c = workflowGates.requireCase(caseId);
    workflowGates.ensureLecturerCanRequestRevision(me, c);

    c.setStatus(CaseStatus.NEEDS_REVISION_SUPERVISOR);
    cases.save(c);

    WorkflowComment comment = comments.save(WorkflowComment.builder()
      .publicationCase(c)
      .author(me)
      .authorRole(Role.LECTURER)
      .authorEmail(me.getEmail())
      .body(req.getReason())
      .build());

    auditEvents.log(
      c.getId(),
      me,
      Role.LECTURER,
      AuditEventType.SUPERVISOR_REQUESTED_REVISION,
      req.getReason()
    );
    auditEvents.log(
      c.getId(),
      comment.getSubmissionVersion() != null ? comment.getSubmissionVersion().getId() : null,
      me,
      Role.LECTURER,
      AuditEventType.FEEDBACK_ADDED,
      summarizeComment(req.getReason(), "Lecturer revision feedback posted")
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/cases/{caseId}/mark-ready")
  public ResponseEntity<?> markReady(@PathVariable Long caseId) {
    User me = currentUser.requireCurrentUser();
    PublicationCase c = workflowGates.requireCase(caseId);
    workflowGates.ensureLecturerCanMarkReady(me, c);

    c.setStatus(CaseStatus.READY_TO_FORWARD);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      me,
      Role.LECTURER,
      AuditEventType.SUPERVISOR_MARKED_READY,
      "Supervisor marked submission ready for library"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/cases/{caseId}/forward-to-library")
  public ResponseEntity<?> forwardToLibrary(@PathVariable Long caseId) {
    // Backward-compatible alias that keeps the transition logic centralized.
    return approveAndForward(caseId);
  }

  @PostMapping("/cases/{caseId}/approve-and-forward")
  public ResponseEntity<?> approveAndForward(@PathVariable Long caseId) {
    User me = currentUser.requireCurrentUser();
    PublicationCase c = workflowGates.requireCase(caseId);

    if (c.getStatus() == CaseStatus.UNDER_SUPERVISOR_REVIEW) {
      workflowGates.ensureLecturerCanMarkReady(me, c);
      c.setStatus(CaseStatus.READY_TO_FORWARD);
      cases.save(c);
      auditEvents.log(
        c.getId(),
        me,
        Role.LECTURER,
        AuditEventType.SUPERVISOR_MARKED_READY,
        "Supervisor marked submission ready for library"
      );
    }

    workflowGates.ensureLecturerCanForward(me, c);
    c.setStatus(CaseStatus.FORWARDED_TO_LIBRARY);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      me,
      Role.LECTURER,
      AuditEventType.SUPERVISOR_FORWARDED_TO_LIBRARY,
      "Supervisor approved and forwarded case to library"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @GetMapping("/students")
  public List<Map<String, Object>> students() {
    User me = currentUser.requireCurrentUser();
    return caseSupervisors.findByLecturer(me).stream()
      .map(CaseSupervisor::getPublicationCase)
      .sorted(Comparator.comparing(PublicationCase::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
      .map(c -> Map.<String, Object>of(
        "caseId", c.getId(),
        "studentId", c.getStudent().getId(),
        "status", c.getStatus(),
        "type", c.getType()
      ))
      .toList();
  }

  @GetMapping("/cases/{caseId}/timeline")
  public List<TimelineItemDto> caseTimeline(@PathVariable Long caseId) {
    PublicationCase c = supervisedCase(caseId);
    return timelineService.buildTimeline(c);
  }

  @GetMapping("/cases/{caseId}/submissions")
  public List<SubmissionVersion> caseSubmissions(@PathVariable Long caseId) {
    PublicationCase c = supervisedCase(caseId);
    return submissionVersions.findByPublicationCaseOrderByVersionNumberDesc(c);
  }

  @GetMapping("/cases/{caseId}/submissions/latest/download")
  public ResponseEntity<Resource> downloadLatestSubmission(@PathVariable Long caseId) {
    PublicationCase c = supervisedCase(caseId);
    SubmissionVersion latest = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No submissions found"));
    return downloadSubmissionInternal(c, latest);
  }

  @GetMapping("/cases/{caseId}/submissions/{submissionId}/download")
  public ResponseEntity<Resource> downloadSubmission(@PathVariable Long caseId, @PathVariable Long submissionId) {
    PublicationCase c = supervisedCase(caseId);
    SubmissionVersion submission = submissionVersions.findById(submissionId)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Submission not found"));
    if (!submission.getPublicationCase().getId().equals(c.getId())) {
      throw new ResponseStatusException(BAD_REQUEST, "Submission does not belong to this case");
    }
    return downloadSubmissionInternal(c, submission);
  }

  private ResponseEntity<Resource> downloadSubmissionInternal(PublicationCase c, SubmissionVersion submission) {
    try {
      String storedKey = submission.getFilePath();
      if (!storageService.exists(storedKey)) {
        throw new ResponseStatusException(NOT_FOUND, "Submission file is missing");
      }

      String contentType = submission.getContentType();
      MediaType mediaType = hasText(contentType)
        ? MediaType.parseMediaType(contentType)
        : MediaType.APPLICATION_OCTET_STREAM;

      String filename = sanitizeFilename(submission.getOriginalFilename(), "submission-" + submission.getId() + ".pdf");
      Resource resource = storageService.openAsResource(storedKey);
      ResponseEntity.BodyBuilder response = ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store");

      if (submission.getFileSize() != null && submission.getFileSize() > 0) {
        response.contentLength(submission.getFileSize());
      }

      return response.body(resource);
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (IOException ex) {
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to read submission file", ex);
    }
  }

  private PublicationCase supervisedCase(Long caseId) {
    User me = currentUser.requireCurrentUser();
    return workflowGates.requireSupervisedCase(me, caseId);
  }

  private List<PublicationCase> filterCasesByStatusAndYear(User lecturer, Set<CaseStatus> statuses, Integer year) {
    List<PublicationCase> supervisedCases = caseSupervisors.findByLecturer(lecturer).stream()
      .map(CaseSupervisor::getPublicationCase)
      .distinct()
      .toList();

    Map<Long, PublicationRegistration> registrationByCase = registrations.findByPublicationCaseIn(supervisedCases).stream()
      .collect(Collectors.toMap(r -> r.getPublicationCase().getId(), r -> r));

    return supervisedCases.stream()
      .filter(c -> statuses == null || statuses.contains(c.getStatus()))
      .filter(c -> {
        if (year == null) {
          return true;
        }
        PublicationRegistration registration = registrationByCase.get(c.getId());
        return registration != null && Objects.equals(registration.getYear(), year);
      })
      .sorted(Comparator.comparing(PublicationCase::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
      .toList();
  }

  private List<LecturerStudentGroupDto> groupCasesByStudent(List<PublicationCase> publicationCases) {
    if (publicationCases.isEmpty()) {
      return List.of();
    }

    Map<Long, PublicationRegistration> registrationByCase = registrations.findByPublicationCaseIn(publicationCases).stream()
      .collect(Collectors.toMap(r -> r.getPublicationCase().getId(), r -> r));
    Map<Long, StudentProfile> profileByUser = loadStudentProfiles(publicationCases);

    Map<Long, List<LecturerCaseWorkItemDto>> grouped = new LinkedHashMap<>();
    for (PublicationCase c : publicationCases) {
      PublicationRegistration registration = registrationByCase.get(c.getId());
      grouped.computeIfAbsent(c.getStudent().getId(), key -> new ArrayList<>())
        .add(toCaseWorkItem(c, registration));
    }

    return grouped.entrySet().stream()
      .map(entry -> {
        Long studentUserId = entry.getKey();
        User student = publicationCases.stream()
          .map(PublicationCase::getStudent)
          .filter(user -> user.getId().equals(studentUserId))
          .findFirst()
          .orElseThrow();
        StudentProfile profile = profileByUser.get(studentUserId);
        return new LecturerStudentGroupDto(
          studentUserId,
          student.getEmail(),
          profile != null && hasText(profile.getName()) ? profile.getName() : student.getEmail(),
          profile != null ? profile.getStudentId() : null,
          profile != null ? profile.getFaculty() : null,
          profile != null ? profile.getProgram() : null,
          entry.getValue()
        );
      })
      .sorted(Comparator.comparing(LecturerStudentGroupDto::studentName, Comparator.nullsLast(String::compareToIgnoreCase)))
      .toList();
  }

  private LecturerCaseWorkItemDto toCaseWorkItem(PublicationCase c, PublicationRegistration registration) {
    Instant latestSubmissionAt = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .map(SubmissionVersion::getCreatedAt)
      .orElse(null);

    Instant lastLecturerFeedbackAt = comments.findTopByPublicationCaseAndAuthorRoleOrderByCreatedAtDesc(c, Role.LECTURER)
      .map(WorkflowComment::getCreatedAt)
      .orElse(null);

    Instant lecturerForwardedAt = auditEventRepository
      .findTopByCaseIdAndEventTypeOrderByCreatedAtDesc(c.getId(), AuditEventType.SUPERVISOR_FORWARDED_TO_LIBRARY)
      .map(AuditEvent::getCreatedAt)
      .orElse(null);

    Instant lastLibraryFeedbackAt = comments.findTopByPublicationCaseAndAuthorRoleOrderByCreatedAtDesc(c, Role.ADMIN)
      .map(WorkflowComment::getCreatedAt)
      .orElse(null);

    Instant libraryApprovedAt = auditEventRepository
      .findTopByCaseIdAndEventTypeOrderByCreatedAtDesc(c.getId(), AuditEventType.LIBRARY_APPROVED_FOR_CLEARANCE)
      .map(AuditEvent::getCreatedAt)
      .orElse(null);

    return new LecturerCaseWorkItemDto(
      c.getId(),
      c.getType(),
      c.getStatus(),
      c.getUpdatedAt(),
      registration != null ? registration.getTitle() : null,
      registration != null ? registration.getYear() : null,
      latestSubmissionAt,
      lastLecturerFeedbackAt,
      lecturerForwardedAt,
      lastLibraryFeedbackAt,
      libraryApprovedAt
    );
  }

  private Map<Long, StudentProfile> loadStudentProfiles(List<PublicationCase> publicationCases) {
    List<Long> studentIds = publicationCases.stream()
      .map(c -> c.getStudent().getId())
      .distinct()
      .toList();
    return studentProfiles.findByUserIdIn(studentIds).stream()
      .collect(Collectors.toMap(StudentProfile::getUserId, profile -> profile));
  }

  private static String summarizeComment(String body, String fallback) {
    if (!hasText(body)) {
      return fallback;
    }
    String trimmed = body.trim();
    return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
  }

  private static String sanitizeFilename(String candidate, String fallback) {
    String value = hasText(candidate) ? candidate.trim() : fallback;
    return value.replaceAll("[\\\\/\\r\\n\\t]", "_");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @Data
  public static class CommentRequest {
    private String body;
  }

  @Data
  public static class RevisionRequest {
    private String reason;
  }

  @Data
  public static class DecisionRequest {
    private String note;
  }
}
