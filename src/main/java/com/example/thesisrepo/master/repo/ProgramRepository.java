package com.example.thesisrepo.master.repo;

import com.example.thesisrepo.master.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long> {
  List<Program> findByActiveTrueOrderByNameAsc();
  List<Program> findByActiveTrueAndFaculty_IdOrderByNameAsc(Long facultyId);
  Optional<Program> findByActiveTrueAndFaculty_IdAndNameIgnoreCase(Long facultyId, String name);
}
