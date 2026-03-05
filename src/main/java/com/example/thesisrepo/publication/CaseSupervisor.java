package com.example.thesisrepo.publication;

import com.example.thesisrepo.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "case_supervisor", uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "lecturer_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseSupervisor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "case_id")
  private PublicationCase publicationCase;

  @ManyToOne(optional = false)
  @JoinColumn(name = "lecturer_user_id")
  private User lecturer;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "decision_note", length = 1000)
  private String decisionNote;

  public boolean isPendingDecision() {
    return approvedAt == null && rejectedAt == null;
  }

  public void approve() {
    this.approvedAt = Instant.now();
    this.rejectedAt = null;
    this.decisionNote = null;
  }

  public void reject(String note) {
    this.rejectedAt = Instant.now();
    this.approvedAt = null;
    this.decisionNote = note;
  }
}
