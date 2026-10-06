package com.motorplatforms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ClientsTest extends IntegrationTest {

  private static final Map<String, String> JANE =
      Map.of(
          "name", " Jane Citizen ",
          "carModel", "Toyota Corolla",
          "phone", "0412 345 678",
          "rego", " abc123 ");

  private Cookie admin;

  @BeforeEach
  void login() throws Exception {
    admin = loginAsAdmin();
  }

  @Test
  void createsANormalisedClient() throws Exception {
    mvc.perform(jsonPost("/api/clients", JANE).cookie(admin))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Jane Citizen"))
        .andExpect(jsonPath("$.phone").value("+61412345678"))
        .andExpect(jsonPath("$.rego").value("ABC123"));
  }

  @Test
  void storesOnlyCiphertextForPii() throws Exception {
    mvc.perform(jsonPost("/api/clients", JANE).cookie(admin)).andExpect(status().isCreated());

    var row = jdbc.queryForMap("SELECT name, phone, rego, car_model FROM clients");
    assertThat(row.get("name").toString()).startsWith("v1:").doesNotContain("Jane");
    assertThat(row.get("phone").toString()).startsWith("v1:").doesNotContain("412345678");
    assertThat(row.get("rego").toString()).startsWith("v1:").doesNotContain("ABC123");
    assertThat(row.get("car_model")).isEqualTo("Toyota Corolla");
  }

  @Test
  void rejectsAnInvalidPhone() throws Exception {
    var body = Map.of("name", "A", "carModel", "B", "phone", "12", "rego", "C");

    mvc.perform(jsonPost("/api/clients", body).cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.phone").exists());
  }

  @Test
  void requiresEveryField() throws Exception {
    mvc.perform(jsonPost("/api/clients", Map.of()).cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.name").exists())
        .andExpect(jsonPath("$.error.fields.carModel").exists())
        .andExpect(jsonPath("$.error.fields.phone").exists())
        .andExpect(jsonPath("$.error.fields.rego").exists());
  }

  @Test
  void readsUpdatesAndDeletes() throws Exception {
    String id = createClient(admin);
    var updated =
        Map.of("name", "Jane C", "carModel", "Mazda 3", "phone", "+61400000000", "rego", "xyz9");

    mvc.perform(get("/api/clients/" + id).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Jane Citizen"));
    mvc.perform(
            put("/api/clients/" + id)
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(updated)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rego").value("XYZ9"));
    mvc.perform(get("/api/clients").cookie(admin)).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(delete("/api/clients/" + id).cookie(admin)).andExpect(status().isNoContent());
    mvc.perform(get("/api/clients/" + id).cookie(admin)).andExpect(status().isNotFound());
  }

  @Test
  void requiresAPortalSession() throws Exception {
    mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
  }
}
