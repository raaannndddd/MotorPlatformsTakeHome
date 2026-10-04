package com.motorplatforms.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed, validated application settings. The app fails to start if any are missing. */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
    @NotBlank String publicBaseUrl,
    @NotBlank @Size(min = 32) String jwtSecret,
    @NotBlank String piiEncryptionKey,
    boolean cookieSecure,
    @Valid @NotNull Storage storage,
    @Positive long maxMediaBytes,
    @NotNull Duration linkTtl,
    @NotNull Duration otpTtl,
    @Positive int otpMaxAttempts,
    @NotNull Duration adminSessionTtl,
    @NotNull Duration customerSessionTtl) {

  public record Storage(@NotBlank String dir, @NotBlank String signingSecret) {}
}
