package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.ChecklistResult;
import com.example.thesisrepo.publication.SubmissionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistResultRepository extends JpaRepository<ChecklistResult, Long> {
  List<ChecklistResult> findBySubmissionVersion(SubmissionVersion submissionVersion);
  void deleteBySubmissionVersion(SubmissionVersion submissionVersion);
  boolean existsByChecklistItem_Template_Id(Long templateId);
}
