package com.motorplatforms.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** The one error shape every endpoint returns: {"error": {"code", "message", "fields"?}}. */
public record ErrorResponse(Body error) {

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Body(String code, String message, Map<String, String> fields) {}

  public static ErrorResponse of(String code, String message, Map<String, String> fields) {
    return new ErrorResponse(new Body(code, message, fields));
  }
}
