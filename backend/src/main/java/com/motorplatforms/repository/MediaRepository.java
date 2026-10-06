package com.motorplatforms.repository;

import com.motorplatforms.model.Media;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, UUID> {

  Optional<Media> findByIdAndInspectionId(UUID id, UUID inspectionId);

  List<Media> findByInspectionIdAndConfirmedAtIsNotNullOrderByCreatedAt(UUID inspectionId);
}
