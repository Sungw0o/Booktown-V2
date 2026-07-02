package com.booktown.domain.illustration.service;

import com.booktown.domain.illustration.document.IllustrationDocument;
import com.booktown.domain.illustration.entity.IllustrationJob;
import com.booktown.domain.illustration.repository.IllustrationDocumentRepository;
import com.booktown.domain.illustration.repository.IllustrationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IllustrationProcessor {

    private final IllustrationJobRepository illustrationJobRepository;
    private final IllustrationDocumentRepository illustrationDocumentRepository;
    private final GeminiImageClient geminiImageClient;

    @Async("illustrationProcessingExecutor")
    @Transactional
    public void process(Long jobId, boolean isRegeneration) {
        IllustrationJob job = illustrationJobRepository.findById(jobId).orElseThrow();
        job.markProcessing();
        illustrationJobRepository.save(job);

        try {
            String prompt = buildPrompt(job);
            String imageUrl = geminiImageClient.generateImageDataUrl(prompt);

            IllustrationDocument doc = IllustrationDocument.create(
                    job.getScene().getId(),
                    job.getUser().getId(),
                    job.getId(),
                    job.getStyle().name(),
                    job.getPromptHint(),
                    imageUrl,
                    isRegeneration
            );
            IllustrationDocument saved = illustrationDocumentRepository.save(doc);

            job.markCompleted(saved.getId());
            illustrationJobRepository.save(job);
            log.info("IllustrationJob {} completed: {}", jobId, saved.getId());
        } catch (Exception e) {
            log.error("IllustrationJob {} failed: {}", jobId, e.getMessage(), e);
            boolean retryable = !(e instanceof IllegalArgumentException);
            job.markFailed(e.getMessage(), retryable);
            illustrationJobRepository.save(job);
        }
    }

    private String buildPrompt(IllustrationJob job) {
        String scene = job.getScene().getTitle();
        String excerpt = job.getScene().getExcerpt();
        String style = job.getStyle().name().toLowerCase();
        String hint = job.getPromptHint() != null ? job.getPromptHint() : "";

        return String.format(
                "Create a %s style literary illustration for the scene '%s'. "
                        + "Scene description: %s. %s"
                        + "The illustration should evoke the mood and atmosphere of classic literature.",
                style, scene,
                        excerpt != null ? excerpt.substring(0, Math.min(200, excerpt.length())) : "",
                hint.isBlank() ? "" : "Additional guidance: " + hint + ". "
        );
    }

}
