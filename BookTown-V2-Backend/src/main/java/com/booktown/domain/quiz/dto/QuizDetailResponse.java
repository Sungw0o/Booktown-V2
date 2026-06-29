package com.booktown.domain.quiz.dto;

import com.booktown.domain.quiz.entity.Quiz;

import java.util.List;

public record QuizDetailResponse(
        Long quizId,
        Long bookId,
        String difficulty,
        List<QuestionResponse> questions
) {
    public static QuizDetailResponse of(Quiz quiz, List<QuestionResponse> questions) {
        return new QuizDetailResponse(
                quiz.getId(),
                quiz.getBook().getId(),
                quiz.getDifficulty().name(),
                questions
        );
    }
}
