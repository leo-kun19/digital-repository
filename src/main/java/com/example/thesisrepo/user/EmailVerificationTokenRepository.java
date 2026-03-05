package com.example.thesisrepo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

  Optional<EmailVerificationToken> findTopByUser_IdAndVerifiedFalseOrderByCreatedAtDesc(Long userId);

  @Modifying
  @Query("DELETE FROM EmailVerificationToken t WHERE t.user.id = :userId")
  void deleteAllByUserId(Long userId);
}
