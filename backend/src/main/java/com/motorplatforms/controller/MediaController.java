package com.motorplatforms.media;

import com.motorplatforms.auth.CustomerPrincipal;
import com.motorplatforms.common.ApiException;
import com.motorplatforms.inspections.InspectionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MediaController {

  record UploadRequest(@NotBlank String contentType, @Positive long sizeBytes) {}

  private final MediaService mediaService;
  private final InspectionRepository inspections;

  MediaController(MediaService mediaService, InspectionRepository inspections) {
    this.mediaService = mediaService;
    this.inspections = inspections;
  }

  // Customer (session cookie scoped to one inspection).

  @GetMapping("/api/public/media")
  List<MediaView> list(@AuthenticationPrincipal CustomerPrincipal customer) {
    return mediaService.confirmedFor(customer.inspectionId());
  }

  @PostMapping("/api/public/media")
  @ResponseStatus(HttpStatus.CREATED)
  MediaService.UploadTicket requestUpload(
      @AuthenticationPrincipal CustomerPrincipal customer, @Valid @RequestBody UploadRequest body) {
    return mediaService.requestUpload(
        customer.inspectionId(), body.contentType(), body.sizeBytes());
  }

  @PostMapping("/api/public/media/{mediaId}/confirm")
  MediaView confirm(
      @AuthenticationPrincipal CustomerPrincipal customer, @PathVariable UUID mediaId) {
    return mediaService.confirm(customer.inspectionId(), mediaId);
  }

  // Staff.

  @GetMapping("/api/inspections/{inspectionId}/media")
  List<MediaView> forStaff(@PathVariable UUID inspectionId) {
    if (!inspections.existsById(inspectionId)) {
      throw ApiException.notFound("Inspection");
    }
    return mediaService.confirmedFor(inspectionId);
  }
}
