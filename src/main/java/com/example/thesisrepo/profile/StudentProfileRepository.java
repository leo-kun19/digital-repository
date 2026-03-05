package com.example.thesisrepo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
  Optional<StudentProfile> findByUserId(Long userId);
  Optional<StudentProfile> findByStudentId(String studentId);
  List<StudentProfile> findByUserIdIn(List<Long> userIds);
}
