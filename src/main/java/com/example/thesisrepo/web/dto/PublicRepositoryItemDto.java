package com.example.thesisrepo.web.dto;

import java.time.Instant;

public record PublicRepositoryItemDto(
  Long id,
  String title,
  String authors,
  String authorName,
  String faculty,
  String program,
  Integer year,
  String keywords,
  Instant publishedAt
) {}
