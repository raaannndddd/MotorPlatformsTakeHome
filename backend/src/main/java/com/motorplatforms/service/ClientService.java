package com.motorplatforms.service;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.model.Client;
import com.motorplatforms.model.ClientRequest;
import com.motorplatforms.repository.ClientRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

  private final ClientRepository clients;

  public ClientService(ClientRepository clients) {
    this.clients = clients;
  }

  @Transactional(readOnly = true)
  public List<Client> list() {
    return clients.findAllByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public Client get(UUID id) {
    return clients.findById(id).orElseThrow(() -> ApiException.notFound("Client"));
  }

  @Transactional
  public Client create(ClientRequest request) {
    ClientRequest r = normalise(request);
    return clients.save(new Client(r.name(), r.carModel(), r.phone(), r.rego()));
  }

  @Transactional
  public Client update(UUID id, ClientRequest request) {
    ClientRequest r = normalise(request);
    Client client = get(id);
    client.update(r.name(), r.carModel(), r.phone(), r.rego());
    return client;
  }

  @Transactional
  public void delete(UUID id) {
    clients.delete(get(id));
    try {
      clients.flush();
    } catch (DataIntegrityViolationException e) {
      throw ApiException.conflict(
          "CLIENT_HAS_INSPECTIONS", "A client with inspections cannot be deleted.");
    }
  }

  /** Trims everything, uppercases the rego and converts the phone to E.164. */
  private static ClientRequest normalise(ClientRequest r) {
    return new ClientRequest(
        r.name().trim(),
        r.carModel().trim(),
        PhoneNumbers.toE164(r.phone()),
        r.rego().trim().toUpperCase(Locale.ROOT));
  }
}
