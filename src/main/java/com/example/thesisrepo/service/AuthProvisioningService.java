package com.example.thesisrepo.service;

import com.example.thesisrepo.user.AuthProvider;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import com.example.thesisrepo.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthProvisioningService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Set<String> adminEmails;

  private static final String STUDENT_DOMAIN = "@my.sampoernauniversity.ac.id";
  private static final String STAFF_DOMAIN = "@sampoernauniversity.ac.id";

  public AuthProvisioningService(
    UserRepository users,
    PasswordEncoder passwordEncoder,
    @Value("${app.auth.admin-emails:}") String adminEmailsConfig
  ) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.adminEmails = Arrays.stream(adminEmailsConfig.split(","))
      .map(String::trim)
      .map(email -> email.toLowerCase(Locale.ROOT))
      .filter(email -> !email.isBlank())
      .collect(Collectors.toUnmodifiableSet());
  }

  public ProvisioningResult provision(OidcUser oidcUser) {
    EmailClaim emailClaim = resolveEmailClaim(oidcUser.getClaims());
    if (emailClaim == null) {
      throw new OAuth2AuthenticationException(new OAuth2Error("missing_email", "Email claim is missing", null));
    }

    String email = emailClaim.value().trim().toLowerCase(Locale.ROOT);
    Role inferredRole = roleFromEmailDomain(email);
    User user = users.findByEmail(email).orElseGet(() -> users.save(User.builder()
      .email(email)
      .role(inferredRole)
      .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()))
      .authProvider(AuthProvider.AAD)
      .externalSubject(oidcUser.getSubject())
      .lastLoginAt(Instant.now())
      .build()));

    if (inferredRole == Role.ADMIN && user.getRole() != Role.ADMIN) {
      user.setRole(Role.ADMIN);
    }

    user.setAuthProvider(AuthProvider.AAD);
    user.setExternalSubject(oidcUser.getSubject());
    user.setLastLoginAt(Instant.now());
    users.save(user);

    return new ProvisioningResult(user, emailClaim.key());
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

  private Role roleFromEmailDomain(String email) {
    if (adminEmails.contains(email)) {
      return Role.ADMIN;
    }
    if (email.endsWith(STUDENT_DOMAIN)) {
      return Role.STUDENT;
    }
    if (email.endsWith(STAFF_DOMAIN)) {
      return Role.LECTURER;
    }
    throw new OAuth2AuthenticationException(
      new OAuth2Error("domain_not_allowed", "Only university accounts are allowed.", null)
    );
  }

  public record ProvisioningResult(User user, String nameAttributeKey) {}

  private record EmailClaim(String value, String key) {}
}
