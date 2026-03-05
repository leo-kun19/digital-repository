package com.example.thesisrepo.profile;

import com.example.thesisrepo.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentProfile {
  @Id
  private Long userId;

  @OneToOne @MapsId
  @JoinColumn(name="user_id")
  private User user;

  private String name;

  @Column(name="student_number", nullable=false, unique=true)
  private String studentId;

  private String program;
  
  private String faculty;
}
