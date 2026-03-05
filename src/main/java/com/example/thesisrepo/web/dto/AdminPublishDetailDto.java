package com.example.thesisrepo.web.dto;

import com.example.thesisrepo.publication.CaseStatus;
import com.example.thesisrepo.publication.PublicationType;

import java.time.Instant;

public record AdminPublishDetailDto(
  Long caseId,
  String title,
  PublicationType type,
  CaseStatus status,
  Instant updatedAt,
  Metadata metadata,
  SubmissionFile latestSubmission
) {
  public record Metadata(
    String title,
    String authors,
    String keywords,
    String faculty,
    Integer year,
    String abstractText
  ) {
  }

  public record SubmissionFile(
    Long id,
    String originalFilename,
    Instant createdAt,
    Long fileSize,
    String downloadUrl
  ) {
  }
}
