package com.example.thesisrepo.service.checklist;

import com.example.thesisrepo.publication.ChecklistItemV2;
import com.example.thesisrepo.publication.ChecklistScope;
import com.example.thesisrepo.publication.ChecklistTemplate;
import com.example.thesisrepo.publication.repo.ChecklistItemV2Repository;
import com.example.thesisrepo.publication.repo.ChecklistTemplateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class ChecklistImportService {

  private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final ChecklistTemplateRepository checklistTemplates;
  private final ChecklistItemV2Repository checklistItems;

  @Transactional
  public ImportSummary importChecklist(ChecklistScope type, MultipartFile file, boolean activate, String sheetName) {
    ParsedChecklist parsed = parseWorkbook(file, sheetName);

    if (parsed.items().isEmpty()) {
      throw new ResponseStatusException(
        BAD_REQUEST,
        "No checklist items were imported. Confirm section/item rows and marker column in the workbook."
      );
    }

    int nextVersion = checklistTemplates.findTopByPublicationTypeOrderByVersionDesc(type)
      .map(template -> template.getVersion() + 1)
      .orElse(1);

    ChecklistTemplate importedTemplate = checklistTemplates.save(ChecklistTemplate.builder()
      .publicationType(type)
      .version(nextVersion)
      .isActive(false)
      .build());

    // New template -> should be empty, but keep safe and consistent:
    checklistItems.deleteByTemplate(importedTemplate);

    for (ParsedItem item : parsed.items()) {
      checklistItems.save(ChecklistItemV2.builder()
        .template(importedTemplate)
        .orderIndex(item.orderIndex())
        .section(item.section())
        .itemText(item.itemText())
        .guidanceText(item.guidanceText())
        .isRequired(item.required())
        .build());
    }

    if (activate) {
      List<ChecklistTemplate> templates = checklistTemplates.findByPublicationTypeOrderByVersionDesc(type);
      for (ChecklistTemplate template : templates) {
        template.setActive(Objects.equals(template.getId(), importedTemplate.getId()));
      }
      checklistTemplates.saveAll(templates);
    }

    return new ImportSummary(
      type,
      importedTemplate.getId(),
      importedTemplate.getVersion(),
      parsed.items().size(),
      parsed.sections()
    );
  }

  ParsedChecklist parseWorkbook(MultipartFile file, String requestedSheetName) {
    validateXlsx(file);

    try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
      Sheet sheet = resolveSheet(workbook, requestedSheetName);

      DataFormatter formatter = new DataFormatter();
      FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

      String currentSection = "General";
      Set<String> sections = new LinkedHashSet<>();
      List<ParsedItem> items = new ArrayList<>();
      int orderIndex = 1;

      for (Row row : sheet) {
        if (isRowBlank(row, formatter, evaluator)) {
          continue;
        }

        // Mapping:
        // - Column A (0) / Column B (1): section/item label text
        // - Column C (2): marker "Template" for headers OR guidance text for items
        // - Column D (3): fallback guidance text if column C is the marker
        String columnA = cellText(row, 0, formatter, evaluator);
        String columnB = cellText(row, 1, formatter, evaluator);
        String columnC = cellText(row, 2, formatter, evaluator);
        String columnD = cellText(row, 3, formatter, evaluator);

        if (isHeaderRow(row, columnA, columnB, columnC, formatter, evaluator)) {
          currentSection = sectionName(columnB, columnA);
          sections.add(currentSection);
          continue;
        }

        String itemText = firstNonBlank(columnB, columnA);
        if (!hasText(itemText)) {
          continue;
        }

        String guidance = isTemplateMarker(columnC) ? columnD : columnC;
        boolean required = !itemText.toLowerCase(Locale.ROOT).contains("optional");

        items.add(new ParsedItem(
          currentSection,
          itemText.trim(),
          hasText(guidance) ? guidance.trim() : null,
          required,
          orderIndex++
        ));
        sections.add(currentSection);
      }

      return new ParsedChecklist(items, new ArrayList<>(sections));
    } catch (Exception ex) {
      // POI can throw a few different exceptions depending on file contents
      throw new ResponseStatusException(BAD_REQUEST, "Unable to read workbook: " + ex.getMessage(), ex);
    }
  }

  private Sheet resolveSheet(Workbook workbook, String requestedSheetName) {
    if (hasText(requestedSheetName)) {
      Sheet sheet = workbook.getSheet(requestedSheetName.trim());
      if (sheet == null) {
        throw new ResponseStatusException(BAD_REQUEST, "Sheet not found: " + requestedSheetName);
      }
      return sheet;
    }

    if (workbook.getNumberOfSheets() == 0) {
      throw new ResponseStatusException(BAD_REQUEST, "Workbook has no sheets");
    }
    return workbook.getSheetAt(0);
  }

  private void validateXlsx(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(BAD_REQUEST, "XLSX file is required");
    }

    String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    if (!fileName.endsWith(".xlsx")) {
      throw new ResponseStatusException(BAD_REQUEST, "Only .xlsx files are supported");
    }

    String contentType = file.getContentType();
    if (contentType != null
      && !contentType.isBlank()
      && !"application/octet-stream".equalsIgnoreCase(contentType)
      && !MIME_XLSX.equalsIgnoreCase(contentType)) {
      throw new ResponseStatusException(BAD_REQUEST, "Unsupported file content type: " + contentType);
    }
  }

  private boolean isHeaderRow(Row row, String columnA, String columnB, String columnC,
                              DataFormatter formatter, FormulaEvaluator evaluator) {
    // Common pattern: "Template" marker appears with header label
    if (isTemplateMarker(columnC) && hasText(columnB)) return true;
    if (isTemplateMarker(columnB) && hasText(columnA)) return true;

    // Heuristic: only one non-blank cell and styled bold
    return hasText(columnB) && !hasText(columnC) && isLikelyStyledHeader(row, 1, formatter, evaluator);
  }

  private boolean isLikelyStyledHeader(Row row, int titleCellIdx,
                                       DataFormatter formatter, FormulaEvaluator evaluator) {
    Cell titleCell = row.getCell(titleCellIdx);
    if (titleCell == null) return false;

    CellStyle style = titleCell.getCellStyle();
    if (style == null) return false;

    int nonBlank = 0;
    for (int idx = 0; idx <= 3; idx++) {
      String txt = cellText(row, idx, formatter, evaluator);
      if (hasText(txt)) nonBlank++;
    }
    if (nonBlank != 1) return false;

    // POI API varies slightly by version; this avoids the common red-underlines
    int fontIndex;
    try {
      fontIndex = style.getFontIndexAsInt(); // POI 5+
    } catch (Throwable t) {
      // Fallback for older POI
      fontIndex = style.getFontIndex();
    }

    Font font = row.getSheet().getWorkbook().getFontAt(fontIndex);
    return font != null && font.getBold();
  }

  private boolean isTemplateMarker(String value) {
    if (!hasText(value)) return false;
    String normalized = value.trim().toLowerCase(Locale.ROOT).replace(":", "");
    return "template".equals(normalized) || normalized.startsWith("template ");
  }

  private static boolean isRowBlank(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
    if (row == null) return true;

    short first = row.getFirstCellNum();
    short last = row.getLastCellNum();
    if (first < 0 || last < 0) return true;

    for (int idx = first; idx < last; idx++) {
      if (idx < 0) continue;
      Cell cell = row.getCell(idx);
      if (cell == null) continue;

      String txt = formatter.formatCellValue(cell, evaluator);
      if (hasText(txt)) return false;
    }
    return true;
  }

  private static String cellText(Row row, int columnIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
    Cell cell = row.getCell(columnIndex);
    if (cell == null) return "";
    return formatter.formatCellValue(cell, evaluator).trim();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String firstNonBlank(String first, String second) {
    if (hasText(first)) return first;
    return hasText(second) ? second : "";
  }

  private static String sectionName(String primary, String secondary) {
    String section = hasText(primary) ? primary.trim() : (hasText(secondary) ? secondary.trim() : "General");
    String cleaned = section.replaceAll("(?i)\\btemplate\\b", "").replace("()", "").trim();
    return cleaned.isEmpty() ? "General" : cleaned;
  }

  public record ImportSummary(
    ChecklistScope type,
    Long newTemplateId,
    Integer newVersion,
    Integer itemsImported,
    List<String> sections
  ) {}

  record ParsedChecklist(List<ParsedItem> items, List<String> sections) {}
  record ParsedItem(String section, String itemText, String guidanceText, boolean required, int orderIndex) {}
}