package com.booktown.domain.summary.dto;

import com.booktown.domain.summary.entity.SummaryJob;

import java.time.LocalDateTime;

public record SummaryJobResponse(
        Long jobId,
        Long bookId,
        String status,
        String summaryId,
        String errorMessage,
        boolean retryable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SummaryJobResponse from(SummaryJob job) {
        return new SummaryJobResponse(
                job.getId(),
                job.getBook().getId(),
                job.getStatus().name(),
                job.getSummaryDocumentId(),
                job.getErrorMessage(),
                job.isRetryable(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
