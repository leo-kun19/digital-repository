package com.example.thesisrepo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StaffRegistryRepository extends JpaRepository<StaffRegistry, Long> {
    Optional<StaffRegistry> findByEmailIgnoreCase(String email);
}
