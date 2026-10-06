package com.motorplatforms.model;

import java.util.UUID;

/** Where and how the browser uploads one file directly to storage. */
public record UploadTicket(UUID mediaId, String uploadUrl, String method, String contentType) {}
