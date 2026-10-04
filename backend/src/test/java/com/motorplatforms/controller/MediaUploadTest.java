package com.motorplatforms.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.motorplatforms.inspections.InspectionTestSupport;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.RequestBuilder;

class MediaUploadTest extends InspectionTestSupport {

  private static final byte[] PHOTO = "fake-jpeg-bytes".getBytes();

  private Cookie customer;
  private String inspectionId;

  @BeforeEach
  void openInspection() throws Exception {
    customer = customerSession(sendLink());
    inspectionId = jdbc.queryForObject("SELECT id::text FROM inspections", String.class);
  }

  @Test
  void requestUploadConfirmAndView() throws Exception {
    JsonNode ticket = ticket("image/jpeg", PHOTO.length);

    mvc.perform(
            put(URI.create(ticket.get("uploadUrl").asText()))
                .contentType("image/jpeg")
                .content(PHOTO))
        .andExpect(status().isOk());
    mvc.perform(confirm(ticket))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sizeBytes").value(PHOTO.length));

    var row = jdbc.queryForMap("SELECT object_key, content_type, size_bytes FROM media");
    assertThat(row).containsEntry("content_type", "image/jpeg");
    assertThat(row.get("object_key").toString()).startsWith(inspectionId);

    String url =
        json.readTree(
                mvc.perform(get("/api/inspections/" + inspectionId + "/media").cookie(staff))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get(0)
            .get("url")
            .asText();
    mvc.perform(get(URI.create(url))).andExpect(status().isOk()).andExpect(content().bytes(PHOTO));
  }

  @Test
  void rejectsADisallowedType() throws Exception {
    mvc.perform(
            jsonPost("/api/public/media", Map.of("contentType", "application/pdf", "sizeBytes", 10))
                .cookie(customer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.contentType").exists());
  }

  @Test
  void rejectsFilesOverTheCap() throws Exception {
    mvc.perform(
            jsonPost(
                    "/api/public/media",
                    Map.of("contentType", "video/mp4", "sizeBytes", 600L * 1024 * 1024 + 1))
                .cookie(customer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.sizeBytes").exists());
  }

  @Test
  void rejectsAnUploadLargerThanDeclared() throws Exception {
    JsonNode ticket = ticket("image/jpeg", 3);

    mvc.perform(
            put(URI.create(ticket.get("uploadUrl").asText()))
                .contentType("image/jpeg")
                .content(PHOTO))
        .andExpect(status().isPayloadTooLarge());
    mvc.perform(confirm(ticket)).andExpect(status().isConflict());
  }

  @Test
  void rejectsATamperedUploadUrl() throws Exception {
    String url = ticket("image/jpeg", PHOTO.length).get("uploadUrl").asText();

    mvc.perform(
            put(URI.create(url.replace("max=", "max=9"))).contentType("image/jpeg").content(PHOTO))
        .andExpect(status().isForbidden());
    mvc.perform(put(URI.create(url)).contentType("image/png").content(PHOTO))
        .andExpect(status().isForbidden());
  }

  @Test
  void confirmBeforeUploadIsAConflict() throws Exception {
    mvc.perform(confirm(ticket("image/png", 10)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("UPLOAD_MISSING"));
  }

  @Test
  void noUploadsAfterSubmission() throws Exception {
    jdbc.update("UPDATE inspections SET status = 'SUBMITTED'");

    mvc.perform(
            jsonPost("/api/public/media", Map.of("contentType", "image/png", "sizeBytes", 10))
                .cookie(customer))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("NOT_EDITABLE"));
  }

  private JsonNode ticket(String type, long size) throws Exception {
    return json.readTree(
        mvc.perform(
                jsonPost("/api/public/media", Map.of("contentType", type, "sizeBytes", size))
                    .cookie(customer))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private RequestBuilder confirm(JsonNode ticket) {
    return post("/api/public/media/" + ticket.get("mediaId").asText() + "/confirm")
        .cookie(customer);
  }
}
