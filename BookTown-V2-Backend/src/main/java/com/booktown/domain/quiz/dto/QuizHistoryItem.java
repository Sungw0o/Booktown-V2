package com.booktown.domain.quiz.dto;

import com.booktown.domain.quiz.entity.QuizSubmission;

import java.time.LocalDateTime;

public record QuizHistoryItem(
        Long submissionId,
        Long quizId,
        Long bookId,
        String difficulty,
        int score,
        int correctCount,
        int totalCount,
        LocalDateTime submittedAt
) {
    public static QuizHistoryItem from(QuizSubmission submission) {
        return new QuizHistoryItem(
                submission.getId(),
                submission.getQuiz().getId(),
                submission.getQuiz().getBook().getId(),
                submission.getQuiz().getDifficulty().name(),
                submission.getScore(),
                submission.getCorrectCount(),
                submission.getTotalCount(),
                submission.getSubmittedAt()
        );
    }
}
