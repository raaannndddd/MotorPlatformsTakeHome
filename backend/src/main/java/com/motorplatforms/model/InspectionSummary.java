package com.motorplatforms.inspections;

import java.time.Instant;
import java.util.UUID;

/** One row on the staff dashboard. */
public record InspectionSummary(
    UUID id,
    InspectionStatus status,
    UUID clientId,
    String clientName,
    String carModel,
    String rego,
    Instant createdAt,
    Instant openedAt,
    Instant submittedAt,
    Instant respondedAt) {

  static InspectionSummary from(Inspection i) {
    var c = i.getClient();
    return new InspectionSummary(
        i.getId(),
        i.getStatus(),
        c.getId(),
        c.getName(),
        c.getCarModel(),
        c.getRego(),
        i.getCreatedAt(),
        i.getOpenedAt(),
        i.getSubmittedAt(),
        i.getRespondedAt());
  }
}
