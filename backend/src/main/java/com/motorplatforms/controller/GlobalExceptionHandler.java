package com.motorplatforms.controller;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.model.ErrorResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every exception to the standard {@link ErrorResponse} shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> handle(ApiException e) {
    return respond(e.status(), e.code(), e.getMessage(), e.fields());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class
  })
  ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
    return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request.", Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException e) {
    return respond(
        HttpStatus.CONFLICT, "CONFLICT", "The change conflicts with existing data.", Map.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> handle(NoResourceFoundException e) {
    return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.", Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("Unhandled error", e);
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong.", Map.of());
  }

  private static ResponseEntity<ErrorResponse> respond(
      HttpStatus status, String code, String message, Map<String, String> fields) {
    return ResponseEntity.status(status).body(ErrorResponse.of(code, message, fields));
  }
}
