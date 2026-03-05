package com.example.thesisrepo.publication;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "checklist_template", uniqueConstraints = @UniqueConstraint(columnNames = {"publication_type", "version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistTemplate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "publication_type", nullable = false)
  private ChecklistScope publicationType;

  @Column(nullable = false)
  private Integer version;

  @Column(nullable = false)
  private boolean isActive;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
