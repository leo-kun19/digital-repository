package com.example.thesisrepo.service;

import com.example.thesisrepo.user.User;
import java.util.Locale;
import com.example.thesisrepo.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
  private final UserRepository users;

  public User me() {
    return requireCurrentUser();
  }

  public User requireCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null) {
      throw new IllegalStateException("No authenticated user");
    }
    String email = auth.getName().toLowerCase(Locale.ROOT);
    return users.findByEmail(email)
      .orElseThrow(() -> new IllegalStateException("User not found: " + email));
  }
}
