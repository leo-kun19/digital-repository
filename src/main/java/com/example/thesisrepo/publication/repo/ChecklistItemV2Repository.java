package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.ChecklistItemV2;
import com.example.thesisrepo.publication.ChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemV2Repository extends JpaRepository<ChecklistItemV2, Long> {
  List<ChecklistItemV2> findByTemplateOrderByOrderIndexAsc(ChecklistTemplate template);
  void deleteByTemplate(ChecklistTemplate template);
}
