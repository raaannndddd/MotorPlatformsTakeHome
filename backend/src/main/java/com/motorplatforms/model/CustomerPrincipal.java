package com.motorplatforms.model;

import java.util.UUID;

/** A customer who passed the OTP check. The session is scoped to exactly one inspection. */
public record CustomerPrincipal(UUID inspectionId) {}
