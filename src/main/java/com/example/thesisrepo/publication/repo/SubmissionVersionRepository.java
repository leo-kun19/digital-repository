package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.PublicationCase;
import com.example.thesisrepo.publication.SubmissionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, Long> {
  List<SubmissionVersion> findByPublicationCaseOrderByVersionNumberDesc(PublicationCase publicationCase);
  Optional<SubmissionVersion> findTopByPublicationCaseOrderByVersionNumberDesc(PublicationCase publicationCase);
}
