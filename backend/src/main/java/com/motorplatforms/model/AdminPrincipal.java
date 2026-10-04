package com.motorplatforms.model;

import java.util.UUID;

/** The logged-in portal admin, taken from the verified admin session cookie. */
public record AdminPrincipal(UUID id, String email) {}
