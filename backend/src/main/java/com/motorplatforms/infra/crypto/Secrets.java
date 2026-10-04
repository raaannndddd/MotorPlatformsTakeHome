package com.motorplatforms.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-way secrets: values that are checked but never read back (link tokens, OTPs). Only their
 * SHA-256 hash is stored.
 */
public final class Secrets {

  private static final SecureRandom RANDOM = new SecureRandom();

  private Secrets() {}

  /** 32 random bytes from a CSPRNG, URL-safe. Not a UUID: a UUID is an identifier, not a secret. */
  public static String newLinkToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** A 6-digit one-time code. */
  public static String newOtp() {
    return "%06d".formatted(RANDOM.nextInt(1_000_000));
  }

  public static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Constant-time comparison of two hashes. */
  public static boolean hashesMatch(String a, String b) {
    return a != null
        && b != null
        && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
