package com.example.thesisrepo.service;

import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.StaffRegistry;
import com.example.thesisrepo.user.StaffRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class SupervisorDirectoryService {

  private final StaffRegistryRepository staffRegistryRepository;

  public SupervisorDirectoryService(StaffRegistryRepository staffRegistryRepository) {
    this.staffRegistryRepository = staffRegistryRepository;
  }

  /**
   * List all LECTURER entries from staff_registry, optionally filtered by faculty and/or studyProgram.
   */
  public List<StaffRegistry> listActiveSupervisors(String faculty, String studyProgram) {
    return staffRegistryRepository.findAll().stream()
      .filter(s -> s.getRole() == Role.LECTURER)
      .filter(s -> matchesStudyProgram(s.getStudyProgram(), studyProgram))
      .toList();
  }

  /**
   * Find a lecturer by email.
   */
  public StaffRegistry findActiveByEmail(String email) {
    String normalizedEmail = normalize(email);
    return staffRegistryRepository.findByEmailIgnoreCase(normalizedEmail)
      .filter(s -> s.getRole() == Role.LECTURER)
      .orElse(null);
  }

  /**
   * Check if a supervisor matches the student's study program.
   */
  public boolean isEligibleForStudent(StaffRegistry supervisor, String studentFaculty, String studentStudyProgram) {
    if (supervisor == null) {
      return false;
    }
    return matchesStudyProgram(supervisor.getStudyProgram(), studentStudyProgram);
  }

  /**
   * Build display name: use fullName from DB, or derive from email.
   */
  public String displayName(StaffRegistry entry) {
    if (entry == null) {
      return "";
    }
    if (StringUtils.hasText(entry.getFullName())) {
      return entry.getFullName().trim();
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

  private static boolean matchesStudyProgram(String entryProgram, String requestedProgram) {
    if (!StringUtils.hasText(requestedProgram)) {
      return true;
    }
    String entryNormalized = normalizeStudyProgram(entryProgram);
    String requestNormalized = normalizeStudyProgram(requestedProgram);
    return entryNormalized.equals(requestNormalized);
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
