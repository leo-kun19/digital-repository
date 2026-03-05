package com.example.thesisrepo.master;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "faculty_id")
  private Faculty faculty;

  private String code;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;
}
