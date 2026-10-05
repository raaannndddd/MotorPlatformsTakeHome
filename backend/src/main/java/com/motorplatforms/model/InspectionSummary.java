package com.motorplatforms.model;

import java.time.Instant;
import java.util.UUID;

/** One row on the admin dashboard. */
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

  public static InspectionSummary from(Inspection i) {
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
