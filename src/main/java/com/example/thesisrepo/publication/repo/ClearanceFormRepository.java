package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.ClearanceForm;
import com.example.thesisrepo.publication.PublicationCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClearanceFormRepository extends JpaRepository<ClearanceForm, Long> {
  Optional<ClearanceForm> findByPublicationCase(PublicationCase publicationCase);
}
