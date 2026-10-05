package com.motorplatforms.model;

/** SENT -> OPENED -> SUBMITTED -> RESPONDED. Link expiry is checked on the token, not stored. */
public enum InspectionStatus {
  SENT,
  OPENED,
  SUBMITTED,
  RESPONDED
}
