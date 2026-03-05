package com.example.thesisrepo.web.dto;

import com.example.thesisrepo.publication.CaseStatus;
import com.example.thesisrepo.publication.PublicationType;

import java.time.Instant;

public record AdminRegistrationApprovalDto(
  Long caseId,
  String title,
  PublicationType type,
  CaseStatus status,
  Instant updatedAt,
  Instant submittedAt,
  Long studentUserId,
  String studentName,
  String studentIdNumber,
  String faculty,
  String program,
  String studentEmail
) {
}
