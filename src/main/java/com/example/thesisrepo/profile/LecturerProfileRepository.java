package com.example.thesisrepo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LecturerProfileRepository extends JpaRepository<LecturerProfile, Long> {
  Optional<LecturerProfile> findByUserId(Long userId);
}
