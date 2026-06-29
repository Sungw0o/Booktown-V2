package com.booktown.domain.quiz.dto;

public record AnswerResult(
        Long questionId,
        int selectedOptionOrder,
        int correctOptionOrder,
        boolean correct,
        String explanation
) {}
