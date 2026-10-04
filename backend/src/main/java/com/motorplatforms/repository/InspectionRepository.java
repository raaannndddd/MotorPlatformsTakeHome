package com.motorplatforms.inspections;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every state change is a single conditional UPDATE that returns the number of rows changed. Zero
 * means another request got there first, so no locks are needed.
 */
public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  Optional<Inspection> findByTokenHash(String tokenHash);

  // Dashboard queries load each inspection's client in the same SQL query (no N+1).

  @EntityGraph(attributePaths = "client")
  List<Inspection> findAllByOrderByCreatedAtDesc();

  @EntityGraph(attributePaths = "client")
  List<Inspection> findByStatusOrderByCreatedAtDesc(InspectionStatus status);

  @EntityGraph(attributePaths = "client")
  Optional<Inspection> findWithClientById(UUID id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Inspection i set i.status = InspectionStatus.OPENED, i.openedAt = :now
      where i.id = :id and i.status = InspectionStatus.SENT
      """)
  int markOpened(@Param("id") UUID id, @Param("now") Instant now);

  /** Replaces any previous code and resets the attempt counter. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Inspection i set i.otpHash = :hash, i.otpExpiresAt = :expiresAt, i.otpAttempts = 0
      where i.id = :id
      """)
  int storeOtp(
      @Param("id") UUID id, @Param("hash") String hash, @Param("expiresAt") Instant expiresAt);

  /** Uses up one attempt. Returns 0 if there is no live code or the attempts are used up. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Inspection i set i.otpAttempts = i.otpAttempts + 1
      where i.id = :id and i.otpHash is not null and i.otpExpiresAt > :now
        and i.otpAttempts < :maxAttempts
      """)
  int useOtpAttempt(
      @Param("id") UUID id, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

  /** Makes a code single-use: only one request can clear the hash it checked. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Inspection i set i.otpHash = null, i.otpExpiresAt = null
      where i.id = :id and i.otpHash = :hash
      """)
  int consumeOtp(@Param("id") UUID id, @Param("hash") String hash);

  /** Exactly one submission wins; the loser sees 0 rows changed. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Inspection i set i.status = InspectionStatus.SUBMITTED, i.submittedAt = :now,
        i.mileage = :mileage, i.conditionNotes = :notes, i.submitIdempotencyKey = :key
      where i.id = :id and i.status = InspectionStatus.OPENED
      """)
  int markSubmitted(
      @Param("id") UUID id,
      @Param("key") UUID idempotencyKey,
      @Param("mileage") int mileage,
      @Param("notes") String conditionNotes,
      @Param("now") Instant now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Inspection i set i.status = InspectionStatus.RESPONDED, i.respondedAt = :now,
        i.response = :response, i.respondIdempotencyKey = :key
      where i.id = :id and i.status = InspectionStatus.SUBMITTED
      """)
  int markResponded(
      @Param("id") UUID id,
      @Param("key") UUID idempotencyKey,
      @Param("response") String response,
      @Param("now") Instant now);
}
