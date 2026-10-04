package com.motorplatforms.infra.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "processed_jobs")
class ProcessedJob {

  @Id private UUID id;

  private String type;

  @CreationTimestamp
  @Column(name = "processed_at")
  private Instant processedAt;

  protected ProcessedJob() {}

  ProcessedJob(UUID id, String type) {
    this.id = id;
    this.type = type;
  }
}
