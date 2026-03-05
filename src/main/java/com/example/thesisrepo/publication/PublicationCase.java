package com.example.thesisrepo.publication;

import com.example.thesisrepo.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "publication_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicationCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "student_user_id")
  private User student;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PublicationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CaseStatus status;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;
}
