package com.booktown.domain.admin.dto;

import com.booktown.domain.admin.entity.ContentJob;

import java.time.LocalDateTime;

public record ContentJobResponse(
        Long jobId,
        Long bookId,
        String status,
        Integer chapterCount,
        String errorMessage,
        boolean retryable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContentJobResponse from(ContentJob job) {
        return new ContentJobResponse(
                job.getId(),
                job.getBook().getId(),
                job.getStatus().name(),
                job.getChapterCount(),
                job.getErrorMessage(),
                job.isRetryable(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
