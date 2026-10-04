package com.motorplatforms.clients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record ClientRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 100) String carModel,
    @NotBlank @Size(max = 30) String phone,
    @NotBlank @Size(max = 20) String rego) {

  /** Trims everything, uppercases the rego and converts the phone to E.164. */
  ClientRequest normalised() {
    return new ClientRequest(
        name.trim(),
        carModel.trim(),
        PhoneNumbers.toE164(phone),
        rego.trim().toUpperCase(Locale.ROOT));
  }
}
