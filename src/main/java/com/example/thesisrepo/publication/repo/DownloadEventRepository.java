package com.example.thesisrepo.publication.repo;

import com.example.thesisrepo.publication.DownloadEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadEventRepository extends JpaRepository<DownloadEvent, Long> {
}
