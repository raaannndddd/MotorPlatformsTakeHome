package com.motorplatforms.repository;

import com.motorplatforms.model.Client;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

  // Encrypted columns cannot be sorted in SQL, so order by creation time.
  List<Client> findAllByOrderByCreatedAtDesc();
}
