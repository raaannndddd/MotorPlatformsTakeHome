package com.motorplatforms.inspections;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff endpoints. */
@RestController
@RequestMapping("/api/inspections")
class InspectionController {

  record CreateInspectionRequest(@NotNull UUID clientId) {}

  // [ASSUMPTION] The staff response is free text.
  record ResponseRequest(@NotBlank @Size(max = 1000) String response) {}

  private final InspectionService inspectionService;

  InspectionController(InspectionService inspectionService) {
    this.inspectionService = inspectionService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  InspectionSummary create(@Valid @RequestBody CreateInspectionRequest body) {
    return InspectionSummary.from(inspectionService.create(body.clientId()));
  }

  /** The dashboard polls this. */
  @GetMapping
  List<InspectionSummary> list(@RequestParam(required = false) InspectionStatus status) {
    return inspectionService.list(status).stream().map(InspectionSummary::from).toList();
  }

  @GetMapping("/{id}")
  InspectionDetail get(@PathVariable UUID id) {
    return InspectionDetail.from(inspectionService.get(id));
  }

  @PostMapping("/{id}/response")
  InspectionDetail respond(
      @PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
      @Valid @RequestBody ResponseRequest body) {
    inspectionService.respond(id, idempotencyKey, body.response());
    return InspectionDetail.from(inspectionService.get(id));
  }
}
