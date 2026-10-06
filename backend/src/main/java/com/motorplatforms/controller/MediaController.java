package com.motorplatforms.controller;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.model.CustomerPrincipal;
import com.motorplatforms.repository.InspectionRepository;
import com.motorplatforms.service.MediaService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class MediaController {

  record UploadRequest(@NotBlank String contentType, @Positive long sizeBytes) {}

  private final MediaService mediaService;
  private final InspectionRepository inspections;
  private final Requests requests;

  MediaController(MediaService mediaService, InspectionRepository inspections, Requests requests) {
    this.mediaService = mediaService;
    this.inspections = inspections;
    this.requests = requests;
  }

  // Customer (session cookie scoped to one inspection).

  ServerResponse list(ServerRequest request) {
    return ServerResponse.ok().body(mediaService.confirmedFor(customerInspectionId()));
  }

  ServerResponse requestUpload(ServerRequest request) throws Exception {
    UploadRequest body = requests.body(request, UploadRequest.class);
    return ServerResponse.status(HttpStatus.CREATED)
        .body(
            mediaService.requestUpload(
                customerInspectionId(), body.contentType(), body.sizeBytes()));
  }

  ServerResponse confirm(ServerRequest request) {
    return ServerResponse.ok()
        .body(mediaService.confirm(customerInspectionId(), requests.pathId(request, "mediaId")));
  }

  // Admin.

  ServerResponse forAdmin(ServerRequest request) {
    UUID inspectionId = requests.pathId(request, "inspectionId");
    if (!inspections.existsById(inspectionId)) {
      throw ApiException.notFound("Inspection");
    }
    return ServerResponse.ok().body(mediaService.confirmedFor(inspectionId));
  }

  private UUID customerInspectionId() {
    return requests.principal(CustomerPrincipal.class).inspectionId();
  }
}
