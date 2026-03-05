package com.example.thesisrepo.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name="users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable=false, unique=true)
  private String email;

  @JsonIgnore
  @Column(name="password_hash", nullable=false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable=false)
  private Role role;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "auth_provider", nullable = false)
  private AuthProvider authProvider = AuthProvider.LOCAL;

  @Column(name = "external_subject")
  private String externalSubject;

  @Builder.Default
  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = false;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;
}
