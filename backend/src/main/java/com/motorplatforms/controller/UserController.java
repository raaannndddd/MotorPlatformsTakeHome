package com.motorplatforms.controller;

import com.motorplatforms.model.UserResponse;
import com.motorplatforms.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class UserController {

  record CreateUserRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 128) String password) {}

  private final UserService userService;
  private final Requests requests;

  UserController(UserService userService, Requests requests) {
    this.userService = userService;
    this.requests = requests;
  }

  ServerResponse list(ServerRequest request) {
    return ServerResponse.ok().body(userService.list().stream().map(UserResponse::from).toList());
  }

  ServerResponse create(ServerRequest request) throws Exception {
    CreateUserRequest body = requests.body(request, CreateUserRequest.class);
    return ServerResponse.status(HttpStatus.CREATED)
        .body(UserResponse.from(userService.create(body.email(), body.password())));
  }
}
