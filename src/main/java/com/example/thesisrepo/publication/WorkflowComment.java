package com.example.thesisrepo.publication;

import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "workflow_comment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowComment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "case_id")
  private PublicationCase publicationCase;

  @ManyToOne
  @JoinColumn(name = "submission_version_id")
  private SubmissionVersion submissionVersion;

  @ManyToOne(optional = false)
  @JoinColumn(name = "author_user_id")
  private User author;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role authorRole;

  @Column(name = "author_email")
  private String authorEmail;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
