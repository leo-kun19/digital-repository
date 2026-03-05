package com.example.thesisrepo.service.workflow;

import com.example.thesisrepo.publication.PublicationCase;
import com.example.thesisrepo.publication.SubmissionVersion;
import com.example.thesisrepo.publication.WorkflowComment;
import com.example.thesisrepo.publication.AuditEventType;
import com.example.thesisrepo.publication.repo.AuditEventRepository;
import com.example.thesisrepo.publication.repo.SubmissionVersionRepository;
import com.example.thesisrepo.publication.repo.WorkflowCommentRepository;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.web.dto.TimelineItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaseTimelineService {

  private final AuditEventRepository auditEvents;
  private final WorkflowCommentRepository comments;
  private final SubmissionVersionRepository submissionVersions;

  public List<TimelineItemDto> buildTimeline(PublicationCase publicationCase) {
    List<TimelineItemDto> timeline = new ArrayList<>();
    List<AuditEventType> excludedEventTypes = List.of(
      AuditEventType.SUBMISSION_UPLOADED,
      AuditEventType.FEEDBACK_ADDED
    );

    auditEvents.findByCaseIdOrderByCreatedAtDesc(publicationCase.getId())
      .stream()
      .filter(event -> !excludedEventTypes.contains(event.getEventType()))
      .forEach(event -> timeline.add(new TimelineItemDto(
        event.getCreatedAt(),
        event.getActorRole(),
        event.getActorEmail(),
        event.getEventType().name(),
        event.getMessage(),
        event.getSubmissionVersionId()
      )));

    comments.findByPublicationCaseOrderByCreatedAtAsc(publicationCase)
      .forEach(comment -> {
        String body = comment.getBody();
        String snippet = summarizeComment(body, "Feedback posted");
        String message = body == null || body.isBlank() ? "Feedback posted" : "Feedback posted: " + snippet;
        timeline.add(new TimelineItemDto(
          comment.getCreatedAt(),
          comment.getAuthorRole(),
          comment.getAuthorEmail() != null ? comment.getAuthorEmail() : comment.getAuthor().getEmail(),
          "COMMENT",
          message,
          comment.getSubmissionVersion() != null ? comment.getSubmissionVersion().getId() : null
        ));
      });

    submissionVersions.findByPublicationCaseOrderByVersionNumberDesc(publicationCase)
      .forEach(version -> timeline.add(toSubmissionTimelineItem(publicationCase, version)));

    timeline.sort(Comparator.comparing(TimelineItemDto::at, Comparator.nullsLast(Comparator.reverseOrder())));
    return timeline;
  }

  private static TimelineItemDto toSubmissionTimelineItem(PublicationCase publicationCase, SubmissionVersion version) {
    String message = "Uploaded submission v" + version.getVersionNumber();
    if (version.getOriginalFilename() != null && !version.getOriginalFilename().isBlank()) {
      message += " (" + version.getOriginalFilename() + ")";
    }
    return new TimelineItemDto(
      version.getCreatedAt(),
      Role.STUDENT,
      publicationCase.getStudent().getEmail(),
      "SUBMISSION_UPLOAD",
      message,
      version.getId()
    );
  }

  private static String summarizeComment(String body, String fallback) {
    if (body == null || body.isBlank()) {
      return fallback;
    }
    String trimmed = body.trim();
    return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
  }
}
