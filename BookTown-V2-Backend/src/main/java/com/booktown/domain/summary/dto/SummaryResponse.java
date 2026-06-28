package com.booktown.domain.summary.dto;

import com.booktown.domain.summary.document.SummaryDocument;
import com.booktown.domain.summary.document.SummaryFeedback;

import java.time.LocalDateTime;

public record SummaryResponse(
        String summaryId,
        Long bookId,
        String content,
        boolean isRegeneration,
        FeedbackInfo feedback,
        LocalDateTime createdAt
) {
    public record FeedbackInfo(int rating, String comment) {}

    public static SummaryResponse from(SummaryDocument doc) {
        SummaryFeedback fb = doc.getFeedback();
        FeedbackInfo feedbackInfo = fb != null ? new FeedbackInfo(fb.getRating(), fb.getComment()) : null;
        return new SummaryResponse(
                doc.getId(),
                doc.getBookId(),
                doc.getContent(),
                doc.isRegeneration(),
                feedbackInfo,
                doc.getCreatedAt()
        );
    }
}
