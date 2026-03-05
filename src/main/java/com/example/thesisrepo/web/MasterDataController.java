package com.example.thesisrepo.web;

import com.example.thesisrepo.master.Faculty;
import com.example.thesisrepo.master.Program;
import com.example.thesisrepo.master.repo.FacultyRepository;
import com.example.thesisrepo.master.repo.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/master")
@RequiredArgsConstructor
public class MasterDataController {

  private final FacultyRepository faculties;
  private final ProgramRepository programs;

  @GetMapping("/faculties")
  public ResponseEntity<List<FacultyDto>> listFaculties() {
    return ResponseEntity.ok(faculties.findByActiveTrueOrderByNameAsc().stream()
      .map(this::toFacultyDto)
      .toList());
  }

  @GetMapping("/programs")
  public ResponseEntity<List<ProgramDto>> listPrograms(@RequestParam(required = false) Long facultyId) {
    List<Program> items = facultyId != null
      ? programs.findByActiveTrueAndFaculty_IdOrderByNameAsc(facultyId)
      : programs.findByActiveTrueOrderByNameAsc();
    return ResponseEntity.ok(items.stream().map(this::toProgramDto).toList());
  }

  private FacultyDto toFacultyDto(Faculty faculty) {
    return new FacultyDto(faculty.getId(), faculty.getCode(), faculty.getName());
  }

  private ProgramDto toProgramDto(Program program) {
    return new ProgramDto(program.getId(), program.getFaculty().getId(), program.getCode(), program.getName());
  }

  public record FacultyDto(Long id, String code, String name) {}
  public record ProgramDto(Long id, Long facultyId, String code, String name) {}
}
