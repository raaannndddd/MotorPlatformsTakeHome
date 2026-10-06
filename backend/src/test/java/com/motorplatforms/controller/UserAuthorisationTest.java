package com.motorplatforms.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UserAuthorisationTest extends InspectionTestSupport {

  private static final Map<String, String> NEW_USER =
      Map.of("email", "new@example.com", "password", "another-long-password");

  @Test
  void aCustomerSessionCannotCreateOrListUsers() throws Exception {
    var customer = customerSession(sendLink());

    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(customer))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    mvc.perform(get("/api/users").cookie(customer)).andExpect(status().isForbidden());
  }

  @Test
  void anonymousRequestsAreRejected() throws Exception {
    mvc.perform(jsonPost("/api/users", NEW_USER)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void adminCanCreateAndListUsers() throws Exception {
    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(admin))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new@example.com"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    mvc.perform(get("/api/users").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void aCreatedUserIsAnAdmin() throws Exception {
    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(admin)).andExpect(status().isCreated());
    var created =
        mvc.perform(jsonPost("/api/auth/login", NEW_USER))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookies()[0];

    mvc.perform(get("/api/users").cookie(created)).andExpect(status().isOk());
  }

  @Test
  void duplicateEmailIsAConflict() throws Exception {
    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(admin)).andExpect(status().isCreated());

    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
  }

  @Test
  void invalidInputReturnsFieldErrors() throws Exception {
    mvc.perform(jsonPost("/api/users", Map.of("email", "nope", "password", "short")).cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.email").exists())
        .andExpect(jsonPath("$.error.fields.password").exists());
  }

  @Test
  void wrongPasswordIsRejected() throws Exception {
    mvc.perform(
            jsonPost(
                "/api/auth/login", Map.of("email", "admin@example.com", "password", "wrong-one")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }
}
