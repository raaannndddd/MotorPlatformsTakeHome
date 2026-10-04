package com.motorplatforms.inspections;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.IntegrationTest;
import com.motorplatforms.auth.SessionCookies;
import com.motorplatforms.users.Role;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

/** Drives the inspection flow through the API, the way the two UIs do. */
public abstract class InspectionTestSupport extends IntegrationTest {

  protected Cookie staff;
  protected String clientId;

  @BeforeEach
  void staffAndClient() throws Exception {
    staff = loginAs(Role.STAFF);
    clientId = createClient(staff);
  }

  /** Staff sends a link; returns the token from the SMS the client received. */
  protected String sendLink() throws Exception {
    int before = sms.sent().size();
    mvc.perform(jsonPost("/api/inspections", Map.of("clientId", clientId)).cookie(staff))
        .andExpect(status().isCreated());
    String body = sms.awaitCount(before + 1).body();
    return body.substring(body.lastIndexOf('/') + 1);
  }

  /** Requests an OTP and returns the code from the SMS. */
  protected String requestOtp(String token) throws Exception {
    int before = sms.sent().size();
    mvc.perform(jsonPost("/api/public/otp", Map.of("token", token)))
        .andExpect(status().isAccepted());
    return sms.awaitCount(before + 1).body().replaceAll("\\D", "").substring(0, 6);
  }

  /** Opens the link and passes the OTP; returns the customer session cookie. */
  protected Cookie customerSession(String token) throws Exception {
    mvc.perform(jsonPost("/api/public/session", Map.of("token", token))).andExpect(status().isOk());
    String code = requestOtp(token);
    return mvc.perform(jsonPost("/api/public/otp/verify", Map.of("token", token, "code", code)))
        .andExpect(status().isNoContent())
        .andReturn()
        .getResponse()
        .getCookie(SessionCookies.CUSTOMER);
  }

  /** Runs the whole customer flow; returns the submitted inspection's id. */
  protected String submittedInspection() throws Exception {
    var customer = customerSession(sendLink());
    mvc.perform(
            jsonPost("/api/public/submit", Map.of("mileage", 84_000, "conditionNotes", "Scratch"))
                .cookie(customer))
        .andExpect(status().isOk());
    return jdbc.queryForObject("SELECT id::text FROM inspections", String.class);
  }

  protected String statusInDb() {
    return jdbc.queryForObject("SELECT status FROM inspections", String.class);
  }
}
