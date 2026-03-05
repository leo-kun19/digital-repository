package com.example.thesisrepo.publication;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "published_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishedItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false)
  @JoinColumn(name = "case_id", unique = true)
  private PublicationCase publicationCase;

  @ManyToOne
  @JoinColumn(name = "submission_version_id")
  private SubmissionVersion submissionVersion;

  @Column(nullable = false)
  private Instant publishedAt;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String authors;

  @Column(name = "author_name")
  private String authorName;

  private String faculty;
  private String program;
  @Column(name = "publication_year")
  private Integer year;

  @Column(columnDefinition = "text")
  private String keywords;

  @Column(columnDefinition = "text")
  private String abstractText;
}
