package com.example.thesisrepo.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Ensures that deep links to the React router are served with the SPA index so we only
 * need one website (the Vite build) for every user role.
 */
@Controller
public class SpaForwardController {

  @GetMapping({
    "/onboarding",
    "/verify-email",
    "/admin",
    "/repo/**",
    "/student/**",
    "/admin/**",
    "/lecturer/**"
  })
  public String forwardSpaRoutes() {
    return "forward:/index.html";
  }
}
