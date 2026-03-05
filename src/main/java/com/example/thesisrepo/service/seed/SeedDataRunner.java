package com.example.thesisrepo.service.seed;

import com.example.thesisrepo.profile.LecturerProfile;
import com.example.thesisrepo.profile.LecturerProfileRepository;
import com.example.thesisrepo.profile.StudentProfile;
import com.example.thesisrepo.profile.StudentProfileRepository;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import com.example.thesisrepo.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class SeedDataRunner implements CommandLineRunner {
  private final UserRepository users;
  private final StudentProfileRepository studentProfiles;
  private final LecturerProfileRepository lecturerProfiles;
  private final PasswordEncoder encoder;

  @Override
  @Transactional
  public void run(String... args) {
    User admin = ensureUser("admin@example.com", "Admin123!", Role.ADMIN);
    User lecturer = ensureUser("lecturer1@example.com", "Lecturer123!", Role.LECTURER);
    User student = ensureUser("student1@example.com", "Student123!", Role.STUDENT);

    ensureLecturerProfile(
      lecturer,
      "Lecturer One",
      "Information System",
      "Faculty of Engineering and Technology (FET)"
    );
    ensureStudentProfile(
      student,
      "Student One",
      "S-001",
      "Information System",
      "Faculty of Engineering and Technology (FET)"
    );

    // Avoid unused warnings while keeping deterministic base users.
    admin.getId();
  }

  private User ensureUser(String email, String rawPassword, Role role) {
    return users.findByEmail(email).orElseGet(() -> users.save(
      User.builder()
        .email(email)
        .passwordHash(encoder.encode(rawPassword))
        .role(role)
        .build()
    ));
  }

  private void ensureStudentProfile(User user, String name, String studentNumber, String program, String faculty) {
    if (studentProfiles.findByUserId(user.getId()).isPresent()) {
      return;
    }
    studentProfiles.save(StudentProfile.builder()
      .user(user)
      .name(name)
      .studentId(studentNumber)
      .program(program)
      .faculty(faculty)
      .build());
  }

  private void ensureLecturerProfile(User user, String name, String department, String faculty) {
    if (lecturerProfiles.findByUserId(user.getId()).isPresent()) {
      return;
    }
    lecturerProfiles.save(LecturerProfile.builder()
      .user(user)
      .name(name)
      .department(department)
      .faculty(faculty)
      .build());
  }

}
