package com.example.thesisrepo.web;

import com.example.thesisrepo.service.SupervisorDirectoryService;
import com.example.thesisrepo.user.StaffRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supervisors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
public class SupervisorDirectoryController {

  private final SupervisorDirectoryService supervisorDirectoryService;

  @GetMapping
  public List<SupervisorDto> list(
    @RequestParam(required = false) String studyProgram,
    @RequestParam(required = false) String faculty
  ) {
    return supervisorDirectoryService.listActiveSupervisors(faculty, studyProgram).stream()
      .map(this::toDto)
      .toList();
  }

  private SupervisorDto toDto(StaffRegistry entry) {
    return new SupervisorDto(
      entry.getEmail(),
      supervisorDirectoryService.displayName(entry),
      entry.getDepartment(),
      entry.getStudyProgram()
    );
  }

  public record SupervisorDto(
    String email,
    String name,
    String faculty,
    String studyProgram
  ) {}
}
