package com.motorplatforms.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.infra.crypto.Secrets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Link token checks and "opened" tracking. */
class CustomerLinkTest extends InspectionTestSupport {

  @Test
  void theSessionCallMarksTheInspectionOpened() throws Exception {
    String token = sendLink();

    mvc.perform(jsonPost("/api/public/session", Map.of("token", token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPENED"));
    mvc.perform(jsonPost("/api/public/session", Map.of("token", token))).andExpect(status().isOk());

    assertThat(statusInDb()).isEqualTo("OPENED");
    assertThat(jdbc.queryForObject("SELECT opened_at FROM inspections", Instant.class)).isNotNull();
  }

  @Test
  void aRawGetOfTheLinkDoesNotMarkItOpened() throws Exception {
    String token = sendLink();

    mvc.perform(get("/i/" + token))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/inspection.html"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"));

    assertThat(statusInDb()).isEqualTo("SENT");
  }

  @Test
  void unknownExpiredAndSubmittedLinksGetTheSameError() throws Exception {
    String token = sendLink();
    String unknown = mvcError(Secrets.newLinkToken());

    jdbc.update("UPDATE inspections SET token_expires_at = now() - interval '1 second'");
    String expired = mvcError(token);

    jdbc.update(
        "UPDATE inspections SET token_expires_at = now() + interval '1 day', status = 'SUBMITTED'");
    String submitted = mvcError(token);

    assertThat(unknown).contains("LINK_INVALID").isEqualTo(expired).isEqualTo(submitted);
  }

  @Test
  void theOtpRequestAlsoRejectsADeadLink() throws Exception {
    mvc.perform(jsonPost("/api/public/otp", Map.of("token", Secrets.newLinkToken())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("LINK_INVALID"));
  }

  @Test
  void customerEndpointsNeedTheCustomerSession() throws Exception {
    mvc.perform(get("/api/public/inspection")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/public/inspection").cookie(staff)).andExpect(status().isForbidden());
  }

  @Test
  void aCustomerSessionCannotReachStaffEndpoints() throws Exception {
    var customer = customerSession(sendLink());

    mvc.perform(get("/api/clients").cookie(customer)).andExpect(status().isForbidden());
  }

  private String mvcError(String token) throws Exception {
    return mvc.perform(jsonPost("/api/public/session", Map.of("token", token)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
