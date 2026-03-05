package com.example.thesisrepo.publication;

import com.example.thesisrepo.user.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "case_id", nullable = false)
  private Long caseId;

  @Column(name = "submission_version_id")
  private Long submissionVersionId;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "actor_email")
  private String actorEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_role", nullable = false)
  private Role actorRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 80)
  private AuditEventType eventType;

  @Column(columnDefinition = "text")
  private String message;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
