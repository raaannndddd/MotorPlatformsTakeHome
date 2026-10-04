package com.motorplatforms.security;

import com.motorplatforms.model.AdminPrincipal;
import com.motorplatforms.model.CustomerPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a valid session cookie into an authenticated principal. An invalid or missing cookie leaves
 * the request anonymous; the authorisation rules then decide what it may reach.
 */
class SessionCookieFilter extends OncePerRequestFilter {

  private final JwtService jwt;

  SessionCookieFilter(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // Prefer the cookie that matches the API being called, so a browser holding both an admin and
    // a customer cookie (for example while testing both sides) is not rejected as the wrong role.
    boolean customerApi = request.getRequestURI().startsWith(SessionCookies.CUSTOMER_PATH);
    Optional<Claims> admin =
        cookie(request, SessionCookies.ADMIN)
            .flatMap(token -> jwt.verify(JwtService.ADMIN_AUDIENCE, token));
    Optional<Claims> customer =
        cookie(request, SessionCookies.CUSTOMER)
            .flatMap(token -> jwt.verify(JwtService.CUSTOMER_AUDIENCE, token));
    if (customerApi && customer.isPresent()) {
      customer.ifPresent(SessionCookieFilter::authenticateCustomer);
    } else if (admin.isPresent()) {
      admin.ifPresent(SessionCookieFilter::authenticateAdmin);
    } else {
      customer.ifPresent(SessionCookieFilter::authenticateCustomer);
    }
    chain.doFilter(request, response);
  }

  private static void authenticateAdmin(Claims claims) {
    var principal =
        new AdminPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class));
    authenticate(principal, "ROLE_ADMIN");
  }

  private static void authenticateCustomer(Claims claims) {
    authenticate(new CustomerPrincipal(UUID.fromString(claims.getSubject())), "ROLE_CUSTOMER");
  }

  private static void authenticate(Object principal, String authority) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(authority)));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private static Optional<String> cookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(c -> c.getName().equals(name) && !c.getValue().isBlank())
        .map(Cookie::getValue)
        .findFirst();
  }
}
