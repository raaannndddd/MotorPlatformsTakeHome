package com.motorplatforms.model;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
    UUID id, String name, String carModel, String phone, String rego, Instant createdAt) {

  public static ClientResponse from(Client c) {
    return new ClientResponse(
        c.getId(), c.getName(), c.getCarModel(), c.getPhone(), c.getRego(), c.getCreatedAt());
  }
}
