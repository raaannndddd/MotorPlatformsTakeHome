package com.motorplatforms.controller;

import com.motorplatforms.common.AppProperties;
import com.motorplatforms.model.AdminPrincipal;
import com.motorplatforms.model.User;
import com.motorplatforms.model.UserResponse;
import com.motorplatforms.security.JwtService;
import com.motorplatforms.security.SessionCookies;
import com.motorplatforms.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class AuthController {

  record LoginRequest(@NotBlank String email, @NotBlank String password) {}

  private final AuthService authService;
  private final JwtService jwt;
  private final SessionCookies cookies;
  private final AppProperties props;
  private final Requests requests;

  AuthController(
      AuthService authService,
      JwtService jwt,
      SessionCookies cookies,
      AppProperties props,
      Requests requests) {
    this.authService = authService;
    this.jwt = jwt;
    this.cookies = cookies;
    this.props = props;
    this.requests = requests;
  }

  ServerResponse login(ServerRequest request) throws Exception {
    LoginRequest body = requests.body(request, LoginRequest.class);
    User user = authService.login(body.email(), body.password());
    String token =
        jwt.issue(
            JwtService.ADMIN_AUDIENCE,
            user.getId().toString(),
            Map.of("email", user.getEmail()),
            props.adminSessionTtl());
    return ServerResponse.ok()
        .header(
            SessionCookies.headerName(),
            cookies.set(SessionCookies.ADMIN, "/", token, props.adminSessionTtl()))
        .body(UserResponse.from(user));
  }

  ServerResponse logout(ServerRequest request) {
    return ServerResponse.noContent()
        .header(SessionCookies.headerName(), cookies.clear(SessionCookies.ADMIN, "/"))
        .build();
  }

  ServerResponse me(ServerRequest request) {
    return ServerResponse.ok().body(requests.principal(AdminPrincipal.class));
  }
}
