package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.CaseStatus;
import com.example.thesisrepo.publication.PublicationCase;
import com.example.thesisrepo.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicationCaseRepository extends JpaRepository<PublicationCase, Long> {
  List<PublicationCase> findByStudentOrderByUpdatedAtDesc(User student);
  List<PublicationCase> findByStatusInOrderByUpdatedAtDesc(List<CaseStatus> statuses);
}
