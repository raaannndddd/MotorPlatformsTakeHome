package com.motorplatforms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorplatforms.auth.SessionCookies;
import com.motorplatforms.users.Role;
import com.motorplatforms.users.UserService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Boots the whole app against the test database and wipes it before each test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

  protected static final String PASSWORD = "correct-horse-battery";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected UserService userService;
  @Autowired protected RecordingSmsSender sms;

  @TestConfiguration
  static class TestBeans {
    @Bean
    @Primary
    RecordingSmsSender recordingSmsSender() {
      return new RecordingSmsSender();
    }
  }

  @BeforeEach
  void resetState() {
    jdbc.execute("TRUNCATE users, clients, inspections, media, processed_jobs CASCADE");
    sms.clear();
  }

  protected Cookie loginAs(Role role) throws Exception {
    String email = role.name().toLowerCase() + "@example.com";
    userService.create(email, PASSWORD, role);
    var result =
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", email, "password", PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    return result.getResponse().getCookie(SessionCookies.STAFF);
  }

  protected String createClient(Cookie session) throws Exception {
    var body =
        Map.of(
            "name", "Jane Citizen",
            "carModel", "Toyota Corolla",
            "phone", "0412 345 678",
            "rego", "ABC123");
    return idOf(mvc.perform(jsonPost("/api/clients", body).cookie(session)).andReturn());
  }

  protected String idOf(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  protected MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
    return post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  }
}
