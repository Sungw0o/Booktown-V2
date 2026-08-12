package com.booktown.domain.illustration.service;

import com.booktown.domain.book.entity.Scene;
import com.booktown.domain.illustration.entity.IllustrationJob;
import com.booktown.domain.illustration.entity.IllustrationStyle;
import com.booktown.domain.illustration.repository.IllustrationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * IllustrationProcessor 의 DB 작업만 담당하는 트랜잭션 경계 전용 서비스.
 *
 * <p>이미지 생성 API 호출은 텍스트 생성보다도 오래 걸리므로,
 * 그 구간이 DB 커넥션을 점유하지 않도록 트랜잭션을 앞뒤로만 짧게 건다.
 */
@Service
@RequiredArgsConstructor
public class IllustrationJobTransactionService {

    private final IllustrationJobRepository illustrationJobRepository;

    /** 1단계 (짧은 TX): PROCESSING 전이 + 프롬프트 생성에 필요한 값 추출. */
    @Transactional
    public IllustrationJobContext startProcessing(Long jobId) {
        IllustrationJob job = illustrationJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("IllustrationJob not found: " + jobId));
        job.markProcessing();

        Scene scene = job.getScene();
        return new IllustrationJobContext(
                jobId,
                scene.getId(),
                job.getUser().getId(),
                job.getStyle(),
                job.getPromptHint(),
                scene.getTitle(),
                scene.getExcerpt(),
                scene.getBook().getTitle(),
                scene.getBook().getAuthor()
        );
    }

    /** 2단계 (짧은 TX): 결과 저장 후 COMPLETED 전이. */
    @Transactional
    public void markCompleted(Long jobId, String illustrationDocumentId) {
        illustrationJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("IllustrationJob not found: " + jobId))
                .markCompleted(illustrationDocumentId);
    }

    /** 실패 처리 (짧은 TX, REQUIRES_NEW). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage, boolean retryable) {
        illustrationJobRepository.findById(jobId)
                .ifPresent(job -> job.markFailed(truncate(errorMessage), retryable));
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    /** 트랜잭션 밖으로 전달하기 위한 불변 값 객체. */
    public record IllustrationJobContext(
            Long jobId,
            Long sceneId,
            Long userId,
            IllustrationStyle style,
            String promptHint,
            String sceneTitle,
            String sceneExcerpt,
            String bookTitle,
            String bookAuthor
    ) {}
}
