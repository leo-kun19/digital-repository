package com.example.thesisrepo.web.dto;

import java.time.Instant;

public record PublicRepositoryItemDetailDto(
  Long id,
  String title,
  String authors,
  String authorName,
  String faculty,
  String program,
  Integer year,
  String keywords,
  String abstractText,
  Instant publishedAt,
  Long caseId
) {}
