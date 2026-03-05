package com.example.thesisrepo.web.dto;

import com.example.thesisrepo.publication.CaseStatus;
import com.example.thesisrepo.publication.PublicationType;

import java.time.Instant;

public record LecturerApprovalQueueRowDto(
  Long caseId,
  PublicationType type,
  CaseStatus status,
  Instant updatedAt,
  Long studentUserId,
  String studentEmail,
  String studentName,
  String studentIdNumber,
  String faculty,
  String program,
  String registrationTitle,
  Integer registrationYear,
  Instant registrationSubmittedAt
) {
}
