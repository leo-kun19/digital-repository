package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.PublicationCase;
import com.example.thesisrepo.publication.PublicationRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicationRegistrationRepository extends JpaRepository<PublicationRegistration, Long> {
  Optional<PublicationRegistration> findByPublicationCase(PublicationCase publicationCase);
  List<PublicationRegistration> findByPublicationCaseIn(List<PublicationCase> cases);
}
