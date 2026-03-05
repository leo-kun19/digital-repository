package com.example.thesisrepo.web.dto;

import com.example.thesisrepo.publication.CaseStatus;
import com.example.thesisrepo.publication.PublicationType;

import java.time.Instant;

public record LecturerCaseWorkItemDto(
  Long caseId,
  PublicationType type,
  CaseStatus status,
  Instant updatedAt,
  String registrationTitle,
  Integer registrationYear,
  Instant latestSubmissionAt,
  Instant lastLecturerFeedbackAt,
  Instant lecturerForwardedAt,
  Instant lastLibraryFeedbackAt,
  Instant libraryApprovedAt
) {
}
