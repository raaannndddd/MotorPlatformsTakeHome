package com.motorplatforms.infra.sms;

public interface SmsSender {

  /**
   * @param toE164 recipient in E.164 format
   */
  void send(String toE164, String body);
}
