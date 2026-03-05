package com.example.thesisrepo.publication;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "submission_version", uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "version_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "case_id")
  private PublicationCase publicationCase;

  @Column(nullable = false)
  private Integer versionNumber;

  @Column(nullable = false)
  @JsonIgnore
  private String filePath;

  @Column(nullable = false)
  private String originalFilename;

  @Column(nullable = false)
  private String contentType;

  @Column(nullable = false)
  private Long fileSize;

  private String metadataTitle;
  private String metadataAuthors;
  private String metadataKeywords;
  private String metadataFaculty;
  private Integer metadataYear;

  @Column(columnDefinition = "text")
  private String abstractText;

  @ManyToOne
  @JoinColumn(name = "checklist_template_id")
  private ChecklistTemplate checklistTemplate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SubmissionStatus status;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
