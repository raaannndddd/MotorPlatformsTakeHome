package com.motorplatforms.infra.storage;

import com.motorplatforms.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * MOCK. Plays the part of the S3 endpoint that presigned URLs point at. Access is by signature
 * only, like S3; there is no session check. Does not exist in production.
 */
@RestController
@RequestMapping("/storage")
class LocalStorageController {

  private final LocalDiskStorage storage;

  LocalStorageController(LocalDiskStorage storage) {
    this.storage = storage;
  }

  @PutMapping("/{key}")
  @ResponseStatus(HttpStatus.OK)
  void upload(
      @PathVariable String key,
      @RequestParam String type,
      @RequestParam long expires,
      @RequestParam long max,
      @RequestParam String sig,
      HttpServletRequest request)
      throws IOException {
    if (!type.equals(request.getContentType())) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "BAD_SIGNATURE", "Content-Type does not match the signed URL.");
    }
    storage.write(key, type, max, expires, sig, request.getInputStream());
  }

  @GetMapping("/{key}")
  ResponseEntity<FileSystemResource> download(
      @PathVariable String key,
      @RequestParam String type,
      @RequestParam long expires,
      @RequestParam String sig) {
    var file = storage.read(key, type, expires, sig);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(type))
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=900")
        .body(new FileSystemResource(file));
  }
}
