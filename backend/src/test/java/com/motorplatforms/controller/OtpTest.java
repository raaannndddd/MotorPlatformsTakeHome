package com.motorplatforms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.security.SessionCookies;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class OtpTest extends InspectionTestSupport {

  private String token;

  @BeforeEach
  void openLink() throws Exception {
    token = sendLink();
    mvc.perform(jsonPost("/api/public/session", Map.of("token", token)));
  }

  @Test
  void sendsASixDigitCodeAndStoresOnlyItsHash() throws Exception {
    String code = requestOtp(token);

    assertThat(code).matches("\\d{6}");
    assertThat(sms.sent().getLast().to()).isEqualTo("+61412345678");
    String stored = jdbc.queryForObject("SELECT otp_hash FROM inspections", String.class);
    assertThat(stored).hasSize(64).doesNotContain(code);
  }

  @Test
  void theRightCodeIssuesAnHttpOnlySessionScopedToTheInspection() throws Exception {
    String code = requestOtp(token);

    var cookie =
        verify(code)
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getCookie(SessionCookies.CUSTOMER);

    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/api/public");
    mvc.perform(get("/api/public/inspection").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPENED"));
  }

  @Test
  void aWrongCodeIsRejected() throws Exception {
    String code = requestOtp(token);

    verify(otherThan(code))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
  }

  @Test
  void aCodeWorksOnlyOnce() throws Exception {
    String code = requestOtp(token);

    verify(code).andExpect(status().isNoContent());
    verify(code).andExpect(status().isBadRequest());
  }

  @Test
  void locksAfterFiveAttemptsEvenWithTheRightCode() throws Exception {
    String code = requestOtp(token);
    for (int i = 0; i < 5; i++) {
      verify(otherThan(code)).andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    verify(code)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));
  }

  @Test
  void anExpiredCodeIsRejected() throws Exception {
    String code = requestOtp(token);
    jdbc.update("UPDATE inspections SET otp_expires_at = now() - interval '1 second'");

    verify(code).andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));
  }

  @Test
  void aNewCodeReplacesTheOldOne() throws Exception {
    String first = requestOtp(token);
    String second = requestOtp(token);

    if (!first.equals(second)) {
      verify(first).andExpect(status().isBadRequest());
    }
    verify(second).andExpect(status().isNoContent());
  }

  @Test
  void rejectsAMalformedCode() throws Exception {
    verify("12ab")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.code").exists());
  }

  private ResultActions verify(String code) throws Exception {
    return mvc.perform(jsonPost("/api/public/otp/verify", Map.of("token", token, "code", code)));
  }

  private static String otherThan(String code) {
    return code.equals("000000") ? "111111" : "000000";
  }
}
