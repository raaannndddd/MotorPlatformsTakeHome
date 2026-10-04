package com.motorplatforms.infra.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Encrypts an entity field on write and decrypts it on read, so the database only ever holds
 * ciphertext. Use with {@code @Convert(converter = EncryptedString.class)}.
 *
 * <p>Hibernate creates this through Spring, which injects the cipher.
 */
@Converter
public class EncryptedString implements AttributeConverter<String, String> {

  private final PiiCipher cipher;

  public EncryptedString(PiiCipher cipher) {
    this.cipher = cipher;
  }

  @Override
  public String convertToDatabaseColumn(String value) {
    return value == null ? null : cipher.encrypt(value);
  }

  @Override
  public String convertToEntityAttribute(String value) {
    return value == null ? null : cipher.decrypt(value);
  }
}
