package com.motorplatforms.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserAuthorisationTest extends IntegrationTest {

  private static final Map<String, String> NEW_USER =
      Map.of("email", "new@example.com", "password", "another-long-password", "role", "STAFF");

  @Test
  void staffCannotCreateUsers() throws Exception {
    var staff = loginAs(Role.STAFF);

    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(staff))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void staffCannotListUsers() throws Exception {
    var staff = loginAs(Role.STAFF);

    mvc.perform(get("/api/users").cookie(staff)).andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateAndListUsers() throws Exception {
    var admin = loginAs(Role.ADMIN);

    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(admin))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new@example.com"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    mvc.perform(get("/api/users").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void adminCanCreateAnotherAdmin() throws Exception {
    var admin = loginAs(Role.ADMIN);

    mvc.perform(
            jsonPost(
                    "/api/users",
                    Map.of("email", "b@example.com", "password", PASSWORD, "role", "ADMIN"))
                .cookie(admin))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void duplicateEmailIsAConflict() throws Exception {
    var admin = loginAs(Role.ADMIN);
    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(admin)).andExpect(status().isCreated());

    mvc.perform(jsonPost("/api/users", NEW_USER).cookie(admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
  }

  @Test
  void invalidInputReturnsFieldErrors() throws Exception {
    var admin = loginAs(Role.ADMIN);

    mvc.perform(
            jsonPost("/api/users", Map.of("email", "nope", "password", "short", "role", "STAFF"))
                .cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.email").exists())
        .andExpect(jsonPath("$.error.fields.password").exists());
  }

  @Test
  void anonymousRequestsAreRejected() throws Exception {
    mvc.perform(get("/api/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void wrongPasswordIsRejected() throws Exception {
    userService.create("x@example.com", PASSWORD, Role.STAFF);

    mvc.perform(
            jsonPost("/api/auth/login", Map.of("email", "x@example.com", "password", "wrong-one")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }
}
