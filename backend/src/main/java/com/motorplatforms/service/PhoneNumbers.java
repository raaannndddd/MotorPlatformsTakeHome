package com.motorplatforms.clients;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.motorplatforms.common.ApiException;

final class PhoneNumbers {

  // [ASSUMPTION] Numbers without a country code are Australian.
  private static final String DEFAULT_REGION = "AU";
  private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

  private PhoneNumbers() {}

  /** Normalises to E.164 or rejects the field. */
  static String toE164(String raw) {
    try {
      var number = UTIL.parse(raw, DEFAULT_REGION);
      if (UTIL.isValidNumber(number)) {
        return UTIL.format(number, PhoneNumberFormat.E164);
      }
    } catch (NumberParseException ignored) {
      // Falls through to the validation error.
    }
    throw ApiException.invalidField("phone", "must be a valid phone number");
  }
}
