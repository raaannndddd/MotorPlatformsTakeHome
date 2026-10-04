package com.motorplatforms.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motorplatforms.common.AppProperties;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PiiCipherTest {

  private final PiiCipher cipher =
      new PiiCipher(
          new AppProperties(
              "http://localhost",
              "x".repeat(32),
              "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
              true,
              new AppProperties.Storage("dir", "secret"),
              1,
              Duration.ofDays(7),
              Duration.ofMinutes(5),
              5,
              Duration.ofHours(1),
              Duration.ofMinutes(30)));

  @Test
  void roundTripsWithAVersionPrefix() {
    String stored = cipher.encrypt("Jane Citizen");

    assertThat(stored).startsWith("v1:").doesNotContain("Jane");
    assertThat(cipher.decrypt(stored)).isEqualTo("Jane Citizen");
  }

  @Test
  void usesAFreshIvEveryTime() {
    assertThat(cipher.encrypt("ABC123")).isNotEqualTo(cipher.encrypt("ABC123"));
  }

  @Test
  void rejectsTamperedCiphertext() {
    String stored = cipher.encrypt("ABC123");
    byte[] bytes = Base64.getDecoder().decode(stored.substring(3));
    bytes[bytes.length - 1] ^= 1; // flip one bit of the auth tag
    String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsUnknownKeyVersion() {
    String stored = cipher.encrypt("ABC123").replaceFirst("v1:", "v9:");

    assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(IllegalStateException.class);
  }
}
