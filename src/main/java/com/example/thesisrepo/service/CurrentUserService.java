package com.example.thesisrepo.service;

import com.example.thesisrepo.user.User;
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
    return users.findByEmail(auth.getName())
      .orElseThrow(() -> new IllegalStateException("User not found: " + auth.getName()));
  }
}
