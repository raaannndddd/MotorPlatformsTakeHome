package com.motorplatforms.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class StaffResponseTest extends InspectionTestSupport {

  private static final String RESPONSE = "Thanks. We can fix the scratch for $150.";

  @Test
  void respondsAndTextsTheCustomer() throws Exception {
    String id = submittedInspection();
    int smsBefore = sms.sent().size();

    respond(id, UUID.randomUUID(), RESPONSE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inspection.status").value("RESPONDED"))
        .andExpect(jsonPath("$.response").value(RESPONSE));

    var sent = sms.awaitCount(smsBefore + 1);
    assertThat(sent.to()).isEqualTo("+61412345678");
    assertThat(sent.body()).isEqualTo(SmsMessagesFixture.response(RESPONSE));
  }

  @Test
  void aRetryWithTheSameKeyDoesNotTextTwice() throws Exception {
    String id = submittedInspection();
    int smsBefore = sms.sent().size();
    UUID key = UUID.randomUUID();

    respond(id, key, RESPONSE).andExpect(status().isOk());
    respond(id, key, RESPONSE).andExpect(status().isOk());

    sms.awaitCount(smsBefore + 1);
    Awaitility.await()
        .during(Duration.ofMillis(300))
        .until(() -> sms.sent().size() == smsBefore + 1);
  }

  @Test
  void aSecondResponseIsAConflict() throws Exception {
    String id = submittedInspection();
    respond(id, UUID.randomUUID(), RESPONSE).andExpect(status().isOk());

    respond(id, UUID.randomUUID(), "Again")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ALREADY_RESPONDED"));
  }

  @Test
  void cannotRespondBeforeTheCustomerSubmits() throws Exception {
    sendLink();
    String id = jdbc.queryForObject("SELECT id::text FROM inspections", String.class);

    respond(id, UUID.randomUUID(), RESPONSE)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("NOT_SUBMITTED"));
  }

  @Test
  void requiresAResponse() throws Exception {
    String id = submittedInspection();

    respond(id, UUID.randomUUID(), " ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.response").exists());
  }

  private ResultActions respond(String id, UUID key, String text) throws Exception {
    return mvc.perform(
        jsonPost("/api/inspections/" + id + "/response", Map.of("response", text))
            .header("Idempotency-Key", key.toString())
            .cookie(staff));
  }

  /** Keeps the expected text next to the test without reaching into production constants. */
  private static final class SmsMessagesFixture {
    static String response(String text) {
      return "Hi, this is MotorPlatforms. We have reviewed your car inspection: " + text;
    }
  }
}
