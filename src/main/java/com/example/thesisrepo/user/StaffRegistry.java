package com.example.thesisrepo.user;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "staff_registry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private String fullName;

    private String department;

    private String studyProgram;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
