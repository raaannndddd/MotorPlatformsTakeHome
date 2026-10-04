package com.motorplatforms.inspections;

import com.motorplatforms.auth.CustomerPrincipal;
import com.motorplatforms.auth.JwtService;
import com.motorplatforms.auth.SessionCookies;
import com.motorplatforms.common.AppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, customer-facing endpoints. session/otp/verify take the link token; everything else
 * requires the customer session cookie issued by a successful OTP check.
 */
@RestController
@RequestMapping("/api/public")
class CustomerInspectionController {

  record TokenRequest(@NotBlank @Size(max = 100) String token) {}

  record VerifyOtpRequest(
      @NotBlank @Size(max = 100) String token,
      @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {}

  record StatusResponse(InspectionStatus status) {}

  private final CustomerInspectionService service;
  private final JwtService jwt;
  private final SessionCookies cookies;
  private final AppProperties props;

  CustomerInspectionController(
      CustomerInspectionService service,
      JwtService jwt,
      SessionCookies cookies,
      AppProperties props) {
    this.service = service;
    this.jwt = jwt;
    this.cookies = cookies;
    this.props = props;
  }

  @PostMapping("/session")
  StatusResponse open(@Valid @RequestBody TokenRequest body) {
    return new StatusResponse(service.open(body.token()));
  }

  @PostMapping("/otp")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void requestOtp(@Valid @RequestBody TokenRequest body) {
    service.requestOtp(body.token());
  }

  /** Exchanges the link token + OTP for a session cookie scoped to this one inspection. */
  @PostMapping("/otp/verify")
  ResponseEntity<Void> verifyOtp(@Valid @RequestBody VerifyOtpRequest body) {
    UUID inspectionId = service.verifyOtp(body.token(), body.code());
    String session =
        jwt.issue(
            JwtService.CUSTOMER_AUDIENCE,
            inspectionId.toString(),
            Map.of(),
            props.customerSessionTtl());
    return ResponseEntity.noContent()
        .header(
            SessionCookies.headerName(),
            cookies.set(
                SessionCookies.CUSTOMER,
                SessionCookies.CUSTOMER_PATH,
                session,
                props.customerSessionTtl()))
        .build();
  }

  /** Ends the customer session: the link and session are both dead after submission. */
  @PostMapping("/submit")
  ResponseEntity<StatusResponse> submit(
      @AuthenticationPrincipal CustomerPrincipal customer,
      @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
      @Valid @RequestBody SubmissionRequest body) {
    service.submit(customer.inspectionId(), idempotencyKey, body);
    return ResponseEntity.ok()
        .header(
            SessionCookies.headerName(),
            cookies.clear(SessionCookies.CUSTOMER, SessionCookies.CUSTOMER_PATH))
        .body(new StatusResponse(InspectionStatus.SUBMITTED));
  }

  @GetMapping("/inspection")
  StatusResponse current(@AuthenticationPrincipal CustomerPrincipal customer) {
    return new StatusResponse(service.get(customer.inspectionId()).getStatus());
  }
}
