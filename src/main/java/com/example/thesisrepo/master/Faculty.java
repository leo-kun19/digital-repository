package com.example.thesisrepo.master;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "faculty")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Faculty {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String code;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;
}
