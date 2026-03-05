package com.example.thesisrepo.web;

import com.example.thesisrepo.publication.DownloadEvent;
import com.example.thesisrepo.publication.PublishedItem;
import com.example.thesisrepo.publication.repo.DownloadEventRepository;
import com.example.thesisrepo.publication.repo.PublishedItemRepository;
import com.example.thesisrepo.service.CurrentUserService;
import com.example.thesisrepo.service.StorageService;
import com.example.thesisrepo.web.dto.PublicRepositoryItemDetailDto;
import com.example.thesisrepo.web.dto.PublicRepositoryItemDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

  private final PublishedItemRepository publishedItems;
  private final DownloadEventRepository downloadEvents;
  private final StorageService storageService;
  private final CurrentUserService currentUserService;

  @GetMapping("/repository/search")
  public ResponseEntity<SearchResponse> searchRepository(
    @RequestParam(required = false) String title,
    @RequestParam(required = false) String author,
    @RequestParam(required = false) String faculty,
    @RequestParam(required = false) String program,
    @RequestParam(required = false) Integer year,
    @RequestParam(required = false) String keyword
  ) {
    Specification<PublishedItem> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (hasText(title)) {
        predicates.add(cb.like(cb.lower(root.get("title")), like(title)));
      }
      if (hasText(author)) {
        predicates.add(cb.or(
          cb.like(cb.lower(root.get("authors")), like(author)),
          cb.like(cb.lower(root.get("authorName")), like(author))
        ));
      }
      if (hasText(faculty)) {
        predicates.add(cb.equal(cb.lower(root.get("faculty")), normalize(faculty)));
      }
      if (hasText(program)) {
        predicates.add(cb.equal(cb.lower(root.get("program")), normalize(program)));
      }
      if (year != null) {
        predicates.add(cb.equal(root.get("year"), year));
      }
      if (hasText(keyword)) {
        predicates.add(cb.like(cb.lower(root.get("keywords")), like(keyword)));
      }

      query.orderBy(cb.desc(root.get("publishedAt")));
      return cb.and(predicates.toArray(Predicate[]::new));
    };

    List<PublicRepositoryItemDto> results = publishedItems.findAll(spec).stream()
      .map(this::toPublicSummary)
      .toList();

    return ResponseEntity.ok(new SearchResponse(results.size(), results));
  }

  @GetMapping("/repository/{id}")
  public ResponseEntity<PublicRepositoryItemDetailDto> repositoryDetail(@PathVariable Long id) {
    PublishedItem item = publishedItems.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published item not found"));
    return ResponseEntity.ok(toPublicDetail(item));
  }

  @GetMapping("/repository/{id}/download")
  @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN')")
  public ResponseEntity<Resource> download(@PathVariable Long id, HttpServletRequest request) {
    PublishedItem item = publishedItems.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published item not found"));

    if (item.getSubmissionVersion() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No file attached");
    }

    String ipAddress = resolveClientIp(request);
    String userAgent = request.getHeader("User-Agent");
    downloadEvents.save(DownloadEvent.builder()
      .publishedItem(item)
      .user(currentUserService.requireCurrentUser())
      .downloadedAt(Instant.now())
      .ipAddress(ipAddress)
      .userAgent(userAgent)
      .build());

    String storedKey = item.getSubmissionVersion().getFilePath();
    if (!storageService.exists(storedKey)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
    }

    try {
      Resource resource = storageService.openAsResource(storedKey);
      String fileName = Optional.ofNullable(item.getSubmissionVersion().getOriginalFilename())
        .filter(name -> !name.isBlank())
        .orElse(defaultFilename(storedKey, "document.pdf"));

      return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .body(resource);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot read file", e);
    }
  }

  private PublicRepositoryItemDto toPublicSummary(PublishedItem item) {
    return new PublicRepositoryItemDto(
      item.getId(),
      item.getTitle(),
      item.getAuthors(),
      item.getAuthorName(),
      item.getFaculty(),
      item.getProgram(),
      item.getYear(),
      item.getKeywords(),
      item.getPublishedAt()
    );
  }

  private PublicRepositoryItemDetailDto toPublicDetail(PublishedItem item) {
    return new PublicRepositoryItemDetailDto(
      item.getId(),
      item.getTitle(),
      item.getAuthors(),
      item.getAuthorName(),
      item.getFaculty(),
      item.getProgram(),
      item.getYear(),
      item.getKeywords(),
      item.getAbstractText(),
      item.getPublishedAt(),
      item.getPublicationCase().getId()
    );
  }

  private static String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String like(String value) {
    return "%" + normalize(value) + "%";
  }

  private static String defaultFilename(String storedKey, String fallback) {
    if (!hasText(storedKey)) {
      return fallback;
    }
    int idx = storedKey.lastIndexOf('/');
    String name = idx >= 0 ? storedKey.substring(idx + 1) : storedKey;
    return hasText(name) ? name : fallback;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
  }

  public record SearchResponse(int total, List<PublicRepositoryItemDto> results) {}
}
