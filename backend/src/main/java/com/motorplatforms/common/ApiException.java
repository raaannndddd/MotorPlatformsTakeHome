package com.motorplatforms.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** An error that maps directly to an HTTP response in the standard error shape. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final Map<String, String> fields;

  public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public static ApiException notFound(String what) {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found.");
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException invalidField(String field, String message) {
    return new ApiException(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid input.", Map.of(field, message));
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Map<String, String> fields() {
    return fields;
  }
}
