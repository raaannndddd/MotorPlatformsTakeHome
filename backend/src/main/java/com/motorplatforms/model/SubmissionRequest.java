package com.motorplatforms.inspections;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The customer's inspection form. Validated here, then mapped onto the inspection row. Photos and
 * video are uploaded and confirmed separately (see media).
 */
public record SubmissionRequest(
    @NotNull @PositiveOrZero @Max(2_000_000) Integer mileage,
    @NotBlank @Size(max = 5000) String conditionNotes) {}
