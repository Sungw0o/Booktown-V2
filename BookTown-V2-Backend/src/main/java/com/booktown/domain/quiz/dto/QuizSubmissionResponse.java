package com.booktown.domain.quiz.dto;

import com.booktown.domain.quiz.entity.QuizSubmission;

import java.time.LocalDateTime;
import java.util.List;

public record QuizSubmissionResponse(
        Long submissionId,
        Long quizId,
        int score,
        int correctCount,
        int totalCount,
        List<AnswerResult> answers,
        LocalDateTime submittedAt
) {
    public static QuizSubmissionResponse of(QuizSubmission submission, List<AnswerResult> answers) {
        return new QuizSubmissionResponse(
                submission.getId(),
                submission.getQuiz().getId(),
                submission.getScore(),
                submission.getCorrectCount(),
                submission.getTotalCount(),
                answers,
                submission.getSubmittedAt()
        );
    }
}
