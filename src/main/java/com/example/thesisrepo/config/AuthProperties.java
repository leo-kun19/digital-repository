package com.example.thesisrepo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
  private AuthMode mode = AuthMode.LOCAL;

  /**
   * Optional override list (simple way to make an account ADMIN).
   * You can keep roles in DB later; this is just a shortcut.
   */
  private List<String> adminEmails = new ArrayList<>();
}
