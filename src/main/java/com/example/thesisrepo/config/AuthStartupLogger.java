package com.example.thesisrepo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthStartupLogger implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AuthStartupLogger.class);

  private final AuthProperties authProperties;
  private final Environment environment;
  private final String uiBaseUrl;

  public AuthStartupLogger(
    AuthProperties authProperties,
    Environment environment,
    @Value("${app.ui.base-url:}") String uiBaseUrl
  ) {
    this.authProperties = authProperties;
    this.environment = environment;
    this.uiBaseUrl = normalizeBaseUrl(uiBaseUrl);
  }

  @Override
  public void run(ApplicationArguments args) {
    AuthMode mode = authProperties.getMode() == null ? AuthMode.LOCAL : authProperties.getMode();
    String redirectUri = environment.getProperty("spring.security.oauth2.client.registration.azure.redirect-uri");

    log.info(
      "Auth config: mode={}, app.ui.base-url={}, azure.redirect-uri={}",
      mode,
      StringUtils.hasText(uiBaseUrl) ? uiBaseUrl : "(empty)",
      StringUtils.hasText(redirectUri) ? redirectUri : "(not-set)"
    );
  }

  private static String normalizeBaseUrl(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    String trimmed = value.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
