package com.motorplatforms.clients;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** PII is decrypted server-side for authenticated staff and admins only. */
@RestController
@RequestMapping("/api/clients")
class ClientController {

  private final ClientService clientService;

  ClientController(ClientService clientService) {
    this.clientService = clientService;
  }

  @GetMapping
  List<ClientResponse> list() {
    return clientService.list().stream().map(ClientResponse::from).toList();
  }

  @GetMapping("/{id}")
  ClientResponse get(@PathVariable UUID id) {
    return ClientResponse.from(clientService.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ClientResponse create(@Valid @RequestBody ClientRequest body) {
    return ClientResponse.from(clientService.create(body));
  }

  @PutMapping("/{id}")
  ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest body) {
    return ClientResponse.from(clientService.update(id, body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    clientService.delete(id);
  }
}
