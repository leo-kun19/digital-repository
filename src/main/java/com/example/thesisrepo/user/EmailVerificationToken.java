package com.example.thesisrepo.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "email_verification_token")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailVerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "otp_code", nullable = false, length = 6)
  private String otpCode;

  @Builder.Default
  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Builder.Default
  @Column(nullable = false)
  private boolean verified = false;

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
