package com.motorplatforms.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motorplatforms.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Bad input to the functional routes still gets the standard error shape. */
class RoutesTest extends IntegrationTest {

  @Test
  void malformedJsonIsABadRequest() throws Exception {
    Cookie admin = loginAsAdmin();

    mvc.perform(
            post("/api/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json")
                .cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
  }

  @Test
  void aMalformedIdOrFilterIsABadRequest() throws Exception {
    Cookie admin = loginAsAdmin();

    mvc.perform(get("/api/clients/not-a-uuid").cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    mvc.perform(get("/api/inspections?status=NOPE").cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
  }

  @Test
  void anUnknownRouteIsNotFound() throws Exception {
    Cookie admin = loginAsAdmin();

    mvc.perform(get("/api/nope").cookie(admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
