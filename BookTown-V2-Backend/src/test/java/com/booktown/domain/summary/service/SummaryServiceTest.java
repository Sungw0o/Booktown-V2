package com.booktown.domain.summary.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.summary.document.SummaryDocument;
import com.booktown.domain.summary.document.SummaryFeedback;
import com.booktown.domain.summary.dto.CreateSummaryRequest;
import com.booktown.domain.summary.dto.SummaryFeedbackRequest;
import com.booktown.domain.summary.dto.SummaryJobResponse;
import com.booktown.domain.summary.entity.SummaryJob;
import com.booktown.domain.summary.entity.SummaryJobStatus;
import com.booktown.domain.summary.repository.SummaryDocumentRepository;
import com.booktown.domain.summary.repository.SummaryJobRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryServiceTest {

    private BookRepository bookRepository;
    private UserRepository userRepository;
    private SummaryJobRepository summaryJobRepository;
    private SummaryDocumentRepository summaryDocumentRepository;
    private SummaryProcessor summaryProcessor;
    private SummaryService summaryService;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        summaryJobRepository = mock(SummaryJobRepository.class);
        summaryDocumentRepository = mock(SummaryDocumentRepository.class);
        summaryProcessor = mock(SummaryProcessor.class);
        summaryService = new SummaryService(bookRepository, userRepository,
                summaryJobRepository, summaryDocumentRepository, summaryProcessor);
    }

    @Test
    void createSummary_throws_when_book_not_found() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> summaryService.createSummary(1L, 99L, new CreateSummaryRequest(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    void createSummary_throws_when_content_not_ready() {
        Book book = bookWithoutContent();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> summaryService.createSummary(1L, 1L, new CreateSummaryRequest(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_CONTENT_NOT_READY);
    }

    @Test
    void createSummary_throws_when_duplicate_active_job() {
        Book book = bookWithContent();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(summaryJobRepository.existsByUserIdAndBookIdAndStatusIn(eq(1L), eq(1L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> summaryService.createSummary(1L, 1L, new CreateSummaryRequest(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_SUMMARY_JOB);
    }

    @Test
    void createSummary_throws_when_rate_limit_exceeded() {
        Book book = bookWithContent();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(summaryJobRepository.existsByUserIdAndBookIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(summaryJobRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(10);

        assertThatThrownBy(() -> summaryService.createSummary(1L, 1L, new CreateSummaryRequest(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUMMARY_RATE_LIMIT_EXCEEDED);
    }

    @Test
    void getSummaryJob_throws_when_not_found_or_not_owner() {
        when(summaryJobRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> summaryService.getSummaryJob(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUMMARY_JOB_NOT_FOUND);
    }

    @Test
    void getSummaryJob_returns_response_for_owner() {
        Book book = bookWithContent();
        User user = sampleUser();
        SummaryJob job = SummaryJob.create(user, book);
        when(summaryJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(job));

        SummaryJobResponse response = summaryService.getSummaryJob(1L, 1L);

        assertThat(response.status()).isEqualTo(SummaryJobStatus.QUEUED.name());
    }

    @Test
    void getSummaries_returns_docs_for_user() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookWithContent()));
        SummaryDocument doc = SummaryDocument.create(1L, 1L, 10L, "요약 내용", false);
        when(summaryDocumentRepository.findAllByBookIdAndUserIdOrderByCreatedAtDesc(1L, 1L))
                .thenReturn(List.of(doc));

        var responses = summaryService.getSummaries(1L, 1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).content()).isEqualTo("요약 내용");
    }

    @Test
    void getSummary_throws_when_not_owner() {
        when(summaryDocumentRepository.findByIdAndUserId("doc1", 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> summaryService.getSummary(2L, "doc1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUMMARY_NOT_FOUND);
    }

    @Test
    void saveFeedback_throws_when_feedback_already_exists() {
        SummaryDocument doc = mock(SummaryDocument.class);
        when(doc.getFeedback()).thenReturn(SummaryFeedback.of(1L, 5, "좋아요"));
        when(summaryDocumentRepository.findByIdAndUserId("doc1", 1L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> summaryService.saveFeedback(1L, "doc1", new SummaryFeedbackRequest(5, "좋아요")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUMMARY_FEEDBACK_ALREADY_EXISTS);
    }

    @Test
    void saveFeedback_saves_when_no_existing_feedback() {
        SummaryDocument doc = SummaryDocument.create(1L, 1L, 10L, "요약", false);
        when(summaryDocumentRepository.findByIdAndUserId("doc1", 1L)).thenReturn(Optional.of(doc));

        summaryService.saveFeedback(1L, "doc1", new SummaryFeedbackRequest(4, "좋네요"));

        verify(summaryDocumentRepository).save(doc);
        assertThat(doc.getFeedback()).isNotNull();
        assertThat(doc.getFeedback().getRating()).isEqualTo(4);
    }

    @Test
    void regenerateSummary_throws_when_summary_not_found() {
        when(summaryDocumentRepository.findByIdAndUserId("doc1", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> summaryService.regenerateSummary(1L, "doc1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUMMARY_NOT_FOUND);
    }

    @Test
    void regenerateSummary_throws_when_active_job_exists() {
        SummaryDocument doc = SummaryDocument.create(1L, 1L, 10L, "요약", false);
        when(summaryDocumentRepository.findByIdAndUserId("doc1", 1L)).thenReturn(Optional.of(doc));
        when(summaryJobRepository.existsByUserIdAndBookIdAndStatusIn(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> summaryService.regenerateSummary(1L, "doc1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_SUMMARY_JOB);
    }

    private Book bookWithContent() {
        Book book = Book.create("소나기", "황순원", null, null, Genre.PROSE, Country.KOREA);
        book.markContentUploaded();
        return book;
    }

    private Book bookWithoutContent() {
        return Book.create("소나기", "황순원", null, null, Genre.PROSE, Country.KOREA);
    }

    private User sampleUser() {
        return User.local("test@test.com", "테스터", "hash");
    }
}
