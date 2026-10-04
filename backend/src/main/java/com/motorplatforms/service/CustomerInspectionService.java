package com.motorplatforms.inspections;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.common.AppProperties;
import com.motorplatforms.infra.crypto.Secrets;
import com.motorplatforms.notifications.SmsMessages;
import com.motorplatforms.notifications.SmsNotifier;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer-side workflow: open the link, prove phone ownership with an OTP. */
@Service
public class CustomerInspectionService {

  private final InspectionRepository inspections;
  private final SmsNotifier sms;
  private final AppProperties props;

  public CustomerInspectionService(
      InspectionRepository inspections, SmsNotifier sms, AppProperties props) {
    this.inspections = inspections;
    this.sms = sms;
    this.props = props;
  }

  /**
   * Called by the page's JavaScript, not by the raw GET of the link, so SMS link previews do not
   * count as "opened". Opening twice is harmless.
   */
  @Transactional
  public InspectionStatus open(String token) {
    Inspection inspection = liveInspection(token);
    inspections.markOpened(inspection.getId(), Instant.now());
    return InspectionStatus.OPENED;
  }

  @Transactional
  public void requestOtp(String token) {
    Inspection inspection = liveInspection(token);
    String code = Secrets.newOtp();
    inspections.storeOtp(
        inspection.getId(), otpHash(inspection.getId(), code), Instant.now().plus(props.otpTtl()));
    sms.send(inspection.getClient().getId(), SmsMessages.otp(code));
  }

  /**
   * Returns the inspection id to scope the customer session to. A failed check must still count as
   * an attempt, so ApiException does not roll the transaction back.
   */
  @Transactional(noRollbackFor = ApiException.class)
  public UUID verifyOtp(String token, String code) {
    Inspection inspection = liveInspection(token);
    UUID id = inspection.getId();
    if (inspections.useOtpAttempt(id, Instant.now(), props.otpMaxAttempts()) == 0) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "This code has expired. Request a new one.");
    }
    String hash = otpHash(id, code);
    if (!Secrets.hashesMatch(hash, inspection.getOtpHash())
        || inspections.consumeOtp(id, hash) == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "That code is incorrect.");
    }
    return id;
  }

  /**
   * Submits the form exactly once. A retry with the same Idempotency-Key succeeds again without
   * changing anything; any other second submit is a conflict.
   */
  @Transactional
  public void submit(UUID inspectionId, UUID idempotencyKey, SubmissionRequest form) {
    int changed =
        inspections.markSubmitted(
            inspectionId,
            idempotencyKey,
            form.mileage(),
            form.conditionNotes().strip(),
            Instant.now());
    if (changed == 1) {
      return;
    }
    Inspection current = get(inspectionId);
    boolean replay =
        idempotencyKey != null && idempotencyKey.equals(current.getSubmitIdempotencyKey());
    if (!replay) {
      throw ApiException.conflict("ALREADY_SUBMITTED", "This inspection was already submitted.");
    }
  }

  @Transactional(readOnly = true)
  public Inspection get(UUID inspectionId) {
    return inspections
        .findById(inspectionId)
        .orElseThrow(() -> ApiException.notFound("Inspection"));
  }

  /** Unknown, expired and already-submitted links all get the same answer. */
  private Inspection liveInspection(String token) {
    return inspections
        .findByTokenHash(Secrets.sha256(token))
        .filter(i -> i.linkIsLive(Instant.now()))
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "LINK_INVALID", "This link is invalid or has expired."));
  }

  // Salted with the inspection id so equal codes on different inspections hash differently.
  private static String otpHash(UUID inspectionId, String code) {
    return Secrets.sha256(inspectionId + ":" + code);
  }
}
