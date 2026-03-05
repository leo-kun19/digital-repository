package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.PublishedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PublishedItemRepository extends JpaRepository<PublishedItem, Long>, JpaSpecificationExecutor<PublishedItem> {
  boolean existsByPublicationCase_Id(Long caseId);
  Optional<PublishedItem> findByPublicationCase_Id(Long caseId);
  void deleteByPublicationCase_Id(Long caseId);
}
