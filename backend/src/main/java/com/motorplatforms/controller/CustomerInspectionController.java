package com.motorplatforms.controller;

import com.motorplatforms.common.AppProperties;
import com.motorplatforms.model.CustomerPrincipal;
import com.motorplatforms.model.InspectionStatus;
import com.motorplatforms.model.SubmissionRequest;
import com.motorplatforms.security.JwtService;
import com.motorplatforms.security.SessionCookies;
import com.motorplatforms.service.CustomerInspectionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Public, customer-facing endpoints. session/otp/verify take the link token; everything else
 * requires the customer session cookie issued by a successful OTP check.
 */
@Component
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
  private final Requests requests;

  CustomerInspectionController(
      CustomerInspectionService service,
      JwtService jwt,
      SessionCookies cookies,
      AppProperties props,
      Requests requests) {
    this.service = service;
    this.jwt = jwt;
    this.cookies = cookies;
    this.props = props;
    this.requests = requests;
  }

  ServerResponse open(ServerRequest request) throws Exception {
    TokenRequest body = requests.body(request, TokenRequest.class);
    return ServerResponse.ok().body(new StatusResponse(service.open(body.token())));
  }

  ServerResponse requestOtp(ServerRequest request) throws Exception {
    TokenRequest body = requests.body(request, TokenRequest.class);
    service.requestOtp(body.token());
    return ServerResponse.accepted().build();
  }

  /** Exchanges the link token + OTP for a session cookie scoped to this one inspection. */
  ServerResponse verifyOtp(ServerRequest request) throws Exception {
    VerifyOtpRequest body = requests.body(request, VerifyOtpRequest.class);
    UUID inspectionId = service.verifyOtp(body.token(), body.code());
    String session =
        jwt.issue(
            JwtService.CUSTOMER_AUDIENCE,
            inspectionId.toString(),
            Map.of(),
            props.customerSessionTtl());
    return ServerResponse.noContent()
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
  ServerResponse submit(ServerRequest request) throws Exception {
    UUID idempotencyKey = requests.idempotencyKey(request);
    SubmissionRequest body = requests.body(request, SubmissionRequest.class);
    service.submit(
        requests.principal(CustomerPrincipal.class).inspectionId(), idempotencyKey, body);
    return ServerResponse.ok()
        .header(
            SessionCookies.headerName(),
            cookies.clear(SessionCookies.CUSTOMER, SessionCookies.CUSTOMER_PATH))
        .body(new StatusResponse(InspectionStatus.SUBMITTED));
  }

  ServerResponse current(ServerRequest request) {
    UUID inspectionId = requests.principal(CustomerPrincipal.class).inspectionId();
    return ServerResponse.ok().body(new StatusResponse(service.get(inspectionId).getStatus()));
  }
}
