package com.motorplatforms.security;

import com.motorplatforms.common.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies session JWTs. The audience separates admin sessions from customer sessions, so
 * one can never be used as the other.
 */
@Component
public class JwtService {

  public static final String ADMIN_AUDIENCE = "admin";
  public static final String CUSTOMER_AUDIENCE = "customer";

  private final SecretKey key;

  public JwtService(AppProperties props) {
    this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String issue(String audience, String subject, Map<String, String> claims, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .audience()
        .add(audience)
        .and()
        .subject(subject)
        .claims(claims)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  /** Returns the claims if the token is valid for the audience, empty otherwise. */
  public Optional<Claims> verify(String audience, String token) {
    try {
      return Optional.of(
          Jwts.parser()
              .verifyWith(key)
              .requireAudience(audience)
              .build()
              .parseSignedClaims(token)
              .getPayload());
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
