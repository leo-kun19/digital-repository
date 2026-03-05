package com.example.thesisrepo.web;

import com.example.thesisrepo.publication.*;
import com.example.thesisrepo.publication.repo.*;
import com.example.thesisrepo.profile.StudentProfile;
import com.example.thesisrepo.profile.StudentProfileRepository;
import com.example.thesisrepo.service.checklist.ChecklistImportService;
import com.example.thesisrepo.service.CurrentUserService;
import com.example.thesisrepo.service.StorageService;
import com.example.thesisrepo.service.workflow.AuditEventService;
import com.example.thesisrepo.service.workflow.CaseTimelineService;
import com.example.thesisrepo.service.workflow.PublicationWorkflowGateService;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import com.example.thesisrepo.web.dto.AdminCaseQueueDto;
import com.example.thesisrepo.web.dto.AdminPublishDetailDto;
import com.example.thesisrepo.web.dto.AdminPublishQueueDto;
import com.example.thesisrepo.web.dto.AdminRegistrationApprovalDto;
import com.example.thesisrepo.web.dto.AdminStudentGroupDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWorkflowController {

  private final PublicationCaseRepository cases;
  private final SubmissionVersionRepository submissionVersions;
  private final WorkflowCommentRepository comments;
  private final PublicationRegistrationRepository registrations;
  private final StudentProfileRepository studentProfiles;
  private final ChecklistTemplateRepository checklistTemplates;
  private final ChecklistItemV2Repository checklistItems;
  private final ChecklistResultRepository checklistResults;
  private final ClearanceFormRepository clearances;
  private final PublishedItemRepository publishedItems;
  private final ChecklistImportService checklistImportService;
  private final ObjectMapper objectMapper;
  private final CurrentUserService currentUser;
  private final PublicationWorkflowGateService workflowGates;
  private final AuditEventService auditEvents;
  private final CaseTimelineService timelineService;
  private final StorageService storageService;

  @GetMapping("/review")
  public List<PublicationCase> reviewQueue(
    @RequestParam(required = false) CaseStatus status,
    @RequestParam(required = false) PublicationType type
  ) {
    List<CaseStatus> defaultQueue = List.of(
      CaseStatus.FORWARDED_TO_LIBRARY,
      CaseStatus.UNDER_LIBRARY_REVIEW,
      CaseStatus.NEEDS_REVISION_LIBRARY
    );

    if (status != null && !defaultQueue.contains(status)) {
      return List.of();
    }

    List<CaseStatus> statuses = status != null ? List.of(status) : defaultQueue;
    return cases.findByStatusInOrderByUpdatedAtDesc(statuses).stream()
      .filter(c -> type == null || c.getType() == type)
      .toList();
  }

  @GetMapping("/registration-approvals")
  public List<AdminRegistrationApprovalDto> registrationApprovals() {
    List<PublicationCase> pending = cases.findByStatusInOrderByUpdatedAtDesc(List.of(CaseStatus.REGISTRATION_APPROVED));
    if (pending.isEmpty()) {
      return List.of();
    }

    Map<Long, PublicationRegistration> registrationByCase = registrations.findByPublicationCaseIn(pending).stream()
      .collect(Collectors.toMap(r -> r.getPublicationCase().getId(), r -> r));
    Map<Long, StudentProfile> profileByUser = loadStudentProfiles(pending);

    return pending.stream()
      .map(c -> {
        PublicationRegistration registration = registrationByCase.get(c.getId());
        StudentProfile profile = profileByUser.get(c.getStudent().getId());
        String studentName = profile != null && hasText(profile.getName()) ? profile.getName() : c.getStudent().getEmail();
        return new AdminRegistrationApprovalDto(
          c.getId(),
          registration != null ? registration.getTitle() : null,
          c.getType(),
          c.getStatus(),
          c.getUpdatedAt(),
          registration != null ? registration.getSubmittedAt() : null,
          c.getStudent().getId(),
          studentName,
          profile != null ? profile.getStudentId() : null,
          profile != null ? profile.getFaculty() : null,
          profile != null ? profile.getProgram() : null,
          c.getStudent().getEmail()
        );
      })
      .toList();
  }

  @PostMapping("/registration-approvals/{caseId}/approve")
  public ResponseEntity<?> approveRegistration(@PathVariable Long caseId) {
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    workflowGates.ensureAdminCanApproveRegistration(c);

    c.setStatus(CaseStatus.REGISTRATION_VERIFIED);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.LIBRARY_APPROVED_REGISTRATION,
      "Registration verified by library"
    );
    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/registration-approvals/{caseId}/reject")
  public ResponseEntity<?> rejectRegistration(@PathVariable Long caseId, @RequestBody DecisionRequest req) {
    if (!hasText(req.getReason())) {
      throw new ResponseStatusException(BAD_REQUEST, "Reason is required");
    }
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    workflowGates.ensureAdminCanRejectRegistration(c);

    c.setStatus(CaseStatus.REJECTED);
    cases.save(c);

    WorkflowComment comment = saveAdminComment(c, admin, req.getReason().trim());
    auditEvents.log(
      c.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.LIBRARY_REJECTED_REGISTRATION,
      comment.getBody()
    );
    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @GetMapping("/review-queue-grouped")
  public List<AdminStudentGroupDto> reviewQueueGrouped() {
    List<CaseStatus> statuses = List.of(
      CaseStatus.FORWARDED_TO_LIBRARY,
      CaseStatus.UNDER_LIBRARY_REVIEW,
      CaseStatus.NEEDS_REVISION_LIBRARY
    );
    List<PublicationCase> reviewCases = cases.findByStatusInOrderByUpdatedAtDesc(statuses);
    if (reviewCases.isEmpty()) {
      return List.of();
    }

    Map<Long, PublicationRegistration> registrationByCase = registrations.findByPublicationCaseIn(reviewCases).stream()
      .collect(Collectors.toMap(r -> r.getPublicationCase().getId(), r -> r));

    Map<Long, StudentProfile> profileByUser = loadStudentProfiles(reviewCases);
    Map<Long, List<AdminCaseQueueDto>> groupedCases = new HashMap<>();
    for (PublicationCase c : reviewCases) {
      PublicationRegistration registration = registrationByCase.get(c.getId());
      groupedCases.computeIfAbsent(c.getStudent().getId(), key -> new ArrayList<>())
        .add(toAdminCaseQueueDto(c, registration));
    }

    return groupedCases.entrySet().stream()
      .map(entry -> {
        Long studentUserId = entry.getKey();
        StudentProfile profile = profileByUser.get(studentUserId);
        User student = reviewCases.stream()
          .map(PublicationCase::getStudent)
          .filter(user -> user.getId().equals(studentUserId))
          .findFirst()
          .orElseThrow();
        return new AdminStudentGroupDto(
          studentUserId,
          profile != null ? profile.getName() : student.getEmail(),
          profile != null ? profile.getStudentId() : null,
          profile != null ? profile.getFaculty() : null,
          profile != null ? profile.getProgram() : null,
          entry.getValue()
        );
      })
      .sorted(Comparator.comparing(AdminStudentGroupDto::studentName, Comparator.nullsLast(String::compareToIgnoreCase)))
      .toList();
  }

  @GetMapping("/cases/{caseId}")
  public ResponseEntity<?> caseDetail(@PathVariable Long caseId) {
    PublicationCase c = getCase(caseId);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("case", c);
    payload.put("submissions", submissionVersions.findByPublicationCaseOrderByVersionNumberDesc(c));
    payload.put("comments", comments.findByPublicationCaseOrderByCreatedAtAsc(c));
    payload.put("clearance", clearances.findByPublicationCase(c).orElse(null));
    payload.put("timeline", timelineService.buildTimeline(c));
    return ResponseEntity.ok(payload);
  }

  @PostMapping("/cases/{caseId}/checklist-results")
  @Transactional
  public ResponseEntity<?> saveChecklistResults(@PathVariable Long caseId, @RequestBody ChecklistResultRequest req) {
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = workflowGates.requireCase(caseId);
    SubmissionVersion version = submissionVersions.findById(req.getSubmissionVersionId())
      .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Submission version not found"));
    workflowGates.ensureSubmissionBelongsToCase(version, c);
    Long templateId = version.getChecklistTemplate() != null ? version.getChecklistTemplate().getId() : null;

    checklistResults.deleteBySubmissionVersion(version);
    for (ChecklistEntry entry : req.getResults()) {
      ChecklistItemV2 item = checklistItems.findById(entry.getChecklistItemId())
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Checklist item not found"));
      if (templateId != null && !item.getTemplate().getId().equals(templateId)) {
        throw new ResponseStatusException(BAD_REQUEST, "Checklist item does not belong to this submission template version");
      }
      checklistResults.save(ChecklistResult.builder()
        .submissionVersion(version)
        .checklistItem(item)
        .passFail(entry.isPass() ? ReviewOutcome.PASS : ReviewOutcome.FAIL)
        .note(entry.getNote())
        .build());
    }

    version.setStatus(SubmissionStatus.UNDER_REVIEW);
    submissionVersions.save(version);
    c.setStatus(CaseStatus.UNDER_LIBRARY_REVIEW);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      version.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.LIBRARY_CHECKLIST_REVIEWED,
      "Checklist results saved for submission v" + version.getVersionNumber()
    );
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/cases/{caseId}/request-revision")
  public ResponseEntity<?> requestRevision(@PathVariable Long caseId, @RequestBody DecisionRequest req) {
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    if (!hasText(req.getReason()) && !latestSubmissionHasFailedItems(c)) {
      throw new ResponseStatusException(BAD_REQUEST, "Reason is required when no failed checklist item exists");
    }

    String commentBody = hasText(req.getReason()) ? req.getReason() : "Revision requested due to failed checklist items.";
    c.setStatus(CaseStatus.NEEDS_REVISION_LIBRARY);
    cases.save(c);
    WorkflowComment comment = saveAdminComment(c, admin, commentBody);

    auditEvents.log(
      c.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.LIBRARY_REQUESTED_REVISION,
      commentBody
    );
    auditEvents.log(
      c.getId(),
      comment.getSubmissionVersion() != null ? comment.getSubmissionVersion().getId() : null,
      admin,
      Role.ADMIN,
      AuditEventType.FEEDBACK_ADDED,
      summarizeComment(commentBody, "Admin feedback posted")
    );
    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/cases/{caseId}/approve")
  public ResponseEntity<?> approveCase(@PathVariable Long caseId) {
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    SubmissionVersion latest = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .orElseThrow(() -> new ResponseStatusException(CONFLICT, "No submission version found"));
    latest.setStatus(SubmissionStatus.APPROVED);
    submissionVersions.save(latest);

    c.setStatus(CaseStatus.APPROVED_FOR_CLEARANCE);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      latest.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.LIBRARY_APPROVED_FOR_CLEARANCE,
      "Library approved submission for clearance"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/cases/{caseId}/reject")
  public ResponseEntity<?> rejectCase(@PathVariable Long caseId, @RequestBody DecisionRequest req) {
    if (!hasText(req.getReason())) {
      throw new ResponseStatusException(BAD_REQUEST, "Reason is required");
    }
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    c.setStatus(CaseStatus.REJECTED);
    cases.save(c);
    WorkflowComment comment = saveAdminComment(c, admin, req.getReason());

    auditEvents.log(
      c.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.LIBRARY_REJECTED,
      req.getReason()
    );
    auditEvents.log(
      c.getId(),
      comment.getSubmissionVersion() != null ? comment.getSubmissionVersion().getId() : null,
      admin,
      Role.ADMIN,
      AuditEventType.FEEDBACK_ADDED,
      summarizeComment(req.getReason(), "Admin feedback posted")
    );
    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @GetMapping("/clearance")
  public List<PublicationCase> clearanceQueue() {
    return cases.findByStatusInOrderByUpdatedAtDesc(List.of(CaseStatus.CLEARANCE_SUBMITTED));
  }

  @PostMapping("/clearance/{caseId}/approve")
  public ResponseEntity<?> approveClearance(@PathVariable Long caseId) {
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    if (c.getStatus() != CaseStatus.CLEARANCE_SUBMITTED) {
      throw new ResponseStatusException(CONFLICT, "Case is not in clearance queue");
    }

    ClearanceForm form = clearances.findByPublicationCase(c)
      .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Clearance not submitted"));
    form.setStatus(ClearanceStatus.APPROVED);
    form.setApprovedAt(Instant.now());
    clearances.save(form);

    c.setStatus(CaseStatus.READY_TO_PUBLISH);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.CLEARANCE_APPROVED,
      "Library approved clearance"
    );

    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @PostMapping("/clearance/{caseId}/request-correction")
  public ResponseEntity<?> requestClearanceCorrection(@PathVariable Long caseId, @RequestBody DecisionRequest req) {
    if (!hasText(req.getReason())) {
      throw new ResponseStatusException(BAD_REQUEST, "Reason is required");
    }

    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    ClearanceForm form = clearances.findByPublicationCase(c)
      .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Clearance not submitted"));
    form.setStatus(ClearanceStatus.NEEDS_CORRECTION);
    form.setNote(req.getReason());
    clearances.save(form);

    c.setStatus(CaseStatus.APPROVED_FOR_CLEARANCE);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.CLEARANCE_CORRECTION_REQUESTED,
      req.getReason()
    );
    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @GetMapping("/publish")
  public List<AdminPublishQueueDto> publishQueue() {
    List<PublicationCase> publishCases = cases.findByStatusInOrderByUpdatedAtDesc(List.of(CaseStatus.READY_TO_PUBLISH));
    if (publishCases.isEmpty()) {
      return List.of();
    }

    Map<Long, PublicationRegistration> registrationByCase = registrations.findByPublicationCaseIn(publishCases).stream()
      .collect(Collectors.toMap(r -> r.getPublicationCase().getId(), r -> r));

    return publishCases.stream()
      .map(c -> new AdminPublishQueueDto(
        c.getId(),
        resolvePublishTitle(c, registrationByCase.get(c.getId())),
        c.getType(),
        c.getStatus(),
        c.getUpdatedAt()
      ))
      .toList();
  }

  @GetMapping("/publish/{caseId}")
  public AdminPublishDetailDto publishDetail(@PathVariable Long caseId) {
    PublicationCase c = getCase(caseId);
    PublicationRegistration registration = registrations.findByPublicationCase(c).orElse(null);
    SubmissionVersion latest = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c).orElse(null);

    AdminPublishDetailDto.Metadata metadata = new AdminPublishDetailDto.Metadata(
      latest != null ? latest.getMetadataTitle() : null,
      latest != null ? latest.getMetadataAuthors() : null,
      latest != null ? latest.getMetadataKeywords() : null,
      latest != null ? latest.getMetadataFaculty() : null,
      latest != null ? latest.getMetadataYear() : null,
      latest != null ? latest.getAbstractText() : null
    );

    AdminPublishDetailDto.SubmissionFile file = latest == null ? null : new AdminPublishDetailDto.SubmissionFile(
      latest.getId(),
      latest.getOriginalFilename(),
      latest.getCreatedAt(),
      latest.getFileSize(),
      "/api/admin/cases/" + caseId + "/file/latest"
    );

    return new AdminPublishDetailDto(
      c.getId(),
      resolvePublishTitle(c, registration),
      c.getType(),
      c.getStatus(),
      c.getUpdatedAt(),
      metadata,
      file
    );
  }

  @GetMapping("/cases/{caseId}/file/latest")
  public ResponseEntity<Resource> downloadLatestSubmission(@PathVariable Long caseId) {
    PublicationCase c = getCase(caseId);
    SubmissionVersion version = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No submission version found"));
    if (version.getFilePath() == null || version.getFilePath().isBlank()) {
      throw new ResponseStatusException(NOT_FOUND, "No file attached");
    }

    try {
      String storedKey = version.getFilePath();
      if (!storageService.exists(storedKey)) {
        throw new ResponseStatusException(NOT_FOUND, "File not found");
      }

      Resource resource = storageService.openAsResource(storedKey);
      String fileName = version.getOriginalFilename() != null && !version.getOriginalFilename().isBlank()
        ? version.getOriginalFilename()
        : defaultFilename(storedKey, "submission.pdf");
      return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .body(resource);
    } catch (Exception e) {
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Cannot read file", e);
    }
  }

  private static String defaultFilename(String storedKey, String fallback) {
    if (!hasText(storedKey)) {
      return fallback;
    }
    int idx = storedKey.lastIndexOf('/');
    String name = idx >= 0 ? storedKey.substring(idx + 1) : storedKey;
    return hasText(name) ? name : fallback;
  }

  @PostMapping("/publish/{caseId}")
  public ResponseEntity<?> publish(@PathVariable Long caseId) {
    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    SubmissionVersion latest = workflowGates.ensureAdminCanPublish(c);
    String studentProgram = studentProfiles.findByUserId(c.getStudent().getId())
      .map(StudentProfile::getProgram)
      .orElse(null);

    PublishedItem item = publishedItems.save(PublishedItem.builder()
      .publicationCase(c)
      .submissionVersion(latest)
      .publishedAt(Instant.now())
      .title(Optional.ofNullable(latest.getMetadataTitle()).orElse("Untitled"))
      .authors(Optional.ofNullable(latest.getMetadataAuthors()).orElse(c.getStudent().getEmail()))
      .authorName(Optional.ofNullable(latest.getMetadataAuthors()).orElse(c.getStudent().getEmail()))
      .faculty(latest.getMetadataFaculty())
      .program(studentProgram)
      .year(latest.getMetadataYear())
      .keywords(latest.getMetadataKeywords())
      .abstractText(latest.getAbstractText())
      .build());

    c.setStatus(CaseStatus.PUBLISHED);
    cases.save(c);

    auditEvents.log(
      c.getId(),
      latest.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.PUBLISHED,
      "Published to repository"
    );
    return ResponseEntity.ok(Map.of("publishedId", item.getId(), "status", c.getStatus()));
  }

  @PostMapping("/publish/{caseId}/unpublish")
  @Transactional
  public ResponseEntity<?> unpublish(@PathVariable Long caseId, @RequestBody DecisionRequest req) {
    if (!hasText(req.getReason()) || req.getReason().trim().length() < 5) {
      throw new ResponseStatusException(BAD_REQUEST, "Reason is required (min 5 characters)");
    }

    User admin = currentUser.requireCurrentUser();
    PublicationCase c = getCase(caseId);
    if (c.getStatus() != CaseStatus.PUBLISHED) {
      throw new ResponseStatusException(CONFLICT, "Case is not published.");
    }

    publishedItems.deleteByPublicationCase_Id(caseId);
    c.setStatus(CaseStatus.NEEDS_REVISION_LIBRARY);
    cases.save(c);

    WorkflowComment comment = saveAdminComment(c, admin, req.getReason().trim());
    auditEvents.log(
      c.getId(),
      admin,
      Role.ADMIN,
      AuditEventType.UNPUBLISHED_FOR_CORRECTION,
      comment.getBody()
    );
    return ResponseEntity.ok(Map.of("caseId", c.getId(), "status", c.getStatus()));
  }

  @GetMapping("/checklists")
  public List<ChecklistTemplateSummaryResponse> checklists(@RequestParam("type") ChecklistScope type) {
    return checklistTemplates.findByPublicationTypeOrderByVersionDesc(type).stream()
      .map(this::toTemplateSummary)
      .toList();
  }

  // Backward-compatible endpoint used by existing review/detail pages that still need item payloads.
  @GetMapping("/checklists/full")
  public List<ChecklistTemplateResponse> checklistsFull(@RequestParam("type") ChecklistScope type) {
    return checklistTemplates.findByPublicationTypeOrderByVersionDesc(type).stream()
      .map(template -> new ChecklistTemplateResponse(template, checklistItems.findByTemplateOrderByOrderIndexAsc(template)))
      .toList();
  }

  @PostMapping("/checklists/{type}/create-empty")
  @Transactional
  public ResponseEntity<ChecklistTemplateSummaryResponse> createEmptyTemplate(@PathVariable ChecklistScope type) {
    int nextVersion = checklistTemplates.findTopByPublicationTypeOrderByVersionDesc(type)
      .map(ChecklistTemplate::getVersion).orElse(0) + 1;

    ChecklistTemplate created = checklistTemplates.save(ChecklistTemplate.builder()
      .publicationType(type)
      .version(nextVersion)
      .isActive(false)
      .build());

    return ResponseEntity.ok(toTemplateSummary(created));
  }

  @PostMapping("/checklists/{type}/new-draft")
  @Transactional
  public ResponseEntity<ChecklistTemplateSummaryResponse> createNewDraftTemplate(@PathVariable ChecklistScope type) {
    return createEmptyTemplate(type);
  }

  @PostMapping("/checklists/{type}/new-version")
  @Transactional
  public ResponseEntity<?> createNewVersion(@PathVariable ChecklistScope type) {
    ChecklistTemplateSummaryResponse created = createNewDraftTemplate(type).getBody();
    if (created == null) {
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to create checklist template");
    }
    return ResponseEntity.ok(Map.of("templateId", created.id(), "version", created.version()));
  }

  @PostMapping(value = "/checklists/{type}/import-xlsx", consumes = "multipart/form-data")
  @Transactional
  public ResponseEntity<ChecklistImportService.ImportSummary> importChecklist(
    @PathVariable ChecklistScope type,
    @RequestPart("file") MultipartFile file,
    @RequestParam(defaultValue = "false") boolean activate,
    @RequestParam(required = false) String sheetName
  ) {
    return ResponseEntity.ok(checklistImportService.importChecklist(type, file, activate, sheetName));
  }

  @GetMapping("/checklists/templates/{templateId}")
  public ResponseEntity<ChecklistTemplateResponse> checklistTemplateDetail(@PathVariable Long templateId) {
    ChecklistTemplate template = checklistTemplates.findById(templateId)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Template not found"));
    return ResponseEntity.ok(new ChecklistTemplateResponse(template, checklistItems.findByTemplateOrderByOrderIndexAsc(template)));
  }

  @DeleteMapping("/checklists/templates/{templateId}")
  @Transactional
  public ResponseEntity<?> deleteTemplate(@PathVariable Long templateId) {
    ChecklistTemplate template = checklistTemplates.findById(templateId)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Template not found"));
    if (checklistResults.existsByChecklistItem_Template_Id(templateId)) {
      throw new ResponseStatusException(BAD_REQUEST, "Cannot delete template used by checklist results");
    }

    checklistItems.deleteByTemplate(template);
    checklistTemplates.delete(template);
    return ResponseEntity.ok(Map.of("deleted", true, "templateId", templateId));
  }

  @PutMapping("/checklists/templates/{templateId}/items")
  @Transactional
  public ResponseEntity<?> replaceTemplateItems(@PathVariable Long templateId, @RequestBody JsonNode payload) {
    ChecklistTemplate template = checklistTemplates.findById(templateId)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Template not found"));
    if (template.isActive()) {
      throw new ResponseStatusException(BAD_REQUEST, "Cannot edit active template; create a new draft first.");
    }

    List<ReplaceItem> items = readReplaceItems(payload);
    validateTemplateItems(items);

    checklistItems.deleteByTemplate(template);
    List<ReplaceItem> normalized = normalizeOrder(items);
    int idx = 1;
    for (ReplaceItem item : normalized) {
      checklistItems.save(ChecklistItemV2.builder()
        .template(template)
        .orderIndex(idx++)
        .section(item.getSection() != null ? item.getSection().trim() : null)
        .itemText(item.getItemText().trim())
        .guidanceText(item.getGuidanceText() != null ? item.getGuidanceText().trim() : null)
        .isRequired(item.isRequired())
        .build());
    }

    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/checklists/templates/{templateId}/activate")
  @Transactional
  public ResponseEntity<?> activateTemplate(@PathVariable Long templateId) {
    ChecklistTemplate toActivate = checklistTemplates.findById(templateId)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Template not found"));
    int itemCount = checklistItems.findByTemplateOrderByOrderIndexAsc(toActivate).size();
    if (itemCount == 0) {
      throw new ResponseStatusException(BAD_REQUEST, "Template must have at least 1 item before activation");
    }

    checklistTemplates.findByPublicationTypeOrderByVersionDesc(toActivate.getPublicationType()).forEach(template -> {
      template.setActive(template.getId().equals(templateId));
      checklistTemplates.save(template);
    });

    return ResponseEntity.ok(Map.of("templateId", templateId, "active", true));
  }

  private PublicationCase getCase(Long caseId) {
    return workflowGates.requireCase(caseId);
  }

  private WorkflowComment saveAdminComment(PublicationCase c, User admin, String body) {
    return comments.save(WorkflowComment.builder()
      .publicationCase(c)
      .author(admin)
      .authorRole(Role.ADMIN)
      .authorEmail(admin.getEmail())
      .body(body)
      .build());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String summarizeComment(String body, String fallback) {
    if (!hasText(body)) {
      return fallback;
    }
    String trimmed = body.trim();
    return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
  }

  private Map<Long, StudentProfile> loadStudentProfiles(List<PublicationCase> casesToMap) {
    List<Long> userIds = casesToMap.stream()
      .map(c -> c.getStudent().getId())
      .distinct()
      .toList();
    return studentProfiles.findByUserIdIn(userIds).stream()
      .collect(Collectors.toMap(StudentProfile::getUserId, profile -> profile));
  }

  private AdminCaseQueueDto toAdminCaseQueueDto(PublicationCase c, PublicationRegistration registration) {
    Instant latestSubmissionAt = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .map(SubmissionVersion::getCreatedAt)
      .orElse(null);
    return new AdminCaseQueueDto(
      c.getId(),
      registration != null ? registration.getTitle() : null,
      c.getType(),
      c.getStatus(),
      c.getUpdatedAt(),
      latestSubmissionAt
    );
  }

  private boolean latestSubmissionHasFailedItems(PublicationCase c) {
    SubmissionVersion latest = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c).orElse(null);
    if (latest == null) {
      return false;
    }
    return checklistResults.findBySubmissionVersion(latest).stream()
      .anyMatch(result -> result.getPassFail() == ReviewOutcome.FAIL);
  }

  private ChecklistTemplateSummaryResponse toTemplateSummary(ChecklistTemplate template) {
    int itemCount = checklistItems.findByTemplateOrderByOrderIndexAsc(template).size();
    return new ChecklistTemplateSummaryResponse(
      template.getId(),
      template.getPublicationType(),
      template.getVersion(),
      template.isActive(),
      template.getCreatedAt(),
      itemCount
    );
  }

  private String resolvePublishTitle(PublicationCase c, PublicationRegistration registration) {
    if (registration != null && hasText(registration.getTitle())) {
      return registration.getTitle();
    }
    return submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .map(SubmissionVersion::getMetadataTitle)
      .filter(AdminWorkflowController::hasText)
      .orElse(null);
  }

  private static List<ReplaceItem> normalizeOrder(List<ReplaceItem> items) {
    if (items.stream().allMatch(item -> item.getOrderIndex() == null)) {
      return items;
    }
    return items.stream()
      .sorted(Comparator.comparing(item -> item.getOrderIndex() == null ? Integer.MAX_VALUE : item.getOrderIndex()))
      .toList();
  }

  private List<ReplaceItem> readReplaceItems(JsonNode payload) {
    if (payload == null || payload.isNull()) {
      return List.of();
    }

    JsonNode itemNode = payload.isArray() ? payload : payload.get("items");
    if (itemNode == null || !itemNode.isArray()) {
      throw new ResponseStatusException(BAD_REQUEST, "Expected checklist items array payload");
    }
    return objectMapper.convertValue(itemNode, objectMapper.getTypeFactory().constructCollectionType(List.class, ReplaceItem.class));
  }

  private static void validateTemplateItems(List<ReplaceItem> items) {
    Set<Integer> orderIndexes = new HashSet<>();
    for (ReplaceItem item : items) {
      if (item == null) {
        throw new ResponseStatusException(BAD_REQUEST, "Checklist item payload cannot be null");
      }
      if (!hasText(item.getItemText())) {
        throw new ResponseStatusException(BAD_REQUEST, "itemText is required");
      }
      if (item.getItemText().trim().length() > 500) {
        throw new ResponseStatusException(BAD_REQUEST, "itemText max length is 500");
      }
      if (item.getSection() != null && item.getSection().length() > 100) {
        throw new ResponseStatusException(BAD_REQUEST, "section max length is 100");
      }
      if (item.getGuidanceText() != null && item.getGuidanceText().length() > 2000) {
        throw new ResponseStatusException(BAD_REQUEST, "guidanceText max length is 2000");
      }
      if (item.getOrderIndex() != null && !orderIndexes.add(item.getOrderIndex())) {
        throw new ResponseStatusException(BAD_REQUEST, "orderIndex values must be unique");
      }
    }
  }

  public record ChecklistTemplateResponse(ChecklistTemplate template, List<ChecklistItemV2> items) {}
  public record ChecklistTemplateSummaryResponse(
    Long id,
    ChecklistScope publicationType,
    Integer version,
    boolean active,
    Instant createdAt,
    int itemCount
  ) {}

  @Data
  public static class ChecklistResultRequest {
    private Long submissionVersionId;
    private List<ChecklistEntry> results = new ArrayList<>();
  }

  @Data
  public static class ChecklistEntry {
    private Long checklistItemId;
    private boolean pass;
    private String note;
  }

  @Data
  public static class DecisionRequest {
    private String reason;
  }

  @Data
  public static class ReplaceItem {
    private Integer orderIndex;
    private String section;
    private String itemText;
    private String guidanceText;
    private boolean required;
  }
}
