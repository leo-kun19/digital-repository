package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.ChecklistScope;
import com.example.thesisrepo.publication.ChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, Long> {
  Optional<ChecklistTemplate> findFirstByPublicationTypeAndIsActiveTrue(ChecklistScope publicationType);
  List<ChecklistTemplate> findByPublicationTypeOrderByVersionDesc(ChecklistScope publicationType);
  Optional<ChecklistTemplate> findTopByPublicationTypeOrderByVersionDesc(ChecklistScope publicationType);
}
