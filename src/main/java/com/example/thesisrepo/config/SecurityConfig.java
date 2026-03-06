package com.example.thesisrepo.config;

import com.example.thesisrepo.service.OidcProvisioningUserService;
import com.example.thesisrepo.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final UserDetailsServiceImpl userDetailsService;
  private final OidcProvisioningUserService oidcUserService;
  private final RoleBasedAuthSuccessHandler successHandler;
  private final AuthProperties authProperties;
  private final String uiBaseUrl;

  public SecurityConfig(
    UserDetailsServiceImpl userDetailsService,
    OidcProvisioningUserService oidcUserService,
    RoleBasedAuthSuccessHandler successHandler,
    AuthProperties authProperties,
    @Value("${app.ui.base-url:}") String uiBaseUrl
  ) {
    this.userDetailsService = userDetailsService;
    this.oidcUserService = oidcUserService;
    this.successHandler = successHandler;
    this.authProperties = authProperties;
    this.uiBaseUrl = normalizeBaseUrl(uiBaseUrl);
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    PasswordEncoder passwordEncoder,
    ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository
  ) throws Exception {
    AuthMode mode = authProperties.getMode() == null ? AuthMode.LOCAL : authProperties.getMode();
    boolean localEnabled = isLocalLoginEnabled(mode);
    boolean ssoEnabled = isSsoEnabled(mode);

    String loginRedirect = resolveUiRoute("/login");

    http
      .csrf(csrf -> csrf.disable())
      .authenticationProvider(authenticationProvider(passwordEncoder))
      .authorizeHttpRequests(auth -> auth
        // SPA + public assets
        .requestMatchers(
          "/",
          "/index.html",
          "/assets/**",
          "/favicon.ico",
          "/login",
          "/register",
          "/verify-email",
          "/error"
        ).permitAll()

        // OAuth endpoints must be public
        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

        // Auth endpoints public (login/register/logout/me)
        .requestMatchers("/api/auth/**").permitAll()

        // Public repo endpoints
        .requestMatchers("/api/public/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/public/repository/*/download").authenticated()
        .requestMatchers("/api/supervisors/**").hasAnyRole("STUDENT", "ADMIN")

        // Role APIs
        .requestMatchers("/api/student/**").hasRole("STUDENT")
        .requestMatchers("/api/lecturer/**").hasRole("LECTURER")
        .requestMatchers("/api/admin/**").hasRole("ADMIN")

        .anyRequest().authenticated()
      )
      .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
        if (isApiRequest(request)) {
          response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
          return;
        }
        response.sendRedirect(loginRedirect);
      }))
      .logout(logout -> logout
        .logoutUrl("/logout")
        .logoutSuccessHandler((request, response, authentication) -> {
          // Clear Spring session
          request.getSession().invalidate();
          // Redirect to Microsoft logout to clear SSO session
          String postLogoutRedirect = URLEncoder.encode(
            resolveUiRoute("/login"), StandardCharsets.UTF_8);
          response.sendRedirect(
            "https://login.microsoftonline.com/common/oauth2/v2.0/logout?post_logout_redirect_uri=" + postLogoutRedirect);
        })
        .invalidateHttpSession(true)
        .deleteCookies("JSESSIONID")
        .permitAll()
      );

    if (localEnabled) {
      http.formLogin(form -> form
        .loginPage("/login")
        .permitAll()
      );
    } else {
      http.formLogin(AbstractHttpConfigurer::disable);
      http.httpBasic(AbstractHttpConfigurer::disable);
    }

    if (ssoEnabled && clientRegistrationRepository.getIfAvailable() != null) {
      var resolver = new org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository.getIfAvailable(), "/oauth2/authorization");
      resolver.setAuthorizationRequestCustomizer(customizer ->
        customizer.additionalParameters(params -> params.put("prompt", "select_account")));

      http.oauth2Login(oauth -> oauth
        .loginPage("/login")
        .authorizationEndpoint(auth -> auth.authorizationRequestResolver(resolver))
        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
        .successHandler(successHandler)
        .failureHandler(oauthFailureHandler())
      );
    }

    return http.build();
  }

  @Bean
  public AuthenticationFailureHandler oauthFailureHandler() {
    return (request, response, exception) -> {
      String message = resolveOAuthErrorMessage(exception);
      String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
      response.sendRedirect(resolveUiRoute("/login") + "?error=" + encoded);
    };
  }

  private static boolean isLocalLoginEnabled(AuthMode mode) {
    return mode == AuthMode.LOCAL || mode == AuthMode.HYBRID;
  }

  private static boolean isSsoEnabled(AuthMode mode) {
    return mode == AuthMode.SSO || mode == AuthMode.AAD || mode == AuthMode.HYBRID;
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

  private static boolean isApiRequest(HttpServletRequest request) {
    String contextPath = request.getContextPath();
    String uri = request.getRequestURI();
    if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
      uri = uri.substring(contextPath.length());
    }
    return uri.startsWith("/api/");
  }

  private String resolveUiRoute(String path) {
    if (StringUtils.hasText(uiBaseUrl)) {
      return uiBaseUrl + path;
    }
    return path;
  }

  private static String resolveOAuthErrorMessage(Exception exception) {
    if (exception == null || !StringUtils.hasText(exception.getMessage())) {
      return "SSO login failed";
    }

    String message = exception.getMessage().trim();
    String lower = message.toLowerCase(Locale.ROOT);

    if (lower.contains("invalid_token_response")) {
      return "Microsoft sign-in failed. Verify AAD tenant/client settings and redirect URI.";
    }
    if (lower.contains("domain_not_allowed")) {
      return "Only university accounts are allowed.";
    }
    if (lower.contains("staff_access_disabled")) {
      return "Lecturer access is not enabled. Supervisors are selected from the directory.";
    }

    return message.length() > 220 ? message.substring(0, 220) + "..." : message;
  }
}
