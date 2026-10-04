package com.motorplatforms.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.IntegrationTest;
import com.motorplatforms.infra.crypto.Secrets;
import com.motorplatforms.users.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SendInspectionTest extends IntegrationTest {

  @Test
  void textsTheClientALinkAndStoresOnlyTheTokenHash() throws Exception {
    var staff = loginAs(Role.STAFF);
    String clientId = createClient(staff);

    mvc.perform(jsonPost("/api/inspections", Map.of("clientId", clientId)).cookie(staff))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.clientName").value("Jane Citizen"));

    var sms = this.sms.awaitCount(1);
    assertThat(sms.to()).isEqualTo("+61412345678");
    assertThat(sms.body())
        .startsWith(
            "Hi, this is MotorPlatforms. Please take 5 minutes to fill out this car inspection"
                + " form: http://localhost/i/");
    String token = sms.body().substring(sms.body().lastIndexOf('/') + 1);
    assertThat(token).matches("[A-Za-z0-9_-]{43}"); // 32 bytes, base64url
    String storedHash = jdbc.queryForObject("SELECT token_hash FROM inspections", String.class);
    assertThat(storedHash).isEqualTo(Secrets.sha256(token)).doesNotContain(token);
  }

  @Test
  void rejectsAnUnknownClient() throws Exception {
    var staff = loginAs(Role.STAFF);

    mvc.perform(
            jsonPost("/api/inspections", Map.of("clientId", UUID.randomUUID().toString()))
                .cookie(staff))
        .andExpect(status().isNotFound());
    assertThat(sms.sent()).isEmpty();
  }
}
