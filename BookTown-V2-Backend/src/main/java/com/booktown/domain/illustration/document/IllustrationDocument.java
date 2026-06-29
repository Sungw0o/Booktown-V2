package com.booktown.domain.illustration.document;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Document(collection = "illustrations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IllustrationDocument {

    @Id
    private String id;

    private Long sceneId;
    private Long userId;
    private Long illustrationJobId;
    private String style;
    private String promptHint;
    private String imageUrl;
    private boolean isRegeneration;
    private LocalDateTime createdAt;

    public static IllustrationDocument create(Long sceneId, Long userId, Long jobId,
                                               String style, String promptHint,
                                               String imageUrl, boolean isRegeneration) {
        IllustrationDocument doc = new IllustrationDocument();
        doc.sceneId = sceneId;
        doc.userId = userId;
        doc.illustrationJobId = jobId;
        doc.style = style;
        doc.promptHint = promptHint;
        doc.imageUrl = imageUrl;
        doc.isRegeneration = isRegeneration;
        doc.createdAt = LocalDateTime.now();
        return doc;
    }
}
