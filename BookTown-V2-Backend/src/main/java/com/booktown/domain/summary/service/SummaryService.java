package com.booktown.domain.summary.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.summary.document.SummaryDocument;
import com.booktown.domain.summary.document.SummaryFeedback;
import com.booktown.domain.summary.dto.CreateSummaryRequest;
import com.booktown.domain.summary.dto.SummaryFeedbackRequest;
import com.booktown.domain.summary.dto.SummaryJobResponse;
import com.booktown.domain.summary.dto.SummaryResponse;
import com.booktown.domain.summary.entity.SummaryJob;
import com.booktown.domain.summary.entity.SummaryJobStatus;
import com.booktown.domain.summary.repository.SummaryDocumentRepository;
import com.booktown.domain.summary.repository.SummaryJobRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private static final int DAILY_RATE_LIMIT = 10;
    private static final List<SummaryJobStatus> ACTIVE_STATUSES =
            List.of(SummaryJobStatus.QUEUED, SummaryJobStatus.PROCESSING);

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final SummaryJobRepository summaryJobRepository;
    private final SummaryDocumentRepository summaryDocumentRepository;
    private final SummaryProcessor summaryProcessor;

    @Transactional
    public SummaryJobResponse createSummary(Long userId, Long bookId, CreateSummaryRequest request) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
        if (!book.isHasContent()) {
            throw new CustomException(ErrorCode.BOOK_CONTENT_NOT_READY);
        }
        if (summaryJobRepository.existsByUserIdAndBookIdAndStatusIn(userId, bookId, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.DUPLICATE_SUMMARY_JOB);
        }
        int todayCount = summaryJobRepository.countByUserIdAndCreatedAtAfter(
                userId, LocalDateTime.now().minusDays(1));
        if (todayCount >= DAILY_RATE_LIMIT) {
            throw new CustomException(ErrorCode.SUMMARY_RATE_LIMIT_EXCEEDED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        SummaryJob job = SummaryJob.create(user, book);
        summaryJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                summaryProcessor.process(job.getId(), false);
            }
        });

        return SummaryJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public SummaryJobResponse getSummaryJob(Long userId, Long jobId) {
        SummaryJob job = summaryJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SUMMARY_JOB_NOT_FOUND));
        return SummaryJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<SummaryResponse> getSummaries(Long userId, Long bookId) {
        bookRepository.findById(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
        return summaryDocumentRepository.findAllByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId)
                .stream()
                .map(SummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(Long userId, String summaryId) {
        SummaryDocument doc = summaryDocumentRepository.findByIdAndUserId(summaryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SUMMARY_NOT_FOUND));
        return SummaryResponse.from(doc);
    }

    @Transactional
    public SummaryJobResponse regenerateSummary(Long userId, String summaryId) {
        SummaryDocument existing = summaryDocumentRepository.findByIdAndUserId(summaryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SUMMARY_NOT_FOUND));

        Long bookId = existing.getBookId();
        if (summaryJobRepository.existsByUserIdAndBookIdAndStatusIn(userId, bookId, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.DUPLICATE_SUMMARY_JOB);
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        SummaryJob job = SummaryJob.create(user, book);
        summaryJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                summaryProcessor.process(job.getId(), true);
            }
        });

        return SummaryJobResponse.from(job);
    }

    @Transactional
    public void saveFeedback(Long userId, String summaryId, SummaryFeedbackRequest request) {
        SummaryDocument doc = summaryDocumentRepository.findByIdAndUserId(summaryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SUMMARY_NOT_FOUND));
        if (doc.getFeedback() != null) {
            throw new CustomException(ErrorCode.SUMMARY_FEEDBACK_ALREADY_EXISTS);
        }
        doc.applyFeedback(SummaryFeedback.of(userId, request.rating(), request.comment()));
        summaryDocumentRepository.save(doc);
    }
}
