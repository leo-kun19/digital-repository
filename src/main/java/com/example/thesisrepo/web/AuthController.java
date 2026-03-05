package com.example.thesisrepo.web;

import com.example.thesisrepo.config.AuthMode;
import com.example.thesisrepo.config.AuthProperties;
import com.example.thesisrepo.master.Faculty;
import com.example.thesisrepo.master.Program;
import com.example.thesisrepo.master.repo.FacultyRepository;
import com.example.thesisrepo.master.repo.ProgramRepository;
import com.example.thesisrepo.profile.LecturerProfile;
import com.example.thesisrepo.profile.LecturerProfileRepository;
import com.example.thesisrepo.profile.StudentProfile;
import com.example.thesisrepo.profile.StudentProfileRepository;
import com.example.thesisrepo.service.CurrentUserService;
import com.example.thesisrepo.service.EmailVerificationService;
import com.example.thesisrepo.user.Role;
import com.example.thesisrepo.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final CurrentUserService currentUserService;
  private final AuthProperties authProperties;
  private final EmailVerificationService emailVerificationService;

  private final StudentProfileRepository studentProfiles;
  private final LecturerProfileRepository lecturerProfiles;
  private final FacultyRepository faculties;
  private final ProgramRepository programs;

  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> loginJson(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return authenticate(request.email(), request.password(), httpRequest);
  }

  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<?> loginForm(
    @RequestParam(name = "username", required = false) String username,
    @RequestParam(name = "email", required = false) String email,
    @RequestParam(name = "password") String password,
    HttpServletRequest httpRequest
  ) {
    return authenticate(firstNonBlank(email, username), password, httpRequest);
  }

  private ResponseEntity<MeResponse> authenticate(String email, String password, HttpServletRequest request) {
    if (!isLocalLoginEnabled()) {
      throw new ResponseStatusException(CONFLICT, "Local login is disabled. Use Microsoft SSO.");
    }

    String normalizedEmail = normalizeText(email);
    String normalizedPassword = normalizeText(password);
    if (normalizedEmail.isBlank() || normalizedPassword.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "Email and password are required.");
    }

    Authentication auth = authenticationManager.authenticate(
      new UsernamePasswordAuthenticationToken(normalizedEmail, normalizedPassword)
    );

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

    User user = currentUserService.requireCurrentUser();

    // Reset email verification on each login so OTP is required every sign-in
    if (user.isEmailVerified()) {
      user.setEmailVerified(false);
      // The user will need to verify again on this session
    }

    // Auto-send OTP on login
    emailVerificationService.generateAndSendOtp(user);

    return ResponseEntity.ok(toMeResponse(user));
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    return ResponseEntity.status(FORBIDDEN).body(Map.of(
      "error", "Self-registration is disabled. Use Sign up with Microsoft."
    ));
  }

  // ──── Email Verification (OTP) Endpoints ────

  @PostMapping("/send-otp")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<?> sendOtp() {
    User user = currentUserService.requireCurrentUser();
    try {
      var result = emailVerificationService.generateAndSendOtp(user);
      if (result.emailSent()) {
        return ResponseEntity.ok(Map.of(
          "message", "Verification code sent to " + maskEmail(user.getEmail()),
          "email", maskEmail(user.getEmail())
        ));
      } else {
        // Email failed (SMTP blocked on Railway) — return OTP directly
        return ResponseEntity.ok(Map.of(
          "message", "Email delivery unavailable. Use the code shown below.",
          "email", maskEmail(user.getEmail()),
          "fallbackOtp", result.otpCode()
        ));
      }
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of(
        "error", "Failed to generate verification code. Please try again later."
      ));
    }
  }

  @PostMapping("/verify-otp")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public ResponseEntity<?> verifyOtp(@RequestBody OtpRequest request) {
    User user = currentUserService.requireCurrentUser();

    if (request.otp() == null || request.otp().isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "Verification code is required.");
    }

    boolean verified = emailVerificationService.verifyOtp(user, request.otp());
    if (!verified) {
      return ResponseEntity.badRequest().body(Map.of(
        "error", "Invalid or expired verification code. Please request a new one."
      ));
    }

    return ResponseEntity.ok(toMeResponse(user));
  }

  // ──── Existing endpoints ────

  @PostMapping("/onboarding")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public ResponseEntity<MeResponse> onboarding(@RequestBody OnboardingRequest request) {
    User user = currentUserService.requireCurrentUser();

    String name = normalizeText(request.name());
    String facultyName = normalizeText(request.faculty());
    if (name.isBlank() || facultyName.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "Name and faculty are required.");
    }

    Faculty faculty = faculties.findByActiveTrueAndNameIgnoreCase(facultyName)
      .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid faculty/program"));

    if (user.getRole() == Role.STUDENT) {
      String programName = firstNonBlank(request.studyProgram(), request.program(), request.department());
      String studentId = normalizeText(request.studentId());
      if (programName.isBlank() || studentId.isBlank()) {
        throw new ResponseStatusException(BAD_REQUEST, "Program and student ID are required for students.");
      }

      Program program = programs.findByActiveTrueAndFaculty_IdAndNameIgnoreCase(faculty.getId(), programName)
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid faculty/program"));

      studentProfiles.findByStudentId(studentId)
        .filter(existing -> !existing.getUserId().equals(user.getId()))
        .ifPresent(existing -> {
          throw new ResponseStatusException(CONFLICT, "Student ID is already used by another account.");
        });

      StudentProfile profile = studentProfiles.findByUserId(user.getId())
        .orElseGet(() -> StudentProfile.builder().user(user).build());
      profile.setName(name);
      profile.setFaculty(faculty.getName());
      profile.setProgram(program.getName());
      profile.setStudentId(studentId);
      studentProfiles.save(profile);
    } else if (user.getRole() == Role.LECTURER) {
      String departmentName = firstNonBlank(request.studyProgram(), request.department(), request.program());
      if (departmentName.isBlank()) {
        throw new ResponseStatusException(BAD_REQUEST, "Study program is required for lecturers.");
      }

      Program department = programs.findByActiveTrueAndFaculty_IdAndNameIgnoreCase(faculty.getId(), departmentName)
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid faculty/program"));

      LecturerProfile profile = lecturerProfiles.findByUserId(user.getId())
        .orElseGet(() -> LecturerProfile.builder().user(user).build());
      profile.setName(name);
      profile.setFaculty(faculty.getName());
      profile.setDepartment(department.getName());
      lecturerProfiles.save(profile);
    } else {
      throw new ResponseStatusException(BAD_REQUEST, "Onboarding is not required for this role.");
    }

    return ResponseEntity.ok(toMeResponse(user));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request) throws Exception {
    request.logout();
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/config")
  public ResponseEntity<AuthConfigResponse> config() {
    AuthMode mode = authProperties.getMode() == null ? AuthMode.LOCAL : authProperties.getMode();
    return ResponseEntity.ok(new AuthConfigResponse(
      mode.name(),
      isLocalLoginEnabled(mode),
      isSsoEnabled(mode),
      "/oauth2/authorization/azure"
    ));
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<MeResponse> me() {
    User user = currentUserService.requireCurrentUser();
    return ResponseEntity.ok(toMeResponse(user));
  }

  private MeResponse toMeResponse(User user) {
    if (user.getRole() == Role.ADMIN) {
      return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.getAuthProvider().name(),
        true,
        user.isEmailVerified(),
        null,
        null,
        null,
        null,
        null,
        null
      );
    }

    if (user.getRole() == Role.STUDENT) {
      StudentProfile profile = studentProfiles.findByUserId(user.getId()).orElse(null);
      String name = profile != null ? normalizeText(profile.getName()) : "";
      String faculty = profile != null ? normalizeText(profile.getFaculty()) : "";
      String program = profile != null ? normalizeText(profile.getProgram()) : "";
      String studentId = profile != null ? normalizeText(profile.getStudentId()) : "";
      boolean complete = !name.isBlank() && !faculty.isBlank() && !program.isBlank() && !studentId.isBlank();
      return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.getAuthProvider().name(),
        complete,
        user.isEmailVerified(),
        emptyToNull(name),
        emptyToNull(name),
        emptyToNull(faculty),
        emptyToNull(program),
        null,
        emptyToNull(studentId)
      );
    }

    if (user.getRole() == Role.LECTURER) {
      LecturerProfile profile = lecturerProfiles.findByUserId(user.getId()).orElse(null);
      String name = profile != null ? normalizeText(profile.getName()) : "";
      String faculty = profile != null ? normalizeText(profile.getFaculty()) : "";
      String department = profile != null ? normalizeText(profile.getDepartment()) : "";
      return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.getAuthProvider().name(),
        true,
        user.isEmailVerified(),
        emptyToNull(name),
        emptyToNull(name),
        emptyToNull(faculty),
        null,
        emptyToNull(department),
        null
      );
    }

    return new MeResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getAuthProvider().name(), false, user.isEmailVerified(), null, null, null, null, null, null);
  }

  // ──── Helpers ────

  private static String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.trim().isEmpty()) {
      return first.trim();
    }
    return second == null ? "" : second.trim();
  }

  private static String firstNonBlank(String first, String second, String third) {
    String value = firstNonBlank(first, second);
    if (!value.isBlank()) {
      return value;
    }
    return third == null ? "" : third.trim();
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String maskEmail(String email) {
    if (email == null || !email.contains("@")) return email;
    String[] parts = email.split("@");
    String local = parts[0];
    if (local.length() <= 3) {
      return local.charAt(0) + "***@" + parts[1];
    }
    return local.substring(0, 3) + "***@" + parts[1];
  }

  private boolean isLocalLoginEnabled() {
    return isLocalLoginEnabled(authProperties.getMode());
  }

  private static boolean isLocalLoginEnabled(AuthMode mode) {
    if (mode == null) {
      return true;
    }
    return mode == AuthMode.LOCAL || mode == AuthMode.HYBRID;
  }

  private static boolean isSsoEnabled(AuthMode mode) {
    if (mode == null) {
      return false;
    }
    return mode == AuthMode.SSO || mode == AuthMode.AAD || mode == AuthMode.HYBRID;
  }

  // ──── Records ────

  public record LoginRequest(String email, String password) {}

  public record RegisterRequest(
    String email,
    String password,
    String name,
    Role role,
    String studentId,
    String program,
    String faculty,
    String department,
    String fullName
  ) {}

  public record OnboardingRequest(
    String name,
    String faculty,
    String studyProgram,
    String program,
    String studentId,
    String department
  ) {}

  public record OtpRequest(String otp) {}

  public record MeResponse(
    Long id,
    String email,
    String role,
    String authProvider,
    boolean profileComplete,
    boolean emailVerified,
    String name,
    String fullName,
    String faculty,
    String program,
    String department,
    String studentId
  ) {}

  public record AuthConfigResponse(
    String mode,
    boolean localEnabled,
    boolean ssoEnabled,
    String ssoUrl
  ) {}
}
