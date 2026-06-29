package com.booktown.domain.quiz.dto;

import com.booktown.domain.quiz.entity.QuizJob;

import java.time.LocalDateTime;

public record QuizJobResponse(
        Long jobId,
        Long bookId,
        String difficulty,
        int questionCount,
        String status,
        Long quizId,
        String errorMessage,
        boolean retryable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QuizJobResponse from(QuizJob job) {
        return new QuizJobResponse(
                job.getId(),
                job.getBook().getId(),
                job.getDifficulty().name(),
                job.getQuestionCount(),
                job.getStatus().name(),
                job.getQuizId(),
                job.getErrorMessage(),
                job.isRetryable(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
