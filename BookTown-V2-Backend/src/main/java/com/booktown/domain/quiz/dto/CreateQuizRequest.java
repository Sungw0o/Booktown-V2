package com.booktown.domain.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateQuizRequest(
        List<Long> chapterIds,
        @Min(1) @Max(20) int questionCount,
        @NotBlank String difficulty
) {}
