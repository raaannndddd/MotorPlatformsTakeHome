package com.motorplatforms.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Every HTTP route of the backend, in one place. The controllers are plain handler classes with no
 * mapping annotations. Who may call each route is decided in {@code SecurityConfig}: /api/auth
 * login and logout and the customer's session/otp calls are open, the rest of /api/public needs the
 * customer cookie, and everything else under /api needs an admin.
 */
@Configuration
class Routes {

  @Bean
  RouterFunction<ServerResponse> apiRoutes(
      AuthController auth,
      UserController users,
      ClientController clients,
      InspectionController inspections,
      MediaController media,
      CustomerInspectionController customer,
      CustomerPageController page) {
    return RouterFunctions.route()
        // Admin session.
        .POST("/api/auth/login", auth::login)
        .POST("/api/auth/logout", auth::logout)
        .GET("/api/auth/me", auth::me)
        // Admin: users, clients, inspections.
        .GET("/api/users", users::list)
        .POST("/api/users", users::create)
        .GET("/api/clients", clients::list)
        .POST("/api/clients", clients::create)
        .GET("/api/clients/{id}", clients::get)
        .PUT("/api/clients/{id}", clients::update)
        .DELETE("/api/clients/{id}", clients::delete)
        .GET("/api/inspections", inspections::list)
        .POST("/api/inspections", inspections::create)
        .GET("/api/inspections/{id}", inspections::get)
        .POST("/api/inspections/{id}/response", inspections::respond)
        .GET("/api/inspections/{inspectionId}/media", media::forAdmin)
        // Customer: the link token opens a session, the OTP cookie unlocks the rest.
        .POST("/api/public/session", customer::open)
        .POST("/api/public/otp", customer::requestOtp)
        .POST("/api/public/otp/verify", customer::verifyOtp)
        .GET("/api/public/inspection", customer::current)
        .POST("/api/public/submit", customer::submit)
        .GET("/api/public/media", media::list)
        .POST("/api/public/media", media::requestUpload)
        .POST("/api/public/media/{mediaId}/confirm", media::confirm)
        // The page the SMS link opens.
        .GET("/i/{token}", page::page)
        .build();
  }
}
