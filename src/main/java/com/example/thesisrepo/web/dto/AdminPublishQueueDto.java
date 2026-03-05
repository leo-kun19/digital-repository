package com.example.thesisrepo.web.dto;

import com.example.thesisrepo.publication.CaseStatus;
import com.example.thesisrepo.publication.PublicationType;

import java.time.Instant;

public record AdminPublishQueueDto(
  Long caseId,
  String title,
  PublicationType type,
  CaseStatus status,
  Instant updatedAt
) {
}
