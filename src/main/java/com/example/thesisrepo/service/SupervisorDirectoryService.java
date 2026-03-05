package com.example.thesisrepo.service;

import com.example.thesisrepo.config.SupervisorDirectoryProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class SupervisorDirectoryService {

  private final SupervisorDirectoryProperties properties;

  public SupervisorDirectoryService(SupervisorDirectoryProperties properties) {
    this.properties = properties;
  }

  public List<SupervisorDirectoryProperties.SupervisorEntry> listActiveSupervisors(
    String faculty,
    String studyProgram
  ) {
    return properties.getEntries().stream()
      .filter(SupervisorDirectoryProperties.SupervisorEntry::isActive)
      .filter(entry -> matchesFaculty(entry.getFaculty(), faculty))
      .filter(entry -> matchesStudyProgram(entry.getStudyProgram(), studyProgram))
      .toList();
  }

  public SupervisorDirectoryProperties.SupervisorEntry findActiveByEmail(String email) {
    String normalizedEmail = normalize(email);
    return properties.getEntries().stream()
      .filter(SupervisorDirectoryProperties.SupervisorEntry::isActive)
      .filter(entry -> normalize(entry.getEmail()).equals(normalizedEmail))
      .findFirst()
      .orElse(null);
  }

  public boolean isEligibleForStudent(
    SupervisorDirectoryProperties.SupervisorEntry supervisor,
    String studentFaculty,
    String studentStudyProgram
  ) {
    if (supervisor == null) {
      return false;
    }
    return matchesFaculty(supervisor.getFaculty(), studentFaculty)
      && matchesStudyProgram(supervisor.getStudyProgram(), studentStudyProgram);
  }

  public String displayName(SupervisorDirectoryProperties.SupervisorEntry entry) {
    if (entry == null) {
      return "";
    }
    if (StringUtils.hasText(entry.getName())) {
      return entry.getName().trim();
    }
    String email = normalize(entry.getEmail());
    int separator = email.indexOf('@');
    if (separator <= 0) {
      return email;
    }
    String localPart = email.substring(0, separator);
    String[] chunks = localPart.split("[._-]+");
    StringBuilder builder = new StringBuilder();
    for (String chunk : chunks) {
      if (chunk.isBlank()) {
        continue;
      }
      if (!builder.isEmpty()) {
        builder.append(' ');
      }
      builder.append(Character.toUpperCase(chunk.charAt(0)))
        .append(chunk.substring(1));
    }
    return builder.isEmpty() ? localPart : builder.toString();
  }

  private static boolean matchesFaculty(String entryFaculty, String requestedFaculty) {
    if (!StringUtils.hasText(requestedFaculty)) {
      return true;
    }
    String entryNormalized = normalizeFaculty(entryFaculty);
    String requestNormalized = normalizeFaculty(requestedFaculty);
    return entryNormalized.equals(requestNormalized);
  }

  private static boolean matchesStudyProgram(String entryProgram, String requestedProgram) {
    if (!StringUtils.hasText(requestedProgram)) {
      return true;
    }
    String entryNormalized = normalizeStudyProgram(entryProgram);
    String requestNormalized = normalizeStudyProgram(requestedProgram);
    return entryNormalized.equals(requestNormalized);
  }

  private static String normalizeFaculty(String value) {
    String normalized = normalize(value);
    int openIdx = normalized.lastIndexOf('(');
    int closeIdx = normalized.lastIndexOf(')');
    if (openIdx >= 0 && closeIdx > openIdx + 1) {
      return normalized.substring(openIdx + 1, closeIdx).trim();
    }
    return normalized;
  }

  private static String normalizeStudyProgram(String value) {
    String normalized = normalize(value);
    if (normalized.endsWith("systems")) {
      return normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
