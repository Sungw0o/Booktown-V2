package com.booktown.domain.summary.document;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Document(collection = "summaries")
public class SummaryDocument {

    @Id
    private String id;

    @Indexed
    private Long bookId;

    @Indexed
    private Long userId;

    private Long summaryJobId;
    private String content;
    private boolean isRegeneration;
    private SummaryFeedback feedback;
    private LocalDateTime createdAt;

    public static SummaryDocument create(Long bookId, Long userId, Long summaryJobId,
                                         String content, boolean isRegeneration) {
        SummaryDocument doc = new SummaryDocument();
        doc.bookId = bookId;
        doc.userId = userId;
        doc.summaryJobId = summaryJobId;
        doc.content = content;
        doc.isRegeneration = isRegeneration;
        doc.createdAt = LocalDateTime.now();
        return doc;
    }

    public void applyFeedback(SummaryFeedback feedback) {
        this.feedback = feedback;
    }
}
