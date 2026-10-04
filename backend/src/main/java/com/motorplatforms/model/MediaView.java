package com.motorplatforms.media;

import java.util.UUID;

/** A confirmed upload, with a short-lived URL to view it. */
public record MediaView(UUID id, String contentType, long sizeBytes, String url) {}
