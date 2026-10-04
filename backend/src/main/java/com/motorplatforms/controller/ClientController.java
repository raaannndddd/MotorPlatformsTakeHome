package com.motorplatforms.controller;

import com.motorplatforms.model.ClientRequest;
import com.motorplatforms.model.ClientResponse;
import com.motorplatforms.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** PII is decrypted server-side for authenticated admins only. */
@Component
class ClientController {

  private final ClientService clientService;
  private final Requests requests;

  ClientController(ClientService clientService, Requests requests) {
    this.clientService = clientService;
    this.requests = requests;
  }

  ServerResponse list(ServerRequest request) {
    return ServerResponse.ok()
        .body(clientService.list().stream().map(ClientResponse::from).toList());
  }

  ServerResponse get(ServerRequest request) {
    return ServerResponse.ok()
        .body(ClientResponse.from(clientService.get(requests.pathId(request, "id"))));
  }

  ServerResponse create(ServerRequest request) throws Exception {
    ClientRequest body = requests.body(request, ClientRequest.class);
    return ServerResponse.status(HttpStatus.CREATED)
        .body(ClientResponse.from(clientService.create(body)));
  }

  ServerResponse update(ServerRequest request) throws Exception {
    var id = requests.pathId(request, "id");
    ClientRequest body = requests.body(request, ClientRequest.class);
    return ServerResponse.ok().body(ClientResponse.from(clientService.update(id, body)));
  }

  ServerResponse delete(ServerRequest request) {
    clientService.delete(requests.pathId(request, "id"));
    return ServerResponse.noContent().build();
  }
}
