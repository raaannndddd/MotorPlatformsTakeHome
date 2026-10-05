package com.motorplatforms.model;

/** A single inspection for admin review: the dashboard row plus the submitted form. */
public record InspectionDetail(
    InspectionSummary inspection, Integer mileage, String conditionNotes, String response) {

  public static InspectionDetail from(Inspection i) {
    return new InspectionDetail(
        InspectionSummary.from(i), i.getMileage(), i.getConditionNotes(), i.getResponse());
  }
}
