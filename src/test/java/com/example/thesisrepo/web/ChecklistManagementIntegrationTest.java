package com.example.thesisrepo.web;

import com.example.thesisrepo.publication.ChecklistScope;
import com.example.thesisrepo.publication.ChecklistTemplate;
import com.example.thesisrepo.publication.repo.ChecklistItemV2Repository;
import com.example.thesisrepo.publication.repo.ChecklistTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChecklistManagementIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ChecklistTemplateRepository checklistTemplates;
  @Autowired private ChecklistItemV2Repository checklistItems;

  @Test
  void createEmptyCreatesVersionOneDraftWhenTypeHasNoTemplates() throws Exception {
    MockHttpSession admin = loginAsAdmin();
    String body = mockMvc.perform(post("/api/admin/checklists/OTHER/create-empty").session(admin))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    JsonNode json = objectMapper.readTree(body);
    assertThat(json.get("version").asInt()).isEqualTo(1);
    assertThat(json.get("active").asBoolean()).isFalse();
    assertThat(json.get("itemCount").asInt()).isEqualTo(0);
  }

  @Test
  void newDraftCreatesNextVersionWhenTemplatesExist() throws Exception {
    ChecklistTemplate active = ensureActiveTemplate(ChecklistScope.THESIS);
    int topVersion = checklistTemplates.findTopByPublicationTypeOrderByVersionDesc(ChecklistScope.THESIS)
      .map(ChecklistTemplate::getVersion).orElse(active.getVersion());

    MockHttpSession admin = loginAsAdmin();
    String body = mockMvc.perform(post("/api/admin/checklists/THESIS/new-draft").session(admin))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    JsonNode json = objectMapper.readTree(body);
    assertThat(json.get("version").asInt()).isEqualTo(topVersion + 1);
    assertThat(json.get("active").asBoolean()).isFalse();
  }

  @Test
  void cannotEditItemsOnActiveTemplate() throws Exception {
    ChecklistTemplate active = ensureActiveTemplate(ChecklistScope.THESIS);
    MockHttpSession admin = loginAsAdmin();

    String body = mockMvc.perform(put("/api/admin/checklists/templates/{templateId}/items", active.getId())
        .session(admin)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          [{"orderIndex":1,"section":"S","itemText":"X","guidanceText":"G","isRequired":true}]
          """))
      .andExpect(status().isBadRequest())
      .andReturn().getResponse().getErrorMessage();

    assertThat(body).isEqualTo("Cannot edit active template; create a new draft first.");
  }

  @Test
  void activateSwitchesActiveTemplateAndKeepsOlderVersions() throws Exception {
    ChecklistTemplate oldActive = ensureActiveTemplate(ChecklistScope.ARTICLE);
    MockHttpSession admin = loginAsAdmin();

    String cloneBody = mockMvc.perform(post("/api/admin/checklists/ARTICLE/new-draft").session(admin))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    Long draftId = objectMapper.readTree(cloneBody).get("id").asLong();

    mockMvc.perform(put("/api/admin/checklists/templates/{templateId}/items", draftId)
        .session(admin)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          [{"orderIndex":1,"section":"New","itemText":"Article check","guidanceText":"Guidance","isRequired":true}]
          """))
      .andExpect(status().isOk());

    mockMvc.perform(post("/api/admin/checklists/templates/{templateId}/activate", draftId).session(admin))
      .andExpect(status().isOk());

    ChecklistTemplate activeAfter = checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(ChecklistScope.ARTICLE).orElseThrow();
    assertThat(activeAfter.getId()).isEqualTo(draftId);
    ChecklistTemplate oldActiveAfter = checklistTemplates.findById(oldActive.getId()).orElseThrow();
    assertThat(oldActiveAfter.isActive()).isFalse();
    assertThat(checklistItems.findByTemplateOrderByOrderIndexAsc(activeAfter)).hasSize(1);
    assertThat(checklistTemplates.findByPublicationTypeOrderByVersionDesc(ChecklistScope.ARTICLE)).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void deleteDraftTemplateRemovesVersion() throws Exception {
    MockHttpSession admin = loginAsAdmin();

    ensureActiveTemplate(ChecklistScope.THESIS);
    String cloneBody = mockMvc.perform(post("/api/admin/checklists/THESIS/new-draft").session(admin))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    Long draftId = objectMapper.readTree(cloneBody).get("id").asLong();

    mockMvc.perform(delete("/api/admin/checklists/templates/{templateId}", draftId).session(admin))
      .andExpect(status().isOk());

    assertThat(checklistTemplates.findById(draftId)).isEmpty();
  }

  private ChecklistTemplate ensureActiveTemplate(ChecklistScope scope) {
    return checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(scope)
      .orElseGet(() -> {
        ChecklistTemplate template = checklistTemplates.save(ChecklistTemplate.builder()
          .publicationType(scope)
          .version(1)
          .isActive(true)
          .build());
        checklistItems.save(com.example.thesisrepo.publication.ChecklistItemV2.builder()
          .template(template)
          .orderIndex(1)
          .section("General")
          .itemText("Seed item")
          .guidanceText("Seed guidance")
          .isRequired(true)
          .build());
        return template;
      });
  }

  private MockHttpSession loginAsAdmin() throws Exception {
    return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("username", "admin@example.com")
        .param("password", "Admin123!"))
      .andExpect(status().isOk())
      .andReturn()
      .getRequest()
      .getSession(false);
  }
}
