package com.motorplatforms.service;

/** Every SMS the system sends. */
public final class SmsMessages {

  private SmsMessages() {}

  public static String inspectionLink(String link) {
    return "Hi, this is MotorPlatforms. Please take 5 minutes to fill out this car inspection"
        + " form: "
        + link;
  }

  public static String otp(String code) {
    return "Your MotorPlatforms verification code is " + code + ". It expires in 5 minutes.";
  }

  // [ASSUMPTION] The response is sent as free text inside the SMS.
  public static String response(String response) {
    return "Hi, this is MotorPlatforms. We have reviewed your car inspection: " + response;
  }
}
