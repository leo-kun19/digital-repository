package com.example.thesisrepo.master.repo;

import com.example.thesisrepo.master.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
  List<Faculty> findByActiveTrueOrderByNameAsc();
  Optional<Faculty> findByActiveTrueAndNameIgnoreCase(String name);
}
