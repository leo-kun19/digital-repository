package com.example.thesisrepo.publication;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "clearance_form")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClearanceForm {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false)
  @JoinColumn(name = "case_id", unique = true)
  private PublicationCase publicationCase;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClearanceStatus status;

  private Instant submittedAt;
  private Instant approvedAt;

  @Column(columnDefinition = "text")
  private String note;
}
