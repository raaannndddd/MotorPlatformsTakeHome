package com.motorplatforms.inspections;

import com.motorplatforms.clients.Client;
import com.motorplatforms.clients.ClientService;
import com.motorplatforms.common.ApiException;
import com.motorplatforms.common.AppProperties;
import com.motorplatforms.infra.crypto.Secrets;
import com.motorplatforms.notifications.SmsMessages;
import com.motorplatforms.notifications.SmsNotifier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-side inspection workflow. */
@Service
public class InspectionService {

  private final InspectionRepository inspections;
  private final ClientService clients;
  private final SmsNotifier sms;
  private final AppProperties props;

  public InspectionService(
      InspectionRepository inspections,
      ClientService clients,
      SmsNotifier sms,
      AppProperties props) {
    this.inspections = inspections;
    this.clients = clients;
    this.sms = sms;
    this.props = props;
  }

  /** Creates the inspection and texts the client a link. Only the token's hash is stored. */
  @Transactional
  public Inspection create(UUID clientId) {
    Client client = clients.get(clientId);
    String token = Secrets.newLinkToken();
    Inspection inspection =
        inspections.save(
            new Inspection(client, Secrets.sha256(token), Instant.now().plus(props.linkTtl())));
    sms.send(client.getId(), SmsMessages.inspectionLink(props.publicBaseUrl() + "/i/" + token));
    return inspection;
  }

  @Transactional(readOnly = true)
  public List<Inspection> list(InspectionStatus status) {
    return status == null
        ? inspections.findAllByOrderByCreatedAtDesc()
        : inspections.findByStatusOrderByCreatedAtDesc(status);
  }

  @Transactional(readOnly = true)
  public Inspection get(UUID id) {
    return inspections
        .findWithClientById(id)
        .orElseThrow(() -> ApiException.notFound("Inspection"));
  }

  /**
   * Records the response and texts it to the customer, exactly once. A retry with the same
   * Idempotency-Key succeeds again without sending a second SMS.
   */
  @Transactional
  public void respond(UUID id, UUID idempotencyKey, String response) {
    Inspection inspection = get(id);
    String text = response.strip();
    if (inspections.markResponded(id, idempotencyKey, text, Instant.now()) == 1) {
      sms.send(inspection.getClient().getId(), SmsMessages.response(text));
      return;
    }
    Inspection current = get(id);
    if (idempotencyKey != null && idempotencyKey.equals(current.getRespondIdempotencyKey())) {
      return;
    }
    throw current.getStatus() == InspectionStatus.RESPONDED
        ? ApiException.conflict("ALREADY_RESPONDED", "A response has already been sent.")
        : ApiException.conflict("NOT_SUBMITTED", "The customer has not submitted the form yet.");
  }
}
