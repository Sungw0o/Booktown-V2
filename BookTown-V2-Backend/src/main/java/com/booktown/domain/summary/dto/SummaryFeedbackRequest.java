package com.booktown.domain.summary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SummaryFeedbackRequest(
        @Min(1) @Max(5) int rating,
        String comment
) {}
