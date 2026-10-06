package com.motorplatforms.infra.storage;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.common.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * MOCK. Stores objects on local disk behind HMAC-signed, expiring URLs that behave like S3
 * presigned URLs. Production: S3 presigned PUT/GET (multipart upload for video).
 *
 * <p>Originals are written once and never modified.
 */
@Component
public class LocalDiskStorage implements ObjectStorage {

  private static final Duration URL_TTL = Duration.ofMinutes(15);
  private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9-]{1,100}");

  private final Path root;
  private final SecretKeySpec signingKey;

  public LocalDiskStorage(AppProperties props) {
    this.root = Path.of(props.storage().dir()).toAbsolutePath();
    this.signingKey =
        new SecretKeySpec(
            props.storage().signingSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Override
  public String uploadUrl(String key, String contentType, long maxBytes) {
    long expires = Instant.now().plus(URL_TTL).getEpochSecond();
    String sig = sign("PUT", key, contentType, maxBytes, expires);
    return url(key, contentType, expires, sig) + "&max=" + maxBytes;
  }

  @Override
  public String downloadUrl(String key, String contentType) {
    long expires = Instant.now().plus(URL_TTL).getEpochSecond();
    return url(key, contentType, expires, sign("GET", key, contentType, 0, expires));
  }

  @Override
  public OptionalLong size(String key) {
    Path file = path(key);
    try {
      return Files.exists(file) ? OptionalLong.of(Files.size(file)) : OptionalLong.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Handles the PUT to an upload URL. Writes to a temp file first so a partial upload is never
   * seen.
   */
  void write(
      String key, String contentType, long maxBytes, long expires, String sig, InputStream body)
      throws IOException {
    verify("PUT", key, contentType, maxBytes, expires, sig);
    Path target = path(key);
    if (Files.exists(target)) {
      throw new ApiException(HttpStatus.CONFLICT, "ALREADY_UPLOADED", "Object already exists.");
    }
    Files.createDirectories(root);
    Path temp = Files.createTempFile(root, "upload-", ".part");
    try (OutputStream out = Files.newOutputStream(temp)) {
      byte[] buffer = new byte[64 * 1024];
      long total = 0;
      for (int n; (n = body.read(buffer)) != -1; ) {
        total += n;
        if (total > maxBytes) {
          throw new ApiException(
              HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", "Upload is larger than declared.");
        }
        out.write(buffer, 0, n);
      }
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
  }

  /** Handles the GET of a download URL. */
  Path read(String key, String contentType, long expires, String sig) {
    verify("GET", key, contentType, 0, expires, sig);
    Path file = path(key);
    if (!Files.exists(file)) {
      throw ApiException.notFound("Object");
    }
    return file;
  }

  private void verify(
      String method, String key, String contentType, long maxBytes, long expires, String sig) {
    boolean valid =
        Instant.now().getEpochSecond() <= expires
            && MessageDigest.isEqual(
                sign(method, key, contentType, maxBytes, expires).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    if (!valid) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "BAD_SIGNATURE", "The URL is invalid or has expired.");
    }
  }

  private String sign(String method, String key, String contentType, long maxBytes, long expires) {
    String payload = String.join("\n", method, key, contentType, "" + maxBytes, "" + expires);
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(signingKey);
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private Path path(String key) {
    if (!SAFE_KEY.matcher(key).matches()) {
      throw ApiException.notFound("Object");
    }
    return root.resolve(key);
  }

  private static String url(String key, String contentType, long expires, String sig) {
    return "/storage/%s?type=%s&expires=%d&sig=%s"
        .formatted(key, URLEncoder.encode(contentType, StandardCharsets.UTF_8), expires, sig);
  }
}
