package com.motorplatforms.model;

import com.motorplatforms.repository.InspectionRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Status changes are made with conditional updates in {@link InspectionRepository}, never by
 * setting fields here, so concurrent requests cannot both win.
 */
@Entity
@Table(name = "inspections")
public class Inspection {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id")
  private Client client;

  @Enumerated(EnumType.STRING)
  private InspectionStatus status = InspectionStatus.SENT;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "token_expires_at")
  private Instant tokenExpiresAt;

  @Column(name = "otp_hash")
  private String otpHash;

  @Column(name = "otp_expires_at")
  private Instant otpExpiresAt;

  @Column(name = "otp_attempts")
  private int otpAttempts;

  private Integer mileage;

  @Column(name = "condition_notes")
  private String conditionNotes;

  @Column(name = "submit_idempotency_key")
  private UUID submitIdempotencyKey;

  private String response;

  @Column(name = "respond_idempotency_key")
  private UUID respondIdempotencyKey;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "opened_at")
  private Instant openedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "responded_at")
  private Instant respondedAt;

  protected Inspection() {}

  public Inspection(Client client, String tokenHash, Instant tokenExpiresAt) {
    this.client = client;
    this.tokenHash = tokenHash;
    this.tokenExpiresAt = tokenExpiresAt;
  }

  /** The link works until it expires or the form is submitted. */
  public boolean linkIsLive(Instant now) {
    return now.isBefore(tokenExpiresAt)
        && (status == InspectionStatus.SENT || status == InspectionStatus.OPENED);
  }

  public UUID getId() {
    return id;
  }

  public Client getClient() {
    return client;
  }

  public InspectionStatus getStatus() {
    return status;
  }

  public String getOtpHash() {
    return otpHash;
  }

  public Integer getMileage() {
    return mileage;
  }

  public String getConditionNotes() {
    return conditionNotes;
  }

  public UUID getSubmitIdempotencyKey() {
    return submitIdempotencyKey;
  }

  public String getResponse() {
    return response;
  }

  public UUID getRespondIdempotencyKey() {
    return respondIdempotencyKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }
}
