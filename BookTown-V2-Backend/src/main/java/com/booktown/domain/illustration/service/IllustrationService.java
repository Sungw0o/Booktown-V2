package com.booktown.domain.illustration.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Scene;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.SceneRepository;
import com.booktown.domain.illustration.document.IllustrationDocument;
import com.booktown.domain.illustration.dto.CreateIllustrationRequest;
import com.booktown.domain.illustration.dto.IllustrationJobResponse;
import com.booktown.domain.illustration.dto.IllustrationResponse;
import com.booktown.domain.illustration.dto.SceneResponse;
import com.booktown.domain.illustration.entity.IllustrationJob;
import com.booktown.domain.illustration.entity.IllustrationJobStatus;
import com.booktown.domain.illustration.entity.IllustrationStyle;
import com.booktown.domain.illustration.repository.IllustrationDocumentRepository;
import com.booktown.domain.illustration.repository.IllustrationJobRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.response.PageMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IllustrationService {

    private static final int DAILY_RATE_LIMIT = 5;
    private static final List<IllustrationJobStatus> ACTIVE_STATUSES =
            List.of(IllustrationJobStatus.QUEUED, IllustrationJobStatus.PROCESSING);

    private final BookRepository bookRepository;
    private final SceneRepository sceneRepository;
    private final UserRepository userRepository;
    private final IllustrationJobRepository illustrationJobRepository;
    private final IllustrationDocumentRepository illustrationDocumentRepository;
    private final IllustrationProcessor illustrationProcessor;

    @Transactional(readOnly = true)
    public Page<SceneResponse> getScenes(Long bookId, int page, int size) {
        if (!bookRepository.existsById(bookId)) {
            throw new CustomException(ErrorCode.BOOK_NOT_FOUND);
        }
        return sceneRepository.findAllByBookIdOrderBySceneOrderAsc(bookId, PageRequest.of(page, size))
                .map(SceneResponse::from);
    }

    @Transactional
    public IllustrationJobResponse createIllustration(Long userId, Long sceneId, CreateIllustrationRequest request) {
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCENE_NOT_FOUND));

        IllustrationStyle style = parseStyle(request.style());

        if (illustrationJobRepository.existsByUserIdAndSceneIdAndStatusIn(userId, sceneId, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.DUPLICATE_ILLUSTRATION_JOB);
        }
        if (illustrationJobRepository.countByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusDays(1)) >= DAILY_RATE_LIMIT) {
            throw new CustomException(ErrorCode.ILLUSTRATION_RATE_LIMIT_EXCEEDED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        IllustrationJob job = IllustrationJob.create(user, scene, style, request.promptHint());
        illustrationJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                illustrationProcessor.process(job.getId(), false);
            }
        });

        return IllustrationJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public IllustrationJobResponse getIllustrationJob(Long userId, Long jobId) {
        IllustrationJob job = illustrationJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ILLUSTRATION_JOB_NOT_FOUND));
        return IllustrationJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<IllustrationResponse> getIllustrations(Long userId, Long sceneId) {
        if (!sceneRepository.existsById(sceneId)) {
            throw new CustomException(ErrorCode.SCENE_NOT_FOUND);
        }
        return illustrationDocumentRepository.findAllBySceneIdAndUserIdOrderByCreatedAtDesc(sceneId, userId)
                .stream().map(IllustrationResponse::from).toList();
    }

    @Transactional
    public IllustrationJobResponse regenerateIllustration(Long userId, String illustrationId) {
        IllustrationDocument doc = illustrationDocumentRepository.findByIdAndUserId(illustrationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ILLUSTRATION_NOT_FOUND));

        Long sceneId = doc.getSceneId();
        if (illustrationJobRepository.existsByUserIdAndSceneIdAndStatusIn(userId, sceneId, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.DUPLICATE_ILLUSTRATION_JOB);
        }

        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCENE_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        IllustrationStyle style = IllustrationStyle.valueOf(doc.getStyle());

        IllustrationJob job = IllustrationJob.create(user, scene, style, doc.getPromptHint());
        illustrationJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                illustrationProcessor.process(job.getId(), true);
            }
        });

        return IllustrationJobResponse.from(job);
    }

    private IllustrationStyle parseStyle(String style) {
        try {
            return IllustrationStyle.valueOf(style.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
