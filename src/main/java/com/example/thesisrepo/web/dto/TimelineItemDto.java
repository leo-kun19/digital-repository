package com.example.thesisrepo.web.dto;

import com.example.thesisrepo.user.Role;

import java.time.Instant;

public record TimelineItemDto(
  Instant at,
  Role actorRole,
  String actorEmail,
  String type,
  String message,
  Long relatedSubmissionVersionId
) {
}
