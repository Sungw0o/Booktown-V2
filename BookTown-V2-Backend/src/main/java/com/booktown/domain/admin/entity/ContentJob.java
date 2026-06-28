package com.booktown.domain.admin.entity;

import com.booktown.domain.book.entity.Book;
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
        name = "content_job",
        indexes = @Index(name = "idx_content_job_book_id", columnList = "book_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentJobStatus status;

    @Column(name = "chapter_count")
    private Integer chapterCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private boolean retryable = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static ContentJob create(Book book) {
        ContentJob job = new ContentJob();
        job.book = book;
        job.status = ContentJobStatus.QUEUED;
        return job;
    }

    public void markProcessing() {
        this.status = ContentJobStatus.PROCESSING;
    }

    public void markCompleted(int chapterCount) {
        this.status = ContentJobStatus.COMPLETED;
        this.chapterCount = chapterCount;
    }

    public void markFailed(String errorMessage, boolean retryable) {
        this.status = ContentJobStatus.FAILED;
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
