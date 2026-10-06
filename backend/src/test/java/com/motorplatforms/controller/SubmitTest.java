package com.motorplatforms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.model.SubmissionRequest;
import com.motorplatforms.service.CustomerInspectionService;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class SubmitTest extends InspectionTestSupport {

  private static final Map<String, Object> FORM =
      Map.of("mileage", 84_000, "conditionNotes", "Small dent on the rear bumper.");

  @Autowired CustomerInspectionService service;

  private Cookie customer;
  private UUID inspectionId;

  @BeforeEach
  void openAndVerify() throws Exception {
    customer = customerSession(sendLink());
    inspectionId = jdbc.queryForObject("SELECT id FROM inspections", UUID.class);
  }

  @Test
  void submitsAndMapsTheFormOntoTheInspection() throws Exception {
    submit(UUID.randomUUID())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"));

    var row = jdbc.queryForMap("SELECT status, mileage, condition_notes FROM inspections");
    assertThat(row)
        .containsEntry("status", "SUBMITTED")
        .containsEntry("mileage", 84_000)
        .containsEntry("condition_notes", "Small dent on the rear bumper.");
  }

  @Test
  void submitsExactlyOnceUnderConcurrency() throws Exception {
    int requests = 10;
    var start = new CountDownLatch(1);
    var form = new SubmissionRequest(84_000, "Concurrent");
    var futures = new ArrayList<Future<Boolean>>();
    try (var pool = Executors.newFixedThreadPool(requests)) {
      for (int i = 0; i < requests; i++) {
        Callable<Boolean> attempt =
            () -> {
              start.await();
              service.submit(inspectionId, UUID.randomUUID(), form);
              return true;
            };
        futures.add(pool.submit(attempt));
      }
      start.countDown();
    }

    int succeeded = 0;
    for (var future : futures) {
      try {
        future.get();
        succeeded++;
      } catch (ExecutionException e) {
        assertThat(e.getCause())
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("already submitted");
      }
    }
    assertThat(succeeded).isEqualTo(1);
    assertThat(statusInDb()).isEqualTo("SUBMITTED");
  }

  @Test
  void aRetryWithTheSameIdempotencyKeySucceedsAgain() throws Exception {
    UUID key = UUID.randomUUID();

    submit(key).andExpect(status().isOk());
    submit(key).andExpect(status().isOk());

    assertThat(statusInDb()).isEqualTo("SUBMITTED");
  }

  @Test
  void aSecondSubmitWithADifferentKeyIsAConflict() throws Exception {
    submit(UUID.randomUUID()).andExpect(status().isOk());

    submit(UUID.randomUUID())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ALREADY_SUBMITTED"));
  }

  @Test
  void rejectsInvalidInputWithFieldErrors() throws Exception {
    mvc.perform(
            jsonPost("/api/public/submit", Map.of("mileage", -5, "conditionNotes", " "))
                .cookie(customer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.mileage").exists())
        .andExpect(jsonPath("$.error.fields.conditionNotes").exists());
    assertThat(statusInDb()).isEqualTo("OPENED");
  }

  @Test
  void rejectsAMalformedIdempotencyKey() throws Exception {
    mvc.perform(
            jsonPost("/api/public/submit", FORM)
                .header("Idempotency-Key", "not-a-uuid")
                .cookie(customer))
        .andExpect(status().isBadRequest());
  }

  @Test
  void requiresTheCustomerSession() throws Exception {
    mvc.perform(jsonPost("/api/public/submit", FORM)).andExpect(status().isUnauthorized());
  }

  private ResultActions submit(UUID key) throws Exception {
    return mvc.perform(
        jsonPost("/api/public/submit", FORM)
            .header("Idempotency-Key", key.toString())
            .cookie(customer));
  }
}
