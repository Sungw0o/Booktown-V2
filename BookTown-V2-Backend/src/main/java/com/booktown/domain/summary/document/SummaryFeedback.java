package com.booktown.domain.summary.document;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SummaryFeedback {

    private Long userId;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

    public static SummaryFeedback of(Long userId, int rating, String comment) {
        SummaryFeedback feedback = new SummaryFeedback();
        feedback.userId = userId;
        feedback.rating = rating;
        feedback.comment = comment;
        feedback.createdAt = LocalDateTime.now();
        return feedback;
    }
}
