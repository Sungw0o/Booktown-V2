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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryProcessor {

    private static final String SUMMARY_PROMPT = """
            다음은 고전문학 원문의 일부입니다. 이 내용을 바탕으로 핵심 내용을 한국어로 요약해주세요.
            독자가 작품의 주제, 등장인물, 주요 사건을 이해할 수 있도록 300~500자로 작성해주세요.

            [참고 원문]
            %s

            [요약]
            """;

    private final SummaryJobRepository summaryJobRepository;
    private final SummaryDocumentRepository summaryDocumentRepository;
    private final ChapterRepository chapterRepository;
    private final RagService ragService;
    private final ChatClient chatClient;

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

            ragService.indexChapters(bookId, chapters);

            String context = ragService.retrieveContext(bookId, "이 작품의 주제와 주요 사건");

            String summaryContent = chatClient.prompt()
                    .user(SUMMARY_PROMPT.formatted(context))
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

    private boolean isAiServiceError(Exception e) {
        return e.getMessage() != null && (
                e.getMessage().contains("timeout") ||
                e.getMessage().contains("rate limit") ||
                e.getMessage().contains("service unavailable")
        );
    }
}
