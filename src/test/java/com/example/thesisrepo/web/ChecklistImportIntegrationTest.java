package com.example.thesisrepo.web;

import com.example.thesisrepo.publication.ChecklistItemV2;
import com.example.thesisrepo.publication.ChecklistScope;
import com.example.thesisrepo.publication.ChecklistTemplate;
import com.example.thesisrepo.publication.repo.ChecklistItemV2Repository;
import com.example.thesisrepo.publication.repo.ChecklistTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChecklistImportIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ChecklistTemplateRepository checklistTemplates;
  @Autowired private ChecklistItemV2Repository checklistItems;

  @Test
  void importCreatesNewVersionAndLeavesOldActiveUnchanged() throws Exception {
    ChecklistTemplate oldActive = ensureActiveTemplate(ChecklistScope.THESIS);
    int topVersionBeforeImport = checklistTemplates.findTopByPublicationTypeOrderByVersionDesc(ChecklistScope.THESIS)
      .map(ChecklistTemplate::getVersion)
      .orElse(oldActive.getVersion());
    int oldItemCount = checklistItems.findByTemplateOrderByOrderIndexAsc(oldActive).size();

    MockMultipartFile file = new MockMultipartFile(
      "file",
      "checklist.xlsx",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      sampleWorkbookBytes()
    );

    MockHttpSession adminSession = loginAsAdmin();
    String body = mockMvc.perform(multipart("/api/admin/checklists/THESIS/import-xlsx")
        .file(file)
        .param("activate", "false")
        .session(adminSession))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    JsonNode json = objectMapper.readTree(body);
    Long newTemplateId = json.get("newTemplateId").asLong();
    assertThat(json.get("itemsImported").asInt()).isEqualTo(2);
    assertThat(json.get("newVersion").asInt()).isEqualTo(topVersionBeforeImport + 1);
    List<String> sections = new java.util.ArrayList<>();
    json.get("sections").forEach(node -> sections.add(node.asText()));
    assertThat(sections).contains("Metadata Checks");

    ChecklistTemplate imported = checklistTemplates.findById(newTemplateId).orElseThrow();
    List<ChecklistItemV2> importedItems = checklistItems.findByTemplateOrderByOrderIndexAsc(imported);
    assertThat(importedItems).hasSize(2);
    assertThat(importedItems.get(0).getOrderIndex()).isEqualTo(1);
    assertThat(importedItems.get(1).getOrderIndex()).isEqualTo(2);
    assertThat(importedItems.get(0).isRequired()).isTrue();
    assertThat(importedItems.get(1).isRequired()).isFalse();

    ChecklistTemplate stillActive = checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(ChecklistScope.THESIS).orElseThrow();
    assertThat(stillActive.getId()).isEqualTo(oldActive.getId());
    assertThat(checklistItems.findByTemplateOrderByOrderIndexAsc(oldActive)).hasSize(oldItemCount);
  }

  @Test
  void importWithActivateSwitchesActiveTemplate() throws Exception {
    ChecklistTemplate oldActive = ensureActiveTemplate(ChecklistScope.ARTICLE);

    MockMultipartFile file = new MockMultipartFile(
      "file",
      "checklist.xlsx",
      MediaType.APPLICATION_OCTET_STREAM_VALUE,
      sampleWorkbookBytes()
    );

    MockHttpSession adminSession = loginAsAdmin();
    String body = mockMvc.perform(multipart("/api/admin/checklists/ARTICLE/import-xlsx")
        .file(file)
        .param("activate", "true")
        .session(adminSession))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long newTemplateId = objectMapper.readTree(body).get("newTemplateId").asLong();
    ChecklistTemplate activeAfter = checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(ChecklistScope.ARTICLE).orElseThrow();
    assertThat(activeAfter.getId()).isEqualTo(newTemplateId);
    assertThat(activeAfter.getId()).isNotEqualTo(oldActive.getId());
    assertThat(checklistItems.findByTemplateOrderByOrderIndexAsc(activeAfter)).hasSize(2);
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

  private ChecklistTemplate ensureActiveTemplate(ChecklistScope scope) {
    return checklistTemplates.findFirstByPublicationTypeAndIsActiveTrue(scope)
      .orElseGet(() -> {
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
        return template;
      });
  }

  private byte[] sampleWorkbookBytes() throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Checklist");

      Row sectionHeader = sheet.createRow(0);
      sectionHeader.createCell(1).setCellValue("Metadata Checks");
      sectionHeader.createCell(2).setCellValue("Template");

      Row item1 = sheet.createRow(1);
      item1.createCell(1).setCellValue("Title matches registration");
      item1.createCell(2).setCellValue("Cross-check against submitted registration.");

      Row item2 = sheet.createRow(2);
      item2.createCell(1).setCellValue("Keywords provided (optional)");
      item2.createCell(2).setCellValue("Optional keywords can be skipped.");

      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        workbook.write(out);
        return out.toByteArray();
      }
    }
  }
}
