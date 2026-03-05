package com.example.thesisrepo.profile;

import com.example.thesisrepo.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LecturerProfile {
  @Id
  private Long userId;

  @OneToOne @MapsId
  @JoinColumn(name="user_id")
  private User user;

  private String name;

  private String department;
  
  private String faculty;
}
