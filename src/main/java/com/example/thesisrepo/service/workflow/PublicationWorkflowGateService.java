package com.example.thesisrepo.service.workflow;

import com.example.thesisrepo.publication.*;
import com.example.thesisrepo.publication.repo.CaseSupervisorRepository;
import com.example.thesisrepo.publication.repo.ChecklistTemplateRepository;
import com.example.thesisrepo.publication.repo.ClearanceFormRepository;
import com.example.thesisrepo.publication.repo.PublicationCaseRepository;
import com.example.thesisrepo.publication.repo.PublishedItemRepository;
import com.example.thesisrepo.publication.repo.SubmissionVersionRepository;
import com.example.thesisrepo.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class PublicationWorkflowGateService {

  private final PublicationCaseRepository cases;
  private final CaseSupervisorRepository caseSupervisors;
  private final SubmissionVersionRepository submissionVersions;
  private final ClearanceFormRepository clearances;
  private final PublishedItemRepository publishedItems;
  private final ChecklistTemplateRepository checklistTemplates;

  public PublicationCase requireCase(Long caseId) {
    return cases.findById(caseId)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Case not found"));
  }

  public PublicationCase requireOwnedCase(User student, Long caseId) {
    PublicationCase c = requireCase(caseId);
    if (!c.getStudent().getId().equals(student.getId())) {
      throw new ResponseStatusException(FORBIDDEN, "You do not own this case");
    }
    return c;
  }

  public PublicationCase requireSupervisedCase(User lecturer, Long caseId) {
    PublicationCase c = requireCase(caseId);
    if (!caseSupervisors.existsByPublicationCaseAndLecturer(c, lecturer)) {
      throw new ResponseStatusException(FORBIDDEN, "You are not assigned supervisor for this case");
    }
    return c;
  }

  public void ensureRegistrationEditable(PublicationCase c) {
    if (!(c.getStatus() == CaseStatus.REGISTRATION_DRAFT
      || c.getStatus() == CaseStatus.REJECTED)) {
      throw new ResponseStatusException(CONFLICT, "Registration can only be edited while draft/rejected");
    }
  }

  public void ensureRegistrationSubmittable(PublicationCase c) {
    if (!(c.getStatus() == CaseStatus.REGISTRATION_DRAFT
      || c.getStatus() == CaseStatus.REJECTED)) {
      throw new ResponseStatusException(CONFLICT, "Registration state does not allow submit");
    }
  }

  // Gate 1: Student upload requires registration approval stage.
  public void ensureStudentCanUploadSubmission(PublicationCase c) {
    if (c.getStatus() == CaseStatus.REGISTRATION_DRAFT
      || c.getStatus() == CaseStatus.REGISTRATION_PENDING
      || c.getStatus() == CaseStatus.REGISTRATION_APPROVED
      || c.getStatus() == CaseStatus.REJECTED) {
      throw new ResponseStatusException(BAD_REQUEST, "Registration must be verified by the library before submission.");
    }

    if (!(c.getStatus() == CaseStatus.REGISTRATION_VERIFIED
      || c.getStatus() == CaseStatus.NEEDS_REVISION_LIBRARY
      || c.getStatus() == CaseStatus.NEEDS_REVISION_SUPERVISOR)) {
      throw new ResponseStatusException(CONFLICT, "Submission upload is not allowed at the current workflow stage");
    }
  }

  public CaseStatus nextStatusAfterStudentUpload(CaseStatus previousStatus) {
    if (previousStatus == CaseStatus.NEEDS_REVISION_LIBRARY) {
      return CaseStatus.UNDER_LIBRARY_REVIEW;
    }
    return CaseStatus.UNDER_SUPERVISOR_REVIEW;
  }

  public void ensureClearanceSubmittable(PublicationCase c) {
    if (c.getStatus() != CaseStatus.APPROVED_FOR_CLEARANCE) {
      throw new ResponseStatusException(CONFLICT, "Case is not ready for clearance");
    }
  }

  // Gate 2: Lecturer must be assigned supervisor and case must be pending.
  public CaseSupervisor ensureLecturerCanApproveRegistration(User lecturer, PublicationCase c) {
    CaseSupervisor supervisor = caseSupervisors.findByPublicationCaseAndLecturer(c, lecturer)
      .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "You are not assigned supervisor for this case"));
    if (c.getStatus() != CaseStatus.REGISTRATION_PENDING) {
      throw new ResponseStatusException(CONFLICT, "Case is not pending supervisor approval");
    }
    if (!supervisor.isPendingDecision()) {
      throw new ResponseStatusException(CONFLICT, "You have already decided on this registration");
    }
    return supervisor;
  }

  public void ensureAdminCanApproveRegistration(PublicationCase c) {
    if (c.getStatus() != CaseStatus.REGISTRATION_APPROVED) {
      throw new ResponseStatusException(CONFLICT, "Case is not pending library registration approval");
    }
  }

  public void ensureAdminCanRejectRegistration(PublicationCase c) {
    if (c.getStatus() != CaseStatus.REGISTRATION_APPROVED) {
      throw new ResponseStatusException(CONFLICT, "Case is not pending library registration approval");
    }
  }

  // Gate 3: Lecturer forward requires READY_TO_FORWARD.
  public void ensureLecturerCanForward(User lecturer, PublicationCase c) {
    if (!caseSupervisors.existsByPublicationCaseAndLecturer(c, lecturer)) {
      throw new ResponseStatusException(FORBIDDEN, "You are not assigned supervisor for this case");
    }
    if (c.getStatus() != CaseStatus.READY_TO_FORWARD) {
      throw new ResponseStatusException(CONFLICT, "Case must be READY_TO_FORWARD first");
    }
  }

  public void ensureLecturerCanMarkReady(User lecturer, PublicationCase c) {
    if (!caseSupervisors.existsByPublicationCaseAndLecturer(c, lecturer)) {
      throw new ResponseStatusException(FORBIDDEN, "You are not assigned supervisor for this case");
    }
    if (c.getStatus() != CaseStatus.UNDER_SUPERVISOR_REVIEW) {
      throw new ResponseStatusException(CONFLICT, "Case must be under supervisor review");
    }
  }

  public void ensureLecturerCanRequestRevision(User lecturer, PublicationCase c) {
    if (!caseSupervisors.existsByPublicationCaseAndLecturer(c, lecturer)) {
      throw new ResponseStatusException(FORBIDDEN, "You are not assigned supervisor for this case");
    }
    if (c.getStatus() != CaseStatus.UNDER_SUPERVISOR_REVIEW) {
      throw new ResponseStatusException(CONFLICT, "Case must be under supervisor review");
    }
  }

  // Gate 4: Publish requires approved clearance and approved latest submission.
  public SubmissionVersion ensureAdminCanPublish(PublicationCase c) {
    if (c.getStatus() != CaseStatus.READY_TO_PUBLISH) {
      throw new ResponseStatusException(CONFLICT, "Case is not ready to publish");
    }

    ClearanceForm form = clearances.findByPublicationCase(c)
      .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Clearance not found"));
    if (form.getStatus() != ClearanceStatus.APPROVED) {
      throw new ResponseStatusException(CONFLICT, "Clearance must be approved before publish");
    }

    SubmissionVersion latest = submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .orElseThrow(() -> new ResponseStatusException(CONFLICT, "No submission version found"));
    if (latest.getStatus() != SubmissionStatus.APPROVED) {
      throw new ResponseStatusException(CONFLICT, "Submission must be approved before publish");
    }

    if (publishedItems.existsByPublicationCase_Id(c.getId())) {
      throw new ResponseStatusException(CONFLICT, "Case already published");
    }

    return latest;
  }

  public int nextSubmissionVersion(PublicationCase c) {
    return submissionVersions.findTopByPublicationCaseOrderByVersionNumberDesc(c)
      .map(version -> version.getVersionNumber() + 1)
      .orElse(1);
  }

  public void ensureSubmissionBelongsToCase(SubmissionVersion version, PublicationCase c) {
    if (!version.getPublicationCase().getId().equals(c.getId())) {
      throw new ResponseStatusException(BAD_REQUEST, "Submission does not belong to case");
    }
  }

  public void ensureTemplateEditable(ChecklistTemplate template) {
    if (template.isActive()) {
      throw new ResponseStatusException(CONFLICT, "Cannot edit active template directly. Create a new version first.");
    }
  }

  public ChecklistTemplate requireActiveTemplateForCaseType(PublicationType type) {
    ChecklistScope scope = switch (type) {
      case THESIS -> ChecklistScope.THESIS;
      case ARTICLE -> ChecklistScope.ARTICLE;
      case INTERNSHIP_REPORT -> ChecklistScope.INTERNSHIP_REPORT;
      case OTHER -> ChecklistScope.OTHER;
    };

    return checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(scope)
      .orElseThrow(() -> new ResponseStatusException(CONFLICT, "No active checklist template for " + scope));
  }
}
