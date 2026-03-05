package com.example.thesisrepo.publication;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "checklist_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "submission_version_id")
  private SubmissionVersion submissionVersion;

  @ManyToOne(optional = false)
  @JoinColumn(name = "checklist_item_id")
  private ChecklistItemV2 checklistItem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReviewOutcome passFail;

  @Column(columnDefinition = "text")
  private String note;
}
