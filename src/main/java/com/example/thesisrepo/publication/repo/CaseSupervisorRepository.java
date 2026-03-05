package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.CaseSupervisor;
import com.example.thesisrepo.publication.PublicationCase;
import com.example.thesisrepo.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaseSupervisorRepository extends JpaRepository<CaseSupervisor, Long> {
  List<CaseSupervisor> findByLecturer(User lecturer);
  List<CaseSupervisor> findByPublicationCase(PublicationCase publicationCase);
  boolean existsByPublicationCaseAndLecturer(PublicationCase publicationCase, User lecturer);
  Optional<CaseSupervisor> findByPublicationCaseAndLecturer(PublicationCase publicationCase, User lecturer);

  @Query("""
    select cs from CaseSupervisor cs
    where cs.lecturer.id = :lecturerUserId
      and cs.publicationCase.status = com.example.thesisrepo.publication.CaseStatus.REGISTRATION_PENDING
      and cs.approvedAt is null
      and cs.rejectedAt is null
    """)
  List<CaseSupervisor> findPendingApprovalsForLecturer(@Param("lecturerUserId") Long lecturerUserId);

  @Query("select cs from CaseSupervisor cs where cs.publicationCase.id = :caseId")
  List<CaseSupervisor> findByCaseId(@Param("caseId") Long caseId);
}
