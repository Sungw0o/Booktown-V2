package com.booktown.domain.illustration.dto;

import com.booktown.domain.illustration.document.IllustrationDocument;

import java.time.LocalDateTime;

public record IllustrationResponse(
        String illustrationId,
        Long sceneId,
        String style,
        String imageUrl,
        boolean isRegeneration,
        LocalDateTime createdAt
) {
    public static IllustrationResponse from(IllustrationDocument doc) {
        return new IllustrationResponse(
                doc.getId(),
                doc.getSceneId(),
                doc.getStyle(),
                doc.getImageUrl(),
                doc.isRegeneration(),
                doc.getCreatedAt()
        );
    }
}
