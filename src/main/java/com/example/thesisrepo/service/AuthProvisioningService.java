package com.example.thesisrepo.service;

import com.example.thesisrepo.user.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthProvisioningService {

  private final UserRepository users;
  private final StaffRegistryRepository staffRegistry;
  private final PasswordEncoder passwordEncoder;

  private static final String STUDENT_DOMAIN = "@my.sampoernauniversity.ac.id";
  private static final String STAFF_DOMAIN = "@sampoernauniversity.ac.id";

  public AuthProvisioningService(
    UserRepository users,
    StaffRegistryRepository staffRegistry,
    PasswordEncoder passwordEncoder
  ) {
    this.users = users;
    this.staffRegistry = staffRegistry;
    this.passwordEncoder = passwordEncoder;
  }

  public ProvisioningResult provision(OidcUser oidcUser) {
    EmailClaim emailClaim = resolveEmailClaim(oidcUser.getClaims());
    if (emailClaim == null) {
      throw new OAuth2AuthenticationException(new OAuth2Error("missing_email", "Email claim is missing", null));
    }

    String email = emailClaim.value().trim().toLowerCase(Locale.ROOT);
    Role inferredRole = roleFromEmail(email);
    User user = users.findByEmail(email).orElseGet(() -> users.save(User.builder()
      .email(email)
      .role(inferredRole)
      .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()))
      .authProvider(AuthProvider.AAD)
      .externalSubject(oidcUser.getSubject())
      .lastLoginAt(Instant.now())
      .build()));

    // Always enforce role from staff registry (in case role changed in DB)
    if (user.getRole() != inferredRole) {
      user.setRole(inferredRole);
    }

    user.setAuthProvider(AuthProvider.AAD);
    user.setExternalSubject(oidcUser.getSubject());
    user.setLastLoginAt(Instant.now());
    users.save(user);

    return new ProvisioningResult(user, emailClaim.key());
  }

  /**
   * Determine role from email:
   * 1. Check staff_registry table (for ADMIN or LECTURER)
   * 2. If not in staff_registry, check domain
   *    - @my.sampoernauniversity.ac.id → STUDENT
   *    - @sampoernauniversity.ac.id → LECTURER (default for staff not in registry)
   *    - other → rejected
   */
  private Role roleFromEmail(String email) {
    // Check staff registry first
    var staffEntry = staffRegistry.findByEmailIgnoreCase(email);
    if (staffEntry.isPresent()) {
      return staffEntry.get().getRole();
    }

    // Fallback to domain-based role
    if (email.endsWith(STUDENT_DOMAIN)) {
      return Role.STUDENT;
    }
    if (email.endsWith(STAFF_DOMAIN)) {
      return Role.LECTURER; // Default for unregistered staff
    }
    throw new OAuth2AuthenticationException(
      new OAuth2Error("domain_not_allowed", "Only university accounts are allowed.", null)
    );
  }

  private static EmailClaim resolveEmailClaim(Map<String, Object> claims) {
    for (String key : List.of("email", "preferred_username", "upn")) {
      Object value = claims.get(key);
      if (value instanceof String email && !email.isBlank()) {
        return new EmailClaim(email.trim(), key);
      }
    }
    return null;
  }

  public record ProvisioningResult(User user, String nameAttributeKey) {}

  private record EmailClaim(String value, String key) {}
}
