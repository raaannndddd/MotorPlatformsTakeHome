package com.motorplatforms.inspections;

/** A single inspection for staff review: the dashboard row plus the submitted form. */
public record InspectionDetail(
    InspectionSummary inspection, Integer mileage, String conditionNotes, String response) {

  static InspectionDetail from(Inspection i) {
    return new InspectionDetail(
        InspectionSummary.from(i), i.getMileage(), i.getConditionNotes(), i.getResponse());
  }
}
