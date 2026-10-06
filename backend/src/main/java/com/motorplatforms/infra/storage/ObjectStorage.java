package com.motorplatforms.infra.storage;

import java.util.OptionalLong;

/**
 * Object storage with presigned URLs: the browser uploads and downloads directly, so file bytes
 * never pass through the API.
 */
public interface ObjectStorage {

  /** A short-lived URL that accepts one PUT of at most {@code maxBytes} with this content type. */
  String uploadUrl(String key, String contentType, long maxBytes);

  /** A short-lived URL to read the object. */
  String downloadUrl(String key, String contentType);

  /** The stored object's size, or empty if nothing has been uploaded. */
  OptionalLong size(String key);
}
