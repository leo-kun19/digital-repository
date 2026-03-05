package com.example.thesisrepo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class UiRedirectController {

  private final String uiBaseUrl;

  public UiRedirectController(@Value("${app.ui.base-url:}") String uiBaseUrl) {
    this.uiBaseUrl = normalizeBaseUrl(uiBaseUrl);
  }

  @GetMapping("/login")
  public String login(HttpServletRequest request) {
    return routeToUiOrSpa("/login", request);
  }

  @GetMapping("/register")
  public String register(HttpServletRequest request) {
    return routeToUiOrSpa("/register", request);
  }

  private String routeToUiOrSpa(String path, HttpServletRequest request) {
    if (!StringUtils.hasText(uiBaseUrl)) {
      return "forward:/index.html";
    }

    UriComponentsBuilder target = UriComponentsBuilder.fromUriString(uiBaseUrl).path(path);
    if (StringUtils.hasText(request.getQueryString())) {
      target.query(request.getQueryString());
    }

    return "redirect:" + target.build(true).toUriString();
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
