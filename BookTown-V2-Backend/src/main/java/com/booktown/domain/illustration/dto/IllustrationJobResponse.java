package com.booktown.domain.illustration.dto;

import com.booktown.domain.illustration.entity.IllustrationJob;

import java.time.LocalDateTime;

public record IllustrationJobResponse(
        Long jobId,
        Long sceneId,
        String style,
        String status,
        String illustrationId,
        String errorMessage,
        boolean retryable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IllustrationJobResponse from(IllustrationJob job) {
        return new IllustrationJobResponse(
                job.getId(),
                job.getScene().getId(),
                job.getStyle().name(),
                job.getStatus().name(),
                job.getIllustrationDocumentId(),
                job.getErrorMessage(),
                job.isRetryable(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
