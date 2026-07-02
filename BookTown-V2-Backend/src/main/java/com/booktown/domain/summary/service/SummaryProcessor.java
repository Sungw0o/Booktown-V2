package com.booktown.domain.summary.service;

import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.summary.document.SummaryDocument;
import com.booktown.domain.summary.entity.SummaryJob;
import com.booktown.domain.summary.repository.SummaryDocumentRepository;
import com.booktown.domain.summary.repository.SummaryJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryProcessor {

    private static final String SUMMARY_PROMPT = """
            당신은 고전문학 독서 플랫폼의 한국어 요약 에디터입니다.
            아래 도서 원문 발췌를 바탕으로 독자가 작품을 빠르게 이해할 수 있는 요약을 작성해주세요.

            요구사항:
            - 한국어 Markdown 형식
            - 700~1,000자
            - 핵심 줄거리, 주요 인물, 주제 의식을 구분
            - 원문에 없는 사실을 단정하지 말 것
            - 대화체가 아니라 차분한 해설문으로 작성

            [도서]
            제목: %s
            저자: %s

            [참고 원문]
            %s

            [요약]
            """;
    private static final int MAX_CONTEXT_CHARS = 18_000;
    private static final int MAX_CHAPTER_EXCERPT_CHARS = 2_400;

    private final SummaryJobRepository summaryJobRepository;
    private final SummaryDocumentRepository summaryDocumentRepository;
    private final ChapterRepository chapterRepository;
    private final ObjectProvider<RagService> ragServiceProvider;
    private final ObjectProvider<ChatClient> chatClientProvider;

    @Async("summaryProcessingExecutor")
    @Transactional
    public void process(Long jobId, boolean isRegeneration) {
        SummaryJob job = summaryJobRepository.findById(jobId).orElseThrow();
        job.markProcessing();
        summaryJobRepository.save(job);

        try {
            Long bookId = job.getBook().getId();
            Long userId = job.getUser().getId();

            List<Chapter> chapters = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(bookId);
            String context = buildRagContextIfAvailable(bookId, chapters);
            if (context.isBlank()) {
                context = buildContext(chapters);
            }
            if (context.isBlank()) {
                throw new IllegalStateException("Book content is empty.");
            }

            ChatClient chatClient = requireChatClient();
            String summaryContent = chatClient.prompt()
                    .user(SUMMARY_PROMPT.formatted(
                            job.getBook().getTitle(),
                            job.getBook().getAuthor(),
                            context
                    ))
                    .call()
                    .content();

            SummaryDocument doc = SummaryDocument.create(bookId, userId, jobId, summaryContent, isRegeneration);
            summaryDocumentRepository.save(doc);

            job.markCompleted(doc.getId());
            summaryJobRepository.save(job);
            log.info("SummaryJob {} completed: summaryId={}", jobId, doc.getId());
        } catch (Exception e) {
            log.error("SummaryJob {} failed: {}", jobId, e.getMessage(), e);
            job.markFailed(e.getMessage(), isAiServiceError(e));
            summaryJobRepository.save(job);
        }
    }

    private String buildRagContextIfAvailable(Long bookId, List<Chapter> chapters) {
        RagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            return "";
        }

        try {
            ragService.indexChapters(bookId, chapters);
            String context = ragService.retrieveContext(bookId, "이 작품의 핵심 줄거리, 주요 인물, 주제 의식");
            if (!context.isBlank()) {
                log.info("SummaryJob uses Chroma RAG context for bookId={}", bookId);
            }
            return context;
        } catch (Exception e) {
            log.warn("Chroma RAG context unavailable for bookId={}: {}", bookId, e.getMessage());
            return "";
        }
    }

    private String buildContext(List<Chapter> chapters) {
        StringBuilder context = new StringBuilder();
        for (Chapter chapter : chapters) {
            if (chapter.getContent() == null || chapter.getContent().isBlank()) {
                continue;
            }

            String excerpt = excerpt(chapter.getContent(), MAX_CHAPTER_EXCERPT_CHARS);
            String block = "[%d. %s]%n%s%n%n".formatted(
                    chapter.getChapterNumber(),
                    chapter.getTitle(),
                    excerpt
            );
            if (context.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(block);
        }
        return context.toString().trim();
    }

    private String excerpt(String content, int maxChars) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private boolean isAiServiceError(Exception e) {
        return e.getMessage() != null && (
                e.getMessage().contains("timeout") ||
                e.getMessage().contains("rate limit") ||
                e.getMessage().contains("service unavailable") ||
                e.getMessage().contains("not configured")
        );
    }

    private ChatClient requireChatClient() {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            throw new IllegalStateException("AI chat client is not configured.");
        }
        return chatClient;
    }
}
