package com.motorplatforms.infra.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MOCK. Writes the message to the log instead of sending it. Production: Twilio.
 *
 * <p>In development the log stands in for the customer's phone, so the body (with the link or code)
 * is printed; the number is masked. A real adapter must never log the body.
 */
@Component
class LogSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

  @Override
  public void send(String toE164, String body) {
    log.info("[SMS MOCK] to ***{}: {}", toE164.substring(Math.max(0, toE164.length() - 3)), body);
  }
}
