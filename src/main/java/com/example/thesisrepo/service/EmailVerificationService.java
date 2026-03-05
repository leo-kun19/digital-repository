package com.example.thesisrepo.service;

import com.example.thesisrepo.user.EmailVerificationToken;
import com.example.thesisrepo.user.EmailVerificationTokenRepository;
import com.example.thesisrepo.user.User;
import com.example.thesisrepo.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

  private final EmailVerificationTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final EmailService emailService;

  private static final SecureRandom RANDOM = new SecureRandom();

  @Value("${app.email.otp-expiry-minutes:10}")
  private int otpExpiryMinutes;

  @Value("${app.email.otp-length:6}")
  private int otpLength;
  /**
   * Result of OTP generation — carries the code and whether email was sent.
   */
  public record OtpResult(String otpCode, boolean emailSent) {}

  /**
   * Generate a new OTP, save it, and attempt to send via email.
   * Returns OtpResult so callers know if email delivery succeeded.
   */
  @Transactional
  public OtpResult generateAndSendOtp(User user) {
    // Invalidate old tokens
    tokenRepository.deleteAllByUserId(user.getId());

    String otpCode = generateOtp();
    Instant now = Instant.now();
    Instant expiresAt = now.plus(otpExpiryMinutes, ChronoUnit.MINUTES);

    EmailVerificationToken token = EmailVerificationToken.builder()
      .user(user)
      .otpCode(otpCode)
      .createdAt(now)
      .expiresAt(expiresAt)
      .verified(false)
      .build();

    tokenRepository.save(token);
    log.info("Generated and saved OTP for user {} (id={}, otp={})", user.getEmail(), user.getId(), otpCode);

    // Send email — caught so transaction still commits with the saved OTP
    boolean emailSent = false;
    try {
      emailService.sendOtpEmail(user.getEmail(), otpCode, otpExpiryMinutes);
      emailSent = true;
    } catch (Exception e) {
      log.error("Email sending failed for user {} but OTP is saved in DB. OTP: {}", user.getEmail(), otpCode, e);
    }
    return new OtpResult(otpCode, emailSent);
  }

  /**
   * Verify user-submitted OTP. Returns true if valid.
   */
  @Transactional
  public boolean verifyOtp(User user, String submittedOtp) {
    if (submittedOtp == null || submittedOtp.isBlank()) {
      return false;
    }

    String trimmed = submittedOtp.trim();

    EmailVerificationToken token = tokenRepository
      .findTopByUser_IdAndVerifiedFalseOrderByCreatedAtDesc(user.getId())
      .orElse(null);

    if (token == null) {
      log.warn("No pending OTP found for user {} (id={})", user.getEmail(), user.getId());
      return false;
    }

    if (token.isExpired()) {
      log.warn("OTP expired for user {} (created={}, expires={}, now={})",
        user.getEmail(), token.getCreatedAt(), token.getExpiresAt(), Instant.now());
      return false;
    }

    if (!token.getOtpCode().equals(trimmed)) {
      log.warn("OTP mismatch for user {}: submitted='{}' stored='{}' (len: {} vs {})",
        user.getEmail(), trimmed, token.getOtpCode(), trimmed.length(), token.getOtpCode().length());
      return false;
    }

    // Mark token as verified
    token.setVerified(true);
    tokenRepository.save(token);

    // Mark user as email-verified
    user.setEmailVerified(true);
    userRepository.save(user);

    log.info("Email verified successfully for user {}", user.getEmail());
    return true;
  }

  private String generateOtp() {
    int bound = (int) Math.pow(10, otpLength);
    int otp = RANDOM.nextInt(bound);
    return String.format("%0" + otpLength + "d", otp);
  }
}
