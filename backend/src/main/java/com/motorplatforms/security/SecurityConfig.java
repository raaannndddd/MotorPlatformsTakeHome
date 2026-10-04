package com.motorplatforms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorplatforms.model.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

// Skipped by `./gradlew seedAdmin`, which runs without a web server.
@Configuration
@ConditionalOnWebApplication
class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, ObjectMapper json)
      throws Exception {
    return http
        // Session cookies are SameSite=Strict, so cross-site requests never carry them.
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // The customer page URL contains the link token; never leak it in a Referer header.
        .headers(h -> h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new SessionCookieFilter(jwt), UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/public/session",
                        "/api/public/otp",
                        "/api/public/otp/verify")
                    .permitAll()
                    .requestMatchers("/api/public/**")
                    .hasRole("CUSTOMER")
                    .requestMatchers("/api/**")
                    .hasRole("ADMIN")
                    // Static pages, the /i/{token} page and signed /storage URLs.
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            write(json, res, 401, "UNAUTHENTICATED", "Please log in."))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            write(json, res, 403, "FORBIDDEN", "You do not have access.")))
        .build();
  }

  private static void write(
      ObjectMapper json, HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(res.getOutputStream(), ErrorResponse.of(code, message, Map.of()));
  }
}
