package com.booktown.domain.summary.entity;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "summary_job",
        indexes = {
                @Index(name = "idx_summary_job_user_book", columnList = "user_id, book_id"),
                @Index(name = "idx_summary_job_status", columnList = "status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SummaryJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SummaryJobStatus status;

    @Column(name = "summary_document_id", length = 36)
    private String summaryDocumentId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private boolean retryable = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static SummaryJob create(User user, Book book) {
        SummaryJob job = new SummaryJob();
        job.user = user;
        job.book = book;
        job.status = SummaryJobStatus.QUEUED;
        return job;
    }

    public void markProcessing() {
        this.status = SummaryJobStatus.PROCESSING;
    }

    public void markCompleted(String summaryDocumentId) {
        this.status = SummaryJobStatus.COMPLETED;
        this.summaryDocumentId = summaryDocumentId;
    }

    public void markFailed(String errorMessage, boolean retryable) {
        this.status = SummaryJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
