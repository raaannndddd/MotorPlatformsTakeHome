package com.motorplatforms.infra.queue;

import java.util.Map;
import java.util.UUID;

/** A unit of background work. The id lets consumers skip a job they have already processed. */
public record Job(UUID id, String type, Map<String, String> payload) {

  public static Job of(String type, Map<String, String> payload) {
    return new Job(UUID.randomUUID(), type, Map.copyOf(payload));
  }
}
