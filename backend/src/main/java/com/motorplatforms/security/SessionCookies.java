package com.motorplatforms.security;

import com.motorplatforms.common.AppProperties;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the HttpOnly, Secure, SameSite=Strict session cookies. */
@Component
public class SessionCookies {

  public static final String ADMIN = "admin_session";
  public static final String CUSTOMER = "inspection_session";
  public static final String CUSTOMER_PATH = "/api/public";

  private final boolean secure;

  public SessionCookies(AppProperties props) {
    this.secure = props.cookieSecure();
  }

  public String set(String name, String path, String value, Duration ttl) {
    return build(name, path, value, ttl);
  }

  public String clear(String name, String path) {
    return build(name, path, "", Duration.ZERO);
  }

  public static String headerName() {
    return HttpHeaders.SET_COOKIE;
  }

  private String build(String name, String path, String value, Duration ttl) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path(path)
        .maxAge(ttl)
        .build()
        .toString();
  }
}
