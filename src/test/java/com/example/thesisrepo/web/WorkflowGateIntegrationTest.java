package com.example.thesisrepo.web;

import com.example.thesisrepo.profile.LecturerProfile;
import com.example.thesisrepo.profile.LecturerProfileRepository;
import com.example.thesisrepo.publication.*;
import com.example.thesisrepo.publication.repo.*;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import com.example.thesisrepo.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowGateIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PublicationCaseRepository cases;
  @Autowired private SubmissionVersionRepository versions;
  @Autowired private PublishedItemRepository publishedItems;
  @Autowired private ChecklistTemplateRepository checklistTemplates;
  @Autowired private ChecklistItemV2Repository checklistItems;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private LecturerProfileRepository lecturerProfiles;
  @Autowired private com.example.thesisrepo.service.workflow.PublicationWorkflowGateService workflowGates;

  private User student;
  private User lecturer;

  @BeforeEach
  void setup() {
    student = users.findByEmail("student1@example.com").orElseThrow();
    lecturer = users.findByEmail("lecturer1@example.com").orElseThrow();
    ensureActiveTemplate(ChecklistScope.THESIS);
  }

  @Test
  void submittingRegistrationCreatesAuditEvent() throws Exception {
    MockHttpSession studentSession = login("student1@example.com", "Student123!");

    String response = mockMvc.perform(post("/api/student/registrations")
        .contentType(MediaType.APPLICATION_JSON)
        .session(studentSession)
        .content("""
          {"type":"THESIS","title":"Audit Registration","supervisorUserIds":[%d]}
          """.formatted(lecturer.getId())))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    Long caseId = Long.valueOf(response.replaceAll(".*\"caseId\":(\\d+).*", "$1"));

    mockMvc.perform(post("/api/student/registrations/{caseId}/submit", caseId)
        .contentType(MediaType.APPLICATION_JSON)
        .session(studentSession)
        .content("{\"permissionAccepted\":true}"))
      .andExpect(status().isOk());

    assertThat(auditEvents.findByCaseIdOrderByCreatedAtDesc(caseId))
      .anyMatch(event ->
        event.getEventType() == AuditEventType.REGISTRATION_SUBMITTED
          && event.getActorRole() == Role.STUDENT
          && event.getActorEmail() != null);
  }

  @Test
  void studentCannotUploadWhenRegistrationNotApproved() throws Exception {
    MockHttpSession session = login("student1@example.com", "Student123!");

    String response = mockMvc.perform(post("/api/student/registrations")
        .contentType(MediaType.APPLICATION_JSON)
        .session(session)
        .content("""
          {"type":"THESIS","title":"Gate Test","supervisorUserIds":[%d]}
          """.formatted(lecturer.getId())))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    Long caseId = Long.valueOf(response.replaceAll(".*\"caseId\":(\\d+).*", "$1"));

    mockMvc.perform(multipart("/api/student/cases/{caseId}/submissions", caseId)
        .file(new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4\ncontent".getBytes()))
        .session(session))
      .andExpect(status().isBadRequest())
      .andExpect(result -> assertThat(result.getResponse().getErrorMessage())
        .contains("Registration must be verified by the library before submission."));
  }

  @Test
  void adminReviewQueueExcludesSupervisorStageUntilLecturerForwards() throws Exception {
    MockHttpSession studentSession = login("student1@example.com", "Student123!");
    MockHttpSession lecturerSession = login("lecturer1@example.com", "Lecturer123!");
    MockHttpSession adminSession = login("admin@example.com", "Admin123!");

    String response = mockMvc.perform(post("/api/student/registrations")
        .contentType(MediaType.APPLICATION_JSON)
        .session(studentSession)
        .content("""
          {"type":"THESIS","title":"Forward Gate","supervisorUserIds":[%d]}
          """.formatted(lecturer.getId())))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    Long caseId = Long.valueOf(response.replaceAll(".*\"caseId\":(\\d+).*", "$1"));

    mockMvc.perform(post("/api/student/registrations/{caseId}/submit", caseId)
        .contentType(MediaType.APPLICATION_JSON)
        .session(studentSession)
        .content("{\"permissionAccepted\":true}"))
      .andExpect(status().isOk());

    mockMvc.perform(post("/api/lecturer/approvals/{caseId}/approve", caseId)
        .session(lecturerSession))
      .andExpect(status().isOk());

    mockMvc.perform(post("/api/admin/registration-approvals/{caseId}/approve", caseId)
        .session(adminSession))
      .andExpect(status().isOk());

    mockMvc.perform(multipart("/api/student/cases/{caseId}/submissions", caseId)
        .file(new MockMultipartFile("file", "submission.pdf", "application/pdf", "%PDF-1.4\ncontent".getBytes()))
        .session(studentSession))
      .andExpect(status().isOk());

    mockMvc.perform(get("/api/admin/review").session(adminSession))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[*].id", not(hasItem(caseId.intValue()))));

    mockMvc.perform(post("/api/lecturer/cases/{caseId}/approve-and-forward", caseId)
        .session(lecturerSession))
      .andExpect(status().isOk());

    mockMvc.perform(get("/api/admin/review").session(adminSession))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[*].id", hasItem(caseId.intValue())));
  }

  @Test
  void registrationRejectsSupervisorOutsideStudentStudyProgram() throws Exception {
    LecturerProfile lecturerProfile = lecturerProfiles.findByUserId(lecturer.getId()).orElseThrow();
    String originalDepartment = lecturerProfile.getDepartment();
    String originalFaculty = lecturerProfile.getFaculty();

    try {
      lecturerProfile.setDepartment("Accounting");
      lecturerProfile.setFaculty("Faculty of Engineering and Technology (FET)");
      lecturerProfiles.save(lecturerProfile);

      MockHttpSession studentSession = login("student1@example.com", "Student123!");

      mockMvc.perform(post("/api/student/registrations")
          .contentType(MediaType.APPLICATION_JSON)
          .session(studentSession)
          .content("""
            {"type":"THESIS","title":"Program Match Test","supervisorUserIds":[%d]}
            """.formatted(lecturer.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(result -> assertThat(result.getResponse().getErrorMessage())
          .contains("Supervisor must be from the same study program."));
    } finally {
      lecturerProfile.setDepartment(originalDepartment);
      lecturerProfile.setFaculty(originalFaculty);
      lecturerProfiles.save(lecturerProfile);
    }
  }

  @Test
  void registrationRejectsMultipleSupervisorEmails() throws Exception {
    MockHttpSession studentSession = login("student1@example.com", "Student123!");

    mockMvc.perform(post("/api/student/registrations")
        .contentType(MediaType.APPLICATION_JSON)
        .session(studentSession)
        .content("""
          {
            "type":"THESIS",
            "title":"Multiple Supervisor Test",
            "supervisorEmails":["lecturer1@example.com","second.lecturer@example.com"]
          }
          """))
      .andExpect(status().isBadRequest())
      .andExpect(result -> assertThat(result.getResponse().getErrorMessage())
        .contains("Only one supervisor is allowed."));
  }

  @Test
  void registrationAcceptsSingleSupervisorEmail() throws Exception {
    MockHttpSession studentSession = login("student1@example.com", "Student123!");

    mockMvc.perform(post("/api/student/registrations")
        .contentType(MediaType.APPLICATION_JSON)
        .session(studentSession)
        .content("""
          {
            "type":"THESIS",
            "title":"Single Supervisor Email",
            "supervisorEmail":"lecturer1@example.com"
          }
          """))
      .andExpect(status().isOk());
  }

  @Test
  void publicCannotDownloadRepositoryFile() throws Exception {
    PublicationCase c = cases.save(PublicationCase.builder()
      .student(student)
      .type(PublicationType.THESIS)
      .status(CaseStatus.PUBLISHED)
      .build());

    SubmissionVersion submission = versions.save(SubmissionVersion.builder()
      .publicationCase(c)
      .versionNumber(1)
      .filePath("file:///tmp/non-existing.pdf")
      .originalFilename("paper.pdf")
      .contentType("application/pdf")
      .fileSize(10L)
      .status(SubmissionStatus.APPROVED)
      .build());

    PublishedItem item = publishedItems.save(PublishedItem.builder()
      .publicationCase(c)
      .submissionVersion(submission)
      .publishedAt(Instant.now())
      .title("Published")
      .authors("A")
      .build());

    mockMvc.perform(get("/api/public/repository/{id}/download", item.getId()))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void checklistEditingCreatesNewVersionAndKeepsOldUnchanged() throws Exception {
    MockHttpSession adminSession = login("admin@example.com", "Admin123!");

    ChecklistTemplate active = checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(ChecklistScope.THESIS).orElseThrow();
    int oldVersion = active.getVersion();
    int oldCount = checklistItems.findByTemplateOrderByOrderIndexAsc(active).size();

    String newVersionResponse = mockMvc.perform(post("/api/admin/checklists/THESIS/new-version")
        .session(adminSession))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    Long newTemplateId = Long.valueOf(newVersionResponse.replaceAll(".*\"templateId\":(\\d+).*", "$1"));

    mockMvc.perform(put("/api/admin/checklists/templates/{templateId}/items", newTemplateId)
        .session(adminSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"items":[{"section":"Doc","itemText":"New item 1","guidanceText":"Guide","required":true}]}
          """))
      .andExpect(status().isOk());

    ChecklistTemplate oldTemplate = checklistTemplates.findByPublicationTypeOrderByVersionDesc(ChecklistScope.THESIS).stream()
      .filter(t -> t.getVersion() == oldVersion)
      .findFirst().orElseThrow();

    ChecklistTemplate edited = checklistTemplates.findById(newTemplateId).orElseThrow();
    assertThat(edited.getVersion()).isGreaterThan(oldVersion);
    assertThat(checklistItems.findByTemplateOrderByOrderIndexAsc(oldTemplate)).hasSize(oldCount);
    assertThat(checklistItems.findByTemplateOrderByOrderIndexAsc(edited)).hasSize(1);
  }

  @Test
  void studentReuploadAfterLibraryRevisionStaysInLibraryReview() throws Exception {
    PublicationCase c = cases.save(PublicationCase.builder()
      .student(student)
      .type(PublicationType.THESIS)
      .status(CaseStatus.NEEDS_REVISION_LIBRARY)
      .build());

    MockHttpSession session = login("student1@example.com", "Student123!");

    mockMvc.perform(multipart("/api/student/cases/{caseId}/submissions", c.getId())
        .file(new MockMultipartFile("file", "revised.pdf", "application/pdf", "%PDF-1.4\ncontent".getBytes()))
        .session(session))
      .andExpect(status().isOk());

    PublicationCase updated = cases.findById(c.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(CaseStatus.UNDER_LIBRARY_REVIEW);
    assertThat(auditEvents.findByCaseIdOrderByCreatedAtDesc(c.getId()))
      .anyMatch(event ->
        event.getEventType() == AuditEventType.SUBMISSION_UPLOADED
          && event.getSubmissionVersionId() != null
          && event.getActorRole() == Role.STUDENT);
  }

  @Test
  void nextStatusAfterStudentUploadMapsToExpectedStage() {
    assertThat(workflowGates.nextStatusAfterStudentUpload(CaseStatus.NEEDS_REVISION_LIBRARY))
      .isEqualTo(CaseStatus.UNDER_LIBRARY_REVIEW);
    assertThat(workflowGates.nextStatusAfterStudentUpload(CaseStatus.NEEDS_REVISION_SUPERVISOR))
      .isEqualTo(CaseStatus.UNDER_SUPERVISOR_REVIEW);
    assertThat(workflowGates.nextStatusAfterStudentUpload(CaseStatus.REGISTRATION_VERIFIED))
      .isEqualTo(CaseStatus.UNDER_SUPERVISOR_REVIEW);
  }

  private void ensureActiveTemplate(ChecklistScope scope) {
    if (checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(scope).isPresent()) {
      return;
    }
    ChecklistTemplate template = checklistTemplates.save(ChecklistTemplate.builder()
      .publicationType(scope)
      .version(1)
      .isActive(true)
      .build());
    checklistItems.save(ChecklistItemV2.builder()
      .template(template)
      .orderIndex(1)
      .section("General")
      .itemText("Seed item")
      .guidanceText("Seed guidance")
      .isRequired(true)
      .build());
  }

  private MockHttpSession login(String username, String password) throws Exception {
    return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("username", username)
        .param("password", password))
      .andExpect(status().isOk())
      .andReturn()
      .getRequest()
      .getSession(false);
  }
}
