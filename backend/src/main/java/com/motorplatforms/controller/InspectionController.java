package com.motorplatforms.controller;

import com.motorplatforms.model.InspectionDetail;
import com.motorplatforms.model.InspectionStatus;
import com.motorplatforms.model.InspectionSummary;
import com.motorplatforms.service.InspectionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** Admin endpoints. */
@Component
class InspectionController {

  record CreateInspectionRequest(@NotNull UUID clientId) {}

  // [ASSUMPTION] The admin response is free text.
  record ResponseRequest(@NotBlank @Size(max = 1000) String response) {}

  private final InspectionService inspectionService;
  private final Requests requests;

  InspectionController(InspectionService inspectionService, Requests requests) {
    this.inspectionService = inspectionService;
    this.requests = requests;
  }

  ServerResponse create(ServerRequest request) throws Exception {
    CreateInspectionRequest body = requests.body(request, CreateInspectionRequest.class);
    return ServerResponse.status(HttpStatus.CREATED)
        .body(InspectionSummary.from(inspectionService.create(body.clientId())));
  }

  /** The dashboard polls this. */
  ServerResponse list(ServerRequest request) {
    InspectionStatus status = requests.optionalParam(request, "status", InspectionStatus.class);
    return ServerResponse.ok()
        .body(inspectionService.list(status).stream().map(InspectionSummary::from).toList());
  }

  ServerResponse get(ServerRequest request) {
    return ServerResponse.ok()
        .body(InspectionDetail.from(inspectionService.get(requests.pathId(request, "id"))));
  }

  ServerResponse respond(ServerRequest request) throws Exception {
    UUID id = requests.pathId(request, "id");
    UUID idempotencyKey = requests.idempotencyKey(request);
    ResponseRequest body = requests.body(request, ResponseRequest.class);
    inspectionService.respond(id, idempotencyKey, body.response());
    return ServerResponse.ok().body(InspectionDetail.from(inspectionService.get(id)));
  }
}
