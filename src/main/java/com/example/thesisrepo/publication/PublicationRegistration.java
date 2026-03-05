package com.example.thesisrepo.publication;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "publication_registration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicationRegistration {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false)
  @JoinColumn(name = "case_id", unique = true)
  private PublicationCase publicationCase;

  @Column(nullable = false)
  private String title;

  @Column(name = "publication_year")
  private Integer year;

  @Column(name = "article_publish_in")
  private String articlePublishIn;

  private String faculty;

  @Column(name = "student_id_number")
  private String studentIdNumber;

  @Column(name = "author_name")
  private String authorName;

  private Instant permissionAcceptedAt;

  private Instant submittedAt;

  private Instant supervisorDecisionAt;

  @Column(columnDefinition = "text")
  private String supervisorDecisionNote;
}
