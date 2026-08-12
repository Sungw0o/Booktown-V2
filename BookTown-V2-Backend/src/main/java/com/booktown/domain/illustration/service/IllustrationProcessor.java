package com.booktown.domain.illustration.service;

import com.booktown.domain.illustration.document.IllustrationDocument;
import com.booktown.domain.illustration.repository.IllustrationDocumentRepository;
import com.booktown.domain.illustration.service.IllustrationJobTransactionService.IllustrationJobContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 삽화 생성 비동기 처리기.
 *
 * <h2>트랜잭션 경계 설계</h2>
 * <p>이 클래스에는 {@code @Transactional} 이 <b>없다</b>. 의도적이다.
 * 이미지 생성 API 는 텍스트 생성보다 응답이 느려 커넥션 점유 시간이 특히 길어지므로,
 * DB 작업은 {@link IllustrationJobTransactionService} 에 위임해 앞뒤로만 짧게 건다.
 *
 * <pre>
 *   [짧은 TX] PROCESSING 전이 + 프롬프트 재료 추출  → 커넥션 반납
 *   [TX 밖 ] 이미지 생성 API 호출 + MongoDB 저장    → DB 커넥션 점유 없음
 *   [짧은 TX] COMPLETED 전이                        → 커넥션 반납
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IllustrationProcessor {

    private final IllustrationJobTransactionService transactionService;
    private final IllustrationDocumentRepository illustrationDocumentRepository;
    private final ImageGenerationClient imageGenerationClient;
    private final BookVisualPromptFactory bookVisualPromptFactory;

    @Async("illustrationProcessingExecutor")
    public void process(Long jobId, boolean isRegeneration) {
        // ── 1단계: 짧은 트랜잭션 ──
        IllustrationJobContext ctx;
        try {
            ctx = transactionService.startProcessing(jobId);
        } catch (Exception e) {
            log.error("IllustrationJob {} could not start: {}", jobId, e.getMessage(), e);
            return;
        }

        // ── 2단계: 트랜잭션 밖 — 이미지 생성 API 호출 ──
        try {
            String prompt = bookVisualPromptFactory.createScenePrompt(
                    ctx.bookTitle(), ctx.bookAuthor(),
                    ctx.sceneTitle(), ctx.sceneExcerpt(),
                    ctx.style(), ctx.promptHint()
            );
            String imageUrl = imageGenerationClient.generateImageDataUrl(prompt);

            IllustrationDocument doc = IllustrationDocument.create(
                    ctx.sceneId(),
                    ctx.userId(),
                    jobId,
                    ctx.style().name(),
                    ctx.promptHint(),
                    imageUrl,
                    isRegeneration
            );
            IllustrationDocument saved = illustrationDocumentRepository.save(doc);

            // ── 3단계: 짧은 트랜잭션 ──
            transactionService.markCompleted(jobId, saved.getId());
            log.info("IllustrationJob {} completed: {}", jobId, saved.getId());
        } catch (Exception e) {
            log.error("IllustrationJob {} failed: {}", jobId, e.getMessage(), e);
            boolean retryable = !(e instanceof IllegalArgumentException);
            markFailedQuietly(jobId, e.getMessage(), retryable);
        }
    }

    private void markFailedQuietly(Long jobId, String message, boolean retryable) {
        try {
            transactionService.markFailed(jobId, message, retryable);
        } catch (Exception e) {
            log.error("IllustrationJob {} stuck in PROCESSING: failed to persist FAILED state: {}",
                    jobId, e.getMessage(), e);
        }
    }
}
