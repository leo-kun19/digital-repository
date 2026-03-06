package com.example.thesisrepo.config;

import com.example.thesisrepo.profile.LecturerProfileRepository;
import com.example.thesisrepo.profile.StudentProfileRepository;
import com.example.thesisrepo.service.EmailVerificationService;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import com.example.thesisrepo.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleBasedAuthSuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository users;
  private final StudentProfileRepository studentProfiles;
  private final LecturerProfileRepository lecturerProfiles;
  private final EmailVerificationService emailVerificationService;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
    throws IOException, ServletException {

    String email = resolveEmail(authentication);
    if (!hasText(email)) {
      response.sendRedirect(request.getContextPath() + "/login?error=Unable%20to%20resolve%20authenticated%20user");
      return;
    }

    User user = users.findByEmail(email.toLowerCase(Locale.ROOT)).orElse(null);
    if (user == null) {
      response.sendRedirect(request.getContextPath() + "/login?error=Authenticated%20user%20is%20not%20registered%20locally");
      return;
    }

    // OTP disabled — auto-verify after SSO login
    user.setEmailVerified(true);
    users.save(user);

    // Redirect based on profile completeness and role
    boolean profileComplete = isProfileComplete(user);
    if (!profileComplete && user.getRole() != Role.ADMIN) {
      response.sendRedirect(request.getContextPath() + "/onboarding");
    } else {
      response.sendRedirect(request.getContextPath() + "/");
    }
  }

  private String resolveEmail(Authentication authentication) {
    if (authentication == null) {
      return null;
    }
    if (hasText(authentication.getName())) {
      return authentication.getName().trim();
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof OidcUser oidcUser) {
      return firstClaim(oidcUser.getClaims());
    }
    if (principal instanceof OAuth2User oauth2User) {
      return firstClaim(oauth2User.getAttributes());
    }

    return null;
  }

  private static String firstClaim(Map<String, Object> claims) {
    for (String key : new String[]{"email", "preferred_username", "upn"}) {
      Object value = claims.get(key);
      if (value instanceof String text && hasText(text)) {
        return text.trim();
      }
    }
    return null;
  }

  private boolean isProfileComplete(User user) {
    if (user.getRole() == Role.STUDENT) {
      return studentProfiles.findByUserId(user.getId()).isPresent();
    }
    if (user.getRole() == Role.LECTURER) {
      return lecturerProfiles.findByUserId(user.getId()).isPresent();
    }
    return true; // ADMIN always complete
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
