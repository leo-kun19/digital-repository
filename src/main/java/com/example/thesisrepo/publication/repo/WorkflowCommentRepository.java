package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.PublicationCase;
import com.example.thesisrepo.publication.WorkflowComment;
import com.example.thesisrepo.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowCommentRepository extends JpaRepository<WorkflowComment, Long> {
  List<WorkflowComment> findByPublicationCaseOrderByCreatedAtAsc(PublicationCase publicationCase);
  Optional<WorkflowComment> findTopByPublicationCaseAndAuthorRoleOrderByCreatedAtDesc(PublicationCase publicationCase, Role authorRole);
}
