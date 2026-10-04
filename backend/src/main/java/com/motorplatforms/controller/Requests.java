package com.motorplatforms.controller;

import com.motorplatforms.common.ApiException;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * What annotated controllers did through @Valid, @PathVariable, @RequestHeader
 * and @AuthenticationPrincipal, for the handler functions wired up in {@link Routes}. Bad input
 * becomes an {@link ApiException}, so {@link GlobalExceptionHandler} gives it the standard error
 * shape.
 */
@Component
class Requests {

  private final Validator validator;

  Requests(Validator validator) {
    this.validator = validator;
  }

  /** Reads the JSON body and validates it; field errors come back as a 400 with `fields`. */
  <T> T body(ServerRequest request, Class<T> type) throws Exception {
    T body = request.body(type);
    if (body == null) {
      throw malformed();
    }
    Map<String, String> fields = new LinkedHashMap<>();
    validator
        .validate(body)
        .forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
    if (!fields.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid input.", fields);
    }
    return body;
  }

  UUID pathId(ServerRequest request, String name) {
    return uuid(request.pathVariable(name));
  }

  /** The optional Idempotency-Key header. */
  UUID idempotencyKey(ServerRequest request) {
    String key = request.headers().firstHeader("Idempotency-Key");
    return key == null || key.isBlank() ? null : uuid(key);
  }

  /**
   * The principal set by the session cookie filter; the route's access rule guarantees its type.
   */
  <T> T principal(Class<T> type) {
    return type.cast(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
  }

  <E extends Enum<E>> E optionalParam(ServerRequest request, String name, Class<E> type) {
    String value = request.param(name).orElse("");
    try {
      return value.isBlank() ? null : Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      throw malformed();
    }
  }

  private static UUID uuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw malformed();
    }
  }

  private static ApiException malformed() {
    return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request.");
  }
}
