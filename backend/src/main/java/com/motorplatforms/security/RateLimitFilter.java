package com.motorplatforms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorplatforms.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A simple fixed-window, per-IP limit on unauthenticated endpoints (login and the customer API).
 *
 * <p>Production: enforce at the load balancer / API gateway, or back this with Redis so the limit
 * holds across instances.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MS = 60_000;
  private static final int MAX_TRACKED_IPS = 10_000;

  private record Window(long startedAt, AtomicInteger count) {}

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final int limitPerMinute;
  private final ObjectMapper json;

  RateLimitFilter(@Value("${app.public-rate-limit-per-minute:60}") int limit, ObjectMapper json) {
    this.limitPerMinute = limit;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.startsWith("/api/public/") || path.equals("/api/auth/login"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = System.currentTimeMillis();
    if (windows.size() > MAX_TRACKED_IPS) {
      windows.clear(); // Crude bound on memory; acceptable for a single-node mock.
    }
    Window window =
        windows.compute(
            request.getRemoteAddr(),
            (ip, w) ->
                w == null || now - w.startedAt() > WINDOW_MS
                    ? new Window(now, new AtomicInteger())
                    : w);
    if (window.count().incrementAndGet() > limitPerMinute) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      json.writeValue(
          response.getOutputStream(),
          ErrorResponse.of("RATE_LIMITED", "Too many requests. Try again shortly.", Map.of()));
      return;
    }
    chain.doFilter(request, response);
  }
}
