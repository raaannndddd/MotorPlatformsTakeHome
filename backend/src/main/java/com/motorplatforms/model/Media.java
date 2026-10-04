package com.motorplatforms.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** A photo or video. Only the object key, type and size are stored; the bytes are in storage. */
@Entity
@Table(name = "media")
public class Media {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "inspection_id")
  private UUID inspectionId;

  @Column(name = "object_key")
  private String objectKey;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "size_bytes")
  private long sizeBytes;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  protected Media() {}

  Media(UUID inspectionId, String objectKey, String contentType, long declaredBytes) {
    this.inspectionId = inspectionId;
    this.objectKey = objectKey;
    this.contentType = contentType;
    this.sizeBytes = declaredBytes;
  }

  /** Records that the upload finished, with the size actually stored. */
  void confirm(long storedBytes) {
    this.sizeBytes = storedBytes;
    this.confirmedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }
}
