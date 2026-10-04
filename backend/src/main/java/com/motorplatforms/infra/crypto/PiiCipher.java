package com.motorplatforms.infra.crypto;

import com.motorplatforms.common.AppProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Two-way encryption for client PII (AES-256-GCM). This is the only class that holds the key.
 *
 * <p>Ciphertext format: {@code <keyVersion>:<base64(iv | ciphertext | tag)>}. The version prefix
 * lets a new key be added for writes while old values still decrypt with the old key.
 *
 * <p>Production: the key comes from KMS (envelope encryption) and storage-level encryption at rest
 * is enabled on top.
 */
@Component
public class PiiCipher {

  private static final String CURRENT_VERSION = "v1";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecureRandom random = new SecureRandom();
  private final Map<String, SecretKey> keys;

  public PiiCipher(AppProperties props) {
    byte[] key = Base64.getDecoder().decode(props.piiEncryptionKey());
    if (key.length != 32) {
      throw new IllegalStateException("PII_ENCRYPTION_KEY must be 32 bytes, base64 encoded.");
    }
    this.keys = Map.of(CURRENT_VERSION, new SecretKeySpec(key, "AES"));
  }

  public String encrypt(String plaintext) {
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, keys.get(CURRENT_VERSION), new GCMParameterSpec(TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
      return CURRENT_VERSION + ":" + Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("PII encryption failed", e);
    }
  }

  public String decrypt(String stored) {
    int sep = stored.indexOf(':');
    SecretKey key = sep > 0 ? keys.get(stored.substring(0, sep)) : null;
    if (key == null) {
      throw new IllegalStateException("Unknown PII key version");
    }
    byte[] in = Base64.getDecoder().decode(stored.substring(sep + 1));
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      // Wrong key or tampered value. Never include the value in the message.
      throw new IllegalStateException("PII decryption failed", e);
    }
  }
}
