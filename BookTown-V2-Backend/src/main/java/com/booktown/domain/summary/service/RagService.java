package com.booktown.domain.summary.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 100;
    private static final int TOP_K = 5;
    private static final String BOOK_ID_KEY = "bookId";

    private final VectorStore vectorStore;

    /**
     * Chapter 엔티티가 아니라 {@link ChapterContent} 값 객체를 받는다.
     * 이 메서드는 Chroma 로 네트워크 호출을 하므로 트랜잭션 밖에서 실행되며,
     * 그 시점에는 영속성 컨텍스트가 이미 닫혀 있어 엔티티를 안전하게 다룰 수 없기 때문이다.
     */
    public void indexChapters(Long bookId, List<ChapterContent> chapters) {
        List<Document> documents = new ArrayList<>();
        for (ChapterContent chapter : chapters) {
            if (!chapter.hasContent()) continue;
            List<String> chunks = chunkText(chapter.content());
            for (int i = 0; i < chunks.size(); i++) {
                documents.add(new Document(
                        chunks.get(i),
                        Map.of(
                                BOOK_ID_KEY, bookId.toString(),
                                "chapterId", String.valueOf(chapter.chapterId()),
                                "chapterNumber", String.valueOf(chapter.chapterNumber()),
                                "chunkIndex", String.valueOf(i)
                        )
                ));
            }
        }
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("Indexed {} chunks for bookId={}", documents.size(), bookId);
        }
    }

    public String retrieveContext(Long bookId, String query) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(TOP_K)
                        .filterExpression(b.eq(BOOK_ID_KEY, bookId.toString()).build())
                        .build()
        );
        return results.stream()
                .map(Document::getText)
                .reduce("", (a, b2) -> a + "\n\n" + b2)
                .trim();
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int len = text.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(start + CHUNK_SIZE, len);
            chunks.add(text.substring(start, end));
            start += CHUNK_SIZE - CHUNK_OVERLAP;
        }
        return chunks;
    }
}
