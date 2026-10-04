package com.motorplatforms.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 100) String carModel,
    @NotBlank @Size(max = 30) String phone,
    @NotBlank @Size(max = 20) String rego) {}
