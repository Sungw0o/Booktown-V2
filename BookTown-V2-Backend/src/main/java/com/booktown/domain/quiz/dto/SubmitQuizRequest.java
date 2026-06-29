package com.booktown.domain.quiz.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubmitQuizRequest(
        @NotEmpty List<AnswerItem> answers
) {
    public record AnswerItem(Long questionId, int selectedOptionOrder) {}
}
