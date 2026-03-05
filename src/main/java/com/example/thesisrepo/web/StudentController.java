package com.example.thesisrepo.web;

import com.example.thesisrepo.profile.LecturerProfile;
import com.example.thesisrepo.profile.LecturerProfileRepository;
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
import com.example.thesisrepo.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

  private final PublicationCaseRepository cases;
  private final PublicationRegistrationRepository registrations;
  private final CaseSupervisorRepository caseSupervisors;
  private final SubmissionVersionRepository submissionVersions;
  private final WorkflowCommentRepository comments;
  private final ChecklistResultRepository checklistResults;
  private final ClearanceFormRepository clearances;
  private final LecturerProfileRepository lecturerProfiles;
  private final StudentProfileRepository studentProfiles;
  private final UserRepository users;
  private final CurrentUserService currentUser;
  private final StorageService storage;
  private final PublicationWorkflowGateService workflowGates;
  private final AuditEventService auditEvents;
  private final CaseTimelineService timelineService;

  @GetMapping("/cases")
  public List<Map<String, Object>> listCases() {
    User me = currentUser.requireCurrentUser();
    return cases.findByStudentOrderByUpdatedAtDesc(me).stream().map(this::toCaseSummary).toList();
  }

  @GetMapping("/supervisors")
  public List<SupervisorDto> listSupervisors() {
    User me = currentUser.requireCurrentUser();
    StudentProfile studentProfile = studentProfiles.findByUserId(me.getId()).orElse(null);
    String studentProgram = normalize(studentProfile != null ? studentProfile.getProgram() : null);
    String studentFaculty = normalize(studentProfile != null ? studentProfile.getFaculty() : null);

    return users.findByRole(Role.LECTURER).stream()
      .filter(lecturer -> isEligibleSupervisor(lecturer.getId(), studentProgram, studentFaculty))
      .map(this::toSupervisorDto)
      .toList();
  }

  @PostMapping("/registrations")
  public ResponseEntity<?> createRegistration(@RequestBody CreateRegistrationRequest req) {
    User me = currentUser.requireCurrentUser();
    StudentProfile studentProfile = studentProfiles.findByUserId(me.getId()).orElse(null);
    if (studentProfile == null || normalize(studentProfile.getProgram()).isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "Student profile must include study program before selecting supervisors");
    }
    String studentProgram = normalize(studentProfile != null ? studentProfile.getProgram() : null);
    String studentFaculty = normalize(studentProfile != null ? studentProfile.getFaculty() : null);

    User supervisor = resolveRequestedSupervisor(req);
    if (supervisor.getRole() != Role.LECTURER) {
      throw new ResponseStatusException(BAD_REQUEST, "Supervisor must be a lecturer.");
    }
    LecturerProfile lecturerProfile = lecturerProfiles.findByUserId(supervisor.getId())
      .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Supervisor not found."));

    boolean sameProgram = normalize(lecturerProfile.getDepartment()).equals(studentProgram);
    boolean sameFaculty = studentFaculty.isBlank() || normalize(lecturerProfile.getFaculty()).equals(studentFaculty);
    if (!sameProgram || !sameFaculty) {
      throw new ResponseStatusException(BAD_REQUEST, "Supervisor must be from the same study program.");
    }

    PublicationCase c = cases.save(PublicationCase.builder()
      .student(me)
      .type(req.getType())
      .status(CaseStatus.REGISTRATION_DRAFT)
      .build());

    registrations.save(PublicationRegistration.builder()
      .publicationCase(c)
      .title(req.getTitle())
      .year(req.getYear())
      .articlePublishIn(req.getArticlePublishIn())
      .faculty(req.getFaculty())
      .studentIdNumber(req.getStudentIdNumber())
      .authorName(req.getAuthorName())
      .build());

    caseSupervisors.save(CaseSupervisor.builder().publicationCase(c).lecturer(supervisor).build());

    auditEvents.log(
      c.getId(),
      me,
      Role.STUDENT,
      AuditEventType.REGISTRATION_DRAFT_SAVED,
      "Registration draft created"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PutMapping("/registrations/{caseId}")
  public ResponseEntity<?> updateRegistration(@PathVariable Long caseId, @RequestBody UpdateRegistrationRequest req) {
    PublicationCase c = ownedCase(caseId);
    workflowGates.ensureRegistrationEditable(c);

    PublicationRegistration registration = registrations.findByPublicationCase(c)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Registration not found"));

    registration.setTitle(req.getTitle());
    registration.setYear(req.getYear());
    registration.setArticlePublishIn(req.getArticlePublishIn());
    registration.setFaculty(req.getFaculty());
    registration.setStudentIdNumber(req.getStudentIdNumber());
    registration.setAuthorName(req.getAuthorName());
    registrations.save(registration);

    auditEvents.log(
      c.getId(),
      currentUser.requireCurrentUser(),
      Role.STUDENT,
      AuditEventType.REGISTRATION_DRAFT_SAVED,
      "Registration draft updated"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/registrations/{caseId}/submit")
  public ResponseEntity<?> submitRegistration(@PathVariable Long caseId, @RequestBody SubmitRegistrationRequest req) {
    User me = currentUser.requireCurrentUser();
    PublicationCase c = ownedCase(caseId);
    workflowGates.ensureRegistrationSubmittable(c);

    PublicationRegistration registration = registrations.findByPublicationCase(c)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Registration not found"));
    if (!req.isPermissionAccepted()) {
      throw new ResponseStatusException(BAD_REQUEST, "Permission must be accepted");
    }

    List<CaseSupervisor> supervisors = caseSupervisors.findByPublicationCase(c);
    supervisors.forEach(supervisor -> {
      supervisor.setApprovedAt(null);
      supervisor.setRejectedAt(null);
      supervisor.setDecisionNote(null);
    });
    caseSupervisors.saveAll(supervisors);

    registration.setPermissionAcceptedAt(Instant.now());
    registration.setSubmittedAt(Instant.now());
    registrations.save(registration);

    c.setStatus(CaseStatus.REGISTRATION_PENDING);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      me,
      Role.STUDENT,
      AuditEventType.REGISTRATION_SUBMITTED,
      "Registration submitted for supervisor approval"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @GetMapping("/cases/{caseId}")
  public ResponseEntity<?> caseDetail(@PathVariable Long caseId) {
    PublicationCase c = ownedCase(caseId);
    PublicationRegistration reg = registrations.findByPublicationCase(c).orElse(null);
    List<SubmissionVersion> versions = submissionVersions.findByPublicationCaseOrderByVersionNumberDesc(c);
    List<WorkflowComment> caseComments = comments.findByPublicationCaseOrderByCreatedAtAsc(c);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("case", toCaseSummary(c));
    payload.put("registration", reg);
    payload.put("versions", versions);
    payload.put("comments", caseComments);
    payload.put("clearance", clearances.findByPublicationCase(c).orElse(null));
    payload.put("timeline", timelineService.buildTimeline(c));
    return ResponseEntity.ok(payload);
  }

  @PostMapping(value = "/cases/{caseId}/submissions", consumes = "multipart/form-data")
  public ResponseEntity<?> uploadSubmission(
    @PathVariable Long caseId,
    @RequestPart("file") MultipartFile file,
    @RequestPart(value = "meta", required = false) SubmissionMeta meta
  ) {
    User me = currentUser.requireCurrentUser();
    PublicationCase c = ownedCase(caseId);
    workflowGates.ensureStudentCanUploadSubmission(c);
    CaseStatus previousStatus = c.getStatus();

    int nextVersion = workflowGates.nextSubmissionVersion(c);
    ChecklistTemplate checklistTemplate = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .map(SubmissionVersion::getChecklistTemplate)
      .orElseGet(() -> workflowGates.requireActiveTemplateForCaseType(c.getType()));

    String storedPath;
    try {
      storedPath = storage.saveDocument(file);
    } catch (Exception ex) {
      throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
    }

    SubmissionVersion version = submissionVersions.save(SubmissionVersion.builder()
      .publicationCase(c)
      .versionNumber(nextVersion)
      .filePath(storedPath)
      .originalFilename(file.getOriginalFilename())
      .contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"))
      .fileSize(file.getSize())
      .metadataTitle(meta != null ? meta.getMetadataTitle() : null)
      .metadataAuthors(meta != null ? meta.getMetadataAuthors() : null)
      .metadataKeywords(meta != null ? meta.getMetadataKeywords() : null)
      .metadataFaculty(meta != null ? meta.getMetadataFaculty() : null)
      .metadataYear(meta != null ? meta.getMetadataYear() : null)
      .abstractText(meta != null ? meta.getAbstractText() : null)
      .checklistTemplate(checklistTemplate)
      .status(SubmissionStatus.SUBMITTED)
      .build());

    c.setStatus(workflowGates.nextStatusAfterStudentUpload(previousStatus));
    cases.save(c);

    auditEvents.log(
      c.getId(),
      version.getId(),
      me,
      Role.STUDENT,
      AuditEventType.SUBMISSION_UPLOADED,
      "Uploaded submission v" + version.getVersionNumber() + " (" + version.getOriginalFilename() + ")"
    );

    return ResponseEntity.ok(Map.of("submissionId", version.getId(), "version", version.getVersionNumber()));
  }

  @GetMapping("/cases/{caseId}/submissions")
  public List<SubmissionVersion> submissions(@PathVariable Long caseId) {
    PublicationCase c = ownedCase(caseId);
    return submissionVersions.findByPublicationCaseOrderByVersionNumberDesc(c);
  }

  @GetMapping("/cases/{caseId}/checklist-results")
  public ResponseEntity<?> checklistResults(@PathVariable Long caseId) {
    PublicationCase c = ownedCase(caseId);
    SubmissionVersion latest = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No submissions"));

    return ResponseEntity.ok(checklistResults.findBySubmissionVersion(latest));
  }

  @PostMapping("/cases/{caseId}/clearance")
  public ResponseEntity<?> submitClearance(@PathVariable Long caseId, @RequestBody ClearanceRequest req) {
    User me = currentUser.requireCurrentUser();
    PublicationCase c = ownedCase(caseId);
    workflowGates.ensureClearanceSubmittable(c);

    ClearanceForm clearance = clearances.findByPublicationCase(c).orElseGet(() -> ClearanceForm.builder()
      .publicationCase(c)
      .status(ClearanceStatus.DRAFT)
      .build());

    clearance.setStatus(ClearanceStatus.SUBMITTED);
    clearance.setSubmittedAt(Instant.now());
    clearance.setNote(req.getNote());
    clearances.save(clearance);

    c.setStatus(CaseStatus.CLEARANCE_SUBMITTED);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      me,
      Role.STUDENT,
      AuditEventType.CLEARANCE_SUBMITTED,
      "Student submitted clearance form"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  private PublicationCase ownedCase(Long caseId) {
    User me = currentUser.requireCurrentUser();
    return workflowGates.requireOwnedCase(me, caseId);
  }

  private Map<String, Object> toCaseSummary(PublicationCase c) {
    PublicationRegistration registration = registrations.findByPublicationCase(c).orElse(null);
    return Map.of(
      "id", c.getId(),
      "type", c.getType(),
      "status", c.getStatus(),
      "title", registration != null ? registration.getTitle() : null,
      "updatedAt", c.getUpdatedAt(),
      "createdAt", c.getCreatedAt()
    );
  }

  private SupervisorDto toSupervisorDto(User user) {
    LecturerProfile profile = lecturerProfiles.findByUserId(user.getId()).orElse(null);
    String name = profile != null && profile.getName() != null && !profile.getName().isBlank()
      ? profile.getName()
      : user.getEmail();
    return new SupervisorDto(
      user.getId(),
      user.getEmail(),
      name,
      profile != null ? profile.getFaculty() : null,
      profile != null ? profile.getDepartment() : null
    );
  }

  private boolean isEligibleSupervisor(Long lecturerUserId, String studentProgram, String studentFaculty) {
    if (studentProgram.isBlank()) {
      return false;
    }

    LecturerProfile profile = lecturerProfiles.findByUserId(lecturerUserId).orElse(null);
    if (profile == null) {
      return false;
    }
    if (!normalize(profile.getDepartment()).equals(studentProgram)) {
      return false;
    }
    return studentFaculty.isBlank() || normalize(profile.getFaculty()).equals(studentFaculty);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private User resolveRequestedSupervisor(CreateRegistrationRequest req) {
    String supervisorEmail = normalize(req.getSupervisorEmail());
    if (!supervisorEmail.isBlank()) {
      return users.findByEmail(supervisorEmail)
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Supervisor not found."));
    }

    if (req.getSupervisorUserId() != null) {
      return users.findById(req.getSupervisorUserId())
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Supervisor not found."));
    }

    if (req.getSupervisorEmails() != null) {
      List<String> supervisorEmails = req.getSupervisorEmails().stream()
        .map(StudentController::normalize)
        .filter(email -> !email.isBlank())
        .distinct()
        .toList();
      if (supervisorEmails.isEmpty()) {
        throw new ResponseStatusException(BAD_REQUEST, "Supervisor is required.");
      }
      if (supervisorEmails.size() > 1) {
        throw new ResponseStatusException(BAD_REQUEST, "Only one supervisor is allowed.");
      }
      return users.findByEmail(supervisorEmails.get(0))
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Supervisor not found."));
    }

    if (req.getSupervisorUserIds() != null) {
      List<Long> supervisorUserIds = req.getSupervisorUserIds().stream()
        .filter(Objects::nonNull)
        .distinct()
        .toList();
      if (supervisorUserIds.isEmpty()) {
        throw new ResponseStatusException(BAD_REQUEST, "Supervisor is required.");
      }
      if (supervisorUserIds.size() > 1) {
        throw new ResponseStatusException(BAD_REQUEST, "Only one supervisor is allowed.");
      }
      return users.findById(supervisorUserIds.get(0))
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Supervisor not found."));
    }

    throw new ResponseStatusException(BAD_REQUEST, "Supervisor is required.");
  }

  public record SupervisorDto(
    Long id,
    String email,
    String name,
    String faculty,
    String department
  ) {}

  @Data
  public static class CreateRegistrationRequest {
    private PublicationType type = PublicationType.THESIS;
    @NotBlank
    private String title;
    private Integer year;
    private String articlePublishIn;
    private String faculty;
    private String studentIdNumber;
    private String authorName;

    private String supervisorEmail;
    private Long supervisorUserId;

    private List<Long> supervisorUserIds;
    private List<String> supervisorEmails;
  }

  @Data
  public static class UpdateRegistrationRequest {
    @NotBlank
    private String title;
    private Integer year;
    private String articlePublishIn;
    private String faculty;
    private String studentIdNumber;
    private String authorName;

    private String supervisorEmail;
    private Long supervisorUserId;
    private List<Long> supervisorUserIds;
    private List<String> supervisorEmails;
  }

  @Data
  public static class SubmitRegistrationRequest {
    private boolean permissionAccepted;
  }

  @Data
  public static class SubmissionMeta {
    private String metadataTitle;
    private String metadataAuthors;
    private String metadataKeywords;
    private String metadataFaculty;
    private Integer metadataYear;
    private String abstractText;
  }

  @Data
  public static class ClearanceRequest {
    private String note;
  }
}
